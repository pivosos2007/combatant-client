/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.resource;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import combatant.client.render.engine.msaa.MsaaFramebuffer;
import combatant.client.util.logging.DebugLog;

/**
 * Single lifecycle owner for Combatant render resources.
 * Stage 11 rule: no subsystem should keep ad-hoc framebuffer/texture pools as the production path.
 */
public final class RenderResourceManager implements AutoCloseable {
    private final ResourceLeakTracker leakTracker = new ResourceLeakTracker();
    private final FrameResourceRetirementQueue retirementQueue = new FrameResourceRetirementQueue();
    private final FramebufferPool framebufferPool = new FramebufferPool(leakTracker);
    private final MsaaFramebufferPool msaaFramebufferPool = new MsaaFramebufferPool(leakTracker);
    private final TexturePool texturePool = new TexturePool(leakTracker);
    private final FrameGraphPhysicalResourcePool frameGraphResources = new FrameGraphPhysicalResourcePool(retirementQueue);
    private final SamplerCache samplerCache = new SamplerCache();
    private final GlyphAtlasManager glyphAtlasManager = new GlyphAtlasManager();
    private long frameId;
    private boolean hdrFramebufferSupported = true;

    public void retire(AutoCloseable resource) {
        retirementQueue.retire(resource);
    }

    public void retireTexture(GpuTexture texture) {
        texturePool.retire(texture);
    }

    public TextureTarget persistentFramebuffer(String name, int width, int height, boolean depth, String owner) {
        return framebufferPool.persistent(name, width, height, depth, owner);
    }

    /**
     * Explicit-format framebuffer. HDR is opt-in for Combatant-owned intermediate passes;
     * never replaces Minecraft/Iris' externally-owned scene attachments.
     */
    public TextureTarget persistentFramebuffer(String name, int width, int height, boolean depth,
                                               GpuFormat format, String owner) {
        return framebufferPool.persistent(FramebufferDescriptor.persistent(
                name, width, height, depth, format, owner));
    }

    /** Attempts 16-bit floating point scene-linear color, with SDR fallback on older GPUs.
     * The caller can inspect getColorTextureView().texture().getFormat() and must choose a
     * matching color-target pipeline. HDR allocation does not perform tonemapping/compositing.
     */
    public TextureTarget persistentHdrFramebuffer(String name, int width, int height,
                                                  boolean depth, String owner) {
        if (hdrFramebufferSupported) {
            try {
                return persistentFramebuffer(name, width, height, depth, GpuFormat.RGBA16_FLOAT, owner);
            } catch (RuntimeException | LinkageError unsupported) {
                hdrFramebufferSupported = false; // Don't repeatedly attempt a failed allocation per frame.
                DebugLog.warnOnce("rhi.hdr.framebuffer.fallback",
                        "[RHI] RGBA16F framebuffer unavailable (%s); falling back to RGBA8",
                        unsupported.getMessage());
            }
        }
        return persistentFramebuffer(name, width, height, depth, GpuFormat.RGBA8_UNORM, owner);
    }

    public TextureTarget borrowFramebuffer(String name, int width, int height, boolean depth, String owner) {
        return framebufferPool.borrowTemporary(name, width, height, depth, owner);
    }

    public TextureTarget borrowFramebuffer(String name, int width, int height, boolean depth,
                                           GpuFormat format, String owner) {
        return framebufferPool.borrowTemporary(FramebufferDescriptor.temporary(
                name, width, height, depth, format, owner));
    }

    public void releaseFramebuffer(TextureTarget framebuffer, String owner) {
        framebufferPool.releaseTemporary(framebuffer, owner);
    }

    public TextureTarget frameTransient(TransientTargetDescriptor descriptor) {
        if (descriptor != null && descriptor.samples() > 1) {
            throw new IllegalArgumentException("Use frameTransientMsaa() for multisampled targets: "
                    + descriptor.logicalName());
        }
        return framebufferPool.acquireFrameTransient(descriptor);
    }

    /** One-frame HDR color attachment for explicit offscreen passes (not Iris' G-buffer). */
    public TextureTarget frameTransientHdr(String name, int width, int height,
                                           boolean depth, String owner) {
        return frameTransient(new TransientTargetDescriptor(name, width, height, depth,
                GpuFormat.RGBA16_FLOAT, 1, TransientTargetDescriptor.Lifetime.FRAME, owner));
    }

