/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.vulkan.msaa;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;
import combatant.client.render.engine.rhi.msaa.MsaaControl;
import combatant.client.util.logging.DebugLog;

/**
 * Vulkan implementation of Combatant MSAA allocation and dynamic-rendering resolve.
 */
public final class VulkanMsaaControl implements MsaaControl {

    @Override
    public boolean supported() {
        return VulkanRenderStateBridge.vulkanBackendActive() && VulkanRenderStateBridge.msaaSupported();
    }

    @Override
    public GpuTexture createTexture(String label, int usage, GpuFormat format, int width, int height, int samples) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid MSAA texture size: " + width + "x" + height);
        }
        int normalizedSamples = VulkanRenderStateBridge.normalizeSamples(samples);
        if (!supported() || normalizedSamples <= 1) {
            throw new IllegalStateException("Vulkan MSAA is disabled or unsupported for requested sample count " + samples
                    + " (reason=" + VulkanRenderStateBridge.msaaDisableReason() + ")");
        }
        int finalSamples = normalizedSamples;
        GpuTexture texture = VulkanRenderStateBridge.withTextureSamples(finalSamples, () ->
                RenderSystem.getDevice().createTexture(label, usage, format, width, height, 1, 1)
        );
        if (texture instanceof IMsaaTexture msaaTexture) {
            msaaTexture.combatant$setSamples(finalSamples);
        }
        return texture;
    }

    @Override
    public void registerTexture(int nativeTextureId, int samples) {
        // GL-only compatibility hook. Vulkan textures are tagged through VulkanGpuTextureMixin.
    }

    @Override
    public void unregisterTexture(int nativeTextureId) {
        // GL-only compatibility hook.
    }

    @Override
    public boolean isMsaaTexture(int nativeTextureId) {
        return false;
    }

    @Override
    public int samples(int nativeTextureId) {
        return 1;
    }

    @Override
    public int framebufferTextureTarget(int nativeTextureId) {
        return 0;
    }

    @Override
    public boolean setupFramebuffer(int framebuffer, int colorAttachment, int depthAttachment, int mipLevel, int bindTarget) {
        return false;
    }

    @Override
    public boolean resolve(RenderTarget src, RenderTarget dst, boolean color, boolean depth) {
        if (src == null || dst == null) return false;
        if (VulkanMsaaResolveBridge.matchesPreparedTarget(src, dst, color, depth)) {
            try {
                boolean ok = true;
                if (color) ok &= resolveColorSnapshot(src.getColorTextureView(), dst.getColorTextureView());
                if (depth) ok &= resolveDepthSnapshot(src.getDepthTextureView(), dst.getDepthTextureView());
                return ok;
            } finally {
                VulkanMsaaResolveBridge.abandon(src);
            }
        }
        boolean ok = true;
        if (color) ok &= resolveColorSnapshot(src.getColorTextureView(), dst.getColorTextureView());
        if (depth) ok &= resolveDepthSnapshot(src.getDepthTextureView(), dst.getDepthTextureView());
        return ok;
    }

    @Override
    public void prepareTarget(RenderTarget src, RenderTarget dst) {
        prepareTarget(src, dst, true, true);
    }

    @Override
    public void prepareTarget(RenderTarget src, RenderTarget dst, boolean color, boolean depth) {
        if (!supported()) {
            throw new IllegalStateException("Vulkan MSAA is disabled or unsupported");
        }
        VulkanMsaaResolveBridge.prepare(src, dst, color, depth);
    }

    @Override
    public void abandonTarget(RenderTarget src) {
        VulkanMsaaResolveBridge.abandon(src);
    }

    @Override
    public boolean resolveDepthSnapshot(GpuTextureView src, GpuTextureView dst) {
        if (!validSnapshotPair(src, dst)) return false;

        try {
            VulkanMsaaResolveBridge.beginDepthSnapshot(src, dst);
            RenderPassDescriptor descriptor = RenderPassDescriptor.create(() -> "Combatant MSAA depth snapshot resolve")
                    .withDepthAttachment(src)
                    .withRenderArea(new RenderPass.RenderArea(0, 0, src.getWidth(0), src.getHeight(0)));
            try (RenderPass ignored = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
                // Ending an otherwise empty dynamic-rendering scope performs the depth resolve.
            }
            return VulkanMsaaResolveBridge.completeDepthSnapshot(src, dst);
        } catch (Throwable t) {
            VulkanMsaaResolveBridge.abortDepthSnapshot(src);
            DebugLog.warnOnChange(
                    "msaa.vulkan.depth_snapshot.failed",
                    t.getClass().getSimpleName() + "|" + t.getMessage(),
                    "[MSAA/Vulkan] depth snapshot resolve failed; matching-depth fallback will be used: %s: %s",
                    t.getClass().getSimpleName(),
                    t.getMessage()
            );
            return false;
        }
    }

    private boolean resolveColorSnapshot(GpuTextureView src, GpuTextureView dst) {
        if (!validSnapshotPair(src, dst)) return false;
        try {
            VulkanMsaaResolveBridge.beginColorSnapshot(src, dst);
            RenderPassDescriptor descriptor = RenderPassDescriptor.create(() -> "Combatant MSAA color snapshot resolve")
                    .withColorAttachment(src)
                    .withRenderArea(new RenderPass.RenderArea(0, 0, src.getWidth(0), src.getHeight(0)));
            try (RenderPass ignored = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
                // Ending an otherwise empty dynamic-rendering scope performs the color resolve.
            }
            return VulkanMsaaResolveBridge.completeColorSnapshot(src, dst);
        } catch (Throwable t) {
            VulkanMsaaResolveBridge.abortColorSnapshot(src);
            DebugLog.warnOnChange(
                    "msaa.vulkan.color_snapshot.failed",
                    t.getClass().getSimpleName() + "|" + t.getMessage(),
                    "[MSAA/Vulkan] color snapshot resolve failed: %s: %s",
                    t.getClass().getSimpleName(),
                    t.getMessage()
            );
            return false;
        }
    }

    private boolean validSnapshotPair(GpuTextureView src, GpuTextureView dst) {
        if (!supported() || src == null || dst == null) return false;
        int sourceSamples = VulkanRenderStateBridge.samplesFor(src);
        int destinationSamples = VulkanRenderStateBridge.samplesFor(dst);
        return sourceSamples > 1
                && destinationSamples == 1
                && src.getWidth(0) == dst.getWidth(0)
                && src.getHeight(0) == dst.getHeight(0);
    }

    @Override
    public boolean supportsDepthSnapshotResolve() {
        return supported();
    }

    @Override
    public boolean eagerPreTranslucentDepthResolve() {
        return false;
    }

    @Override
    public void applyPipelineState(RenderPipeline pipeline) {
        // MSAA sample count is baked into Vulkan pipelines by VulkanRenderPipelineMixin.
    }

    @Override
    public void resetPipelineState() {
    }
}
