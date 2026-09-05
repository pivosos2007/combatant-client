/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.msaa;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

/**
 * RHI-owned MSAA capability surface.
 * <p>
 * The current implementation is OpenGL/Sodium specific because Minecraft 1.21.x still exposes the active renderer
 * through GL-backed Mojang objects. When a Vulkan backend exists, replace the implementation behind this interface
 * and keep mixins/features calling this capability instead of reintroducing backend-specific logic outside RHI.
 */
public interface MsaaControl {
    boolean supported();

    GpuTexture createTexture(String label,
                             int usage,
                             GpuFormat format,
                             int width,
                             int height,
                             int samples);

    void registerTexture(int nativeTextureId, int samples);

    void unregisterTexture(int nativeTextureId);

    boolean isMsaaTexture(int nativeTextureId);

    int samples(int nativeTextureId);

    int framebufferTextureTarget(int nativeTextureId);

    boolean setupFramebuffer(int framebuffer, int colorAttachment, int depthAttachment, int mipLevel, int bindTarget);

    boolean resolve(RenderTarget src, RenderTarget dst, boolean color, boolean depth);

    /** Resolve whose multisampled source is dead immediately afterwards. */
    default boolean resolveTransient(RenderTarget src, RenderTarget dst, boolean color, boolean depth) {
        return resolve(src, dst, color, depth);
    }

    default void prepareTarget(RenderTarget src, RenderTarget dst) {
    }

    default void prepareTarget(RenderTarget src, RenderTarget dst, boolean color, boolean depth) {
        prepareTarget(src, dst);
    }

    default void abandonTarget(RenderTarget src) {
    }

    /**
     * Resolves a multisampled depth snapshot into a single-sampled depth view.
     * Backends without native depth resolve support return {@code false}.
     */
    default boolean resolveDepthSnapshot(GpuTextureView src, GpuTextureView dst) {
        return false;
    }

    default boolean supportsDepthSnapshotResolve() {
        return false;
    }

    default boolean eagerPreTranslucentDepthResolve() {
        return supportsDepthSnapshotResolve();
    }

    void applyPipelineState(RenderPipeline pipeline);

    void resetPipelineState();
}
