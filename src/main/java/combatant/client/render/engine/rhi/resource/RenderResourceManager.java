/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.resource;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import combatant.client.render.engine.msaa.MsaaFramebuffer;

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
    private final SamplerCache samplerCache = new SamplerCache();
    private final GlyphAtlasManager glyphAtlasManager = new GlyphAtlasManager();
    private long frameId;

    public void retire(AutoCloseable resource) {
        retirementQueue.retire(resource);
    }

    public void retireTexture(GpuTexture texture) {
        texturePool.retire(texture);
    }

    public TextureTarget persistentFramebuffer(String name, int width, int height, boolean depth, String owner) {
        return framebufferPool.persistent(name, width, height, depth, owner);
    }

    public TextureTarget borrowFramebuffer(String name, int width, int height, boolean depth, String owner) {
        return framebufferPool.borrowTemporary(name, width, height, depth, owner);
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

    public MsaaFramebuffer frameTransientMsaa(TransientTargetDescriptor descriptor) {
        return msaaFramebufferPool.acquireFrameTransient(descriptor);
    }

    public void beginFrame() {
        framebufferPool.beginFrame();
        msaaFramebufferPool.beginFrame();
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
        framebufferPool.invalidateSizeDependent();
        msaaFramebufferPool.invalidate();
    }

    public void onReload() {
        framebufferPool.invalidateSizeDependent();
        msaaFramebufferPool.invalidate();
        glyphAtlasManager.clear();
        texturePool.drain(Integer.MAX_VALUE);
    }

    public void onWorldUnload() {
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
        retirementQueue.drainAll();
        framebufferPool.close();
        msaaFramebufferPool.close();
        texturePool.close();
        glyphAtlasManager.clear();
        leakTracker.clear();
    }
}