    /** Opt-in HDR transient; falls back to RGBA8 if the device rejects RGBA16F. */
    public TextureTarget frameTransientHdrOrSdr(String name, int width, int height,
                                                boolean depth, String owner) {
        if (hdrFramebufferSupported) {
            try {
                return frameTransientHdr(name, width, height, depth, owner);
            } catch (RuntimeException | LinkageError unsupported) {
                hdrFramebufferSupported = false;
                DebugLog.warnOnce("rhi.hdr.framebuffer.fallback",
                        "[RHI] RGBA16F framebuffer unavailable (%s); falling back to RGBA8",
                        unsupported.getMessage());
            }
        }
        return frameTransient(new TransientTargetDescriptor(name, width, height, depth,
                GpuFormat.RGBA8_UNORM, 1, TransientTargetDescriptor.Lifetime.FRAME, owner));
    }

    public MsaaFramebuffer frameTransientMsaa(TransientTargetDescriptor descriptor) {
        return msaaFramebufferPool.acquireFrameTransient(descriptor);
    }

    public void beginFrame() {
        framebufferPool.beginFrame();
        msaaFramebufferPool.beginFrame();
        frameGraphResources.beginFrame();
    }

    public void onFramePresented() {
        frameId++;
        framebufferPool.releaseFrameTransients();
        msaaFramebufferPool.releaseFrameTransients();
        // Budgeted cleanup: do not turn flipFrame into a blocking resource purge.
        retirementQueue.drain(16);
        texturePool.drain(8);
    }

    public FramebufferPool framebuffers() {
        return framebufferPool;
    }

    public MsaaFramebufferPool msaaFramebuffers() {
        return msaaFramebufferPool;
    }

    public TexturePool textures() {
        return texturePool;
    }

    public FrameGraphPhysicalResourcePool frameGraphResources() {
        return frameGraphResources;
    }

    public SamplerCache samplers() {
        return samplerCache;
    }

    public GlyphAtlasManager glyphAtlases() {
        return glyphAtlasManager;
    }

    public long frameId() {
        return frameId;
    }

    public void onResize() {
        frameGraphResources.invalidateAll();
        framebufferPool.invalidateSizeDependent();
        msaaFramebufferPool.invalidate();
    }

    public void onReload() {
        hdrFramebufferSupported = true; // Device/resource reload may change format availability.
        frameGraphResources.invalidateAll();
        framebufferPool.invalidateSizeDependent();
        msaaFramebufferPool.invalidate();
        glyphAtlasManager.clear();
        texturePool.drain(Integer.MAX_VALUE);
    }

    public void onWorldUnload() {
        frameGraphResources.invalidateAll();
        framebufferPool.invalidateSizeDependent();
        msaaFramebufferPool.invalidate();
    }

    public RenderResourceStatsSnapshot statsSnapshot() {
        return new RenderResourceStatsSnapshot(
                frameId,
                framebufferPool.persistentCount(),
                framebufferPool.temporaryCount() + msaaFramebufferPool.pooledCount(),
                framebufferPool.borrows(),
                framebufferPool.releases(),
                framebufferPool.creates() + msaaFramebufferPool.creates(),
                framebufferPool.resizes(),
                framebufferPool.invalidations(),
                texturePool.retirements(),
                texturePool.closes(),
                retirementQueue.queued(),
                retirementQueue.closed(),
                retirementQueue.backlog(),
                leakTracker.liveCount(),
                framebufferPool.activeFrameTransientCount() + msaaFramebufferPool.activeFrameTransientCount(),
                framebufferPool.transientAcquires() + msaaFramebufferPool.acquires(),
                framebufferPool.transientReuses() + msaaFramebufferPool.reuses(),
                framebufferPool.transientReleases() + msaaFramebufferPool.releases(),
                framebufferPool.peakFrameTransients() + msaaFramebufferPool.peakFrameLive(),
                framebufferPool.transientEvictions() + msaaFramebufferPool.evictions(),
                framebufferPool.idleTransientBytes() + msaaFramebufferPool.idleBytes()
        );
    }

    @Override
    public void close() {
        frameGraphResources.close();
        retirementQueue.drainAll();
        framebufferPool.close();
        msaaFramebufferPool.close();
        texturePool.close();
        glyphAtlasManager.clear();
        leakTracker.clear();
    }
}
