/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.resource;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuTexture;

/**
 * Single lifecycle owner for Combatant render resources.
 * Stage 11 rule: no subsystem should keep ad-hoc framebuffer/texture pools as the production path.
 */
public final class RenderResourceManager implements AutoCloseable {
    private final ResourceLeakTracker leakTracker = new ResourceLeakTracker();
    private final FrameResourceRetirementQueue retirementQueue = new FrameResourceRetirementQueue();
    private final FramebufferPool framebufferPool = new FramebufferPool(leakTracker);
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

    public void onFramePresented() {
        frameId++;
        // Budgeted cleanup: do not turn flipFrame into a blocking resource purge.
        retirementQueue.drain(16);
        texturePool.drain(8);
    }

    public FramebufferPool framebuffers() {
        return framebufferPool;
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
    }

    public void onReload() {
        framebufferPool.invalidateSizeDependent();
        glyphAtlasManager.clear();
        texturePool.drain(Integer.MAX_VALUE);
    }

    public void onWorldUnload() {
        framebufferPool.invalidateSizeDependent();
    }

    public RenderResourceStatsSnapshot statsSnapshot() {
        return new RenderResourceStatsSnapshot(
                frameId,
                framebufferPool.persistentCount(),
                framebufferPool.temporaryCount(),
                framebufferPool.borrows(),
                framebufferPool.releases(),
                framebufferPool.creates(),
                framebufferPool.resizes(),
                framebufferPool.invalidations(),
                texturePool.retirements(),
                texturePool.closes(),
                retirementQueue.queued(),
                retirementQueue.closed(),
                retirementQueue.backlog(),
                leakTracker.liveCount()
        );
    }

    @Override
    public void close() {
        retirementQueue.drainAll();
        framebufferPool.close();
        texturePool.close();
        glyphAtlasManager.clear();
        leakTracker.clear();
    }
}
