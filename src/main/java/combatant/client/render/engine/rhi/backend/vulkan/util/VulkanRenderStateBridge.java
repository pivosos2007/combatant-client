/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.vulkan.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.mixininterface.IRenderPipeline;
import combatant.client.render.engine.rhi.clip.ShapeClipRenderPassContract;
import combatant.client.util.logging.DebugLog;

import java.util.List;

import static org.lwjgl.vulkan.VK12.*;

/**
 * Vulkan-side fixed pipeline state bridge for Combatant's GL-era dynamic state.
 *
 * <p>Important Vulkan rule: do not destroy/recreate cached VkPipelines while the current
 * command buffer may already reference them. Stencil modes are therefore represented through a
 * separate per-mode pipeline variant cache in VulkanDeviceMixin, not by calling
 * GpuDevice.clearPipelineCache() from setStencilMode().</p>
 */
public enum VulkanRenderStateBridge {
    ;

    public static boolean forceDisableStencil() {
        return runtimeStencilDisabled;
    }

    public static String stencilDisableReason() {
        return runtimeStencilDisableReason;
    }

    public static void disableStencilAfterFailure(String reason, Throwable t) {
        runtimeStencilDisabled = true;
        runtimeStencilDisableReason = reason == null ? "runtime-failure" : reason;
        stencilMode = StencilMode.DISABLED;
        stencilReference = 0;
        stencilCompareReference = 0;
        stencilClearRequested = false;
        currentRenderPassStencilAttachment = false;
        String key = runtimeStencilDisableReason + "|" + (t == null ? "null" : t.getClass().getSimpleName());
        DebugLog.errorOnChange("shapeclip.vulkan.disabled.after.failure", key,
                "[ShapeClip/Vulkan] disabling stencil after failure: reason=%s error=%s: %s",
                runtimeStencilDisableReason,
                t == null ? "none" : t.getClass().getSimpleName(),
                t == null ? "" : t.getMessage());
    }

    public static boolean forceDisableMsaa() {
        return runtimeMsaaDisabled;
    }

    public static String msaaDisableReason() {
        return runtimeMsaaDisableReason;
    }

    public static void disableMsaaAfterFailure(String reason, Throwable t) {
        runtimeMsaaDisabled = true;
        runtimeMsaaDisableReason = reason == null ? "runtime-failure" : reason;
        String key = runtimeMsaaDisableReason + "|" + (t == null ? "null" : t.getClass().getSimpleName());
        DebugLog.errorOnChange("msaa.vulkan.disabled.after.failure", key,
                "[MSAA/Vulkan] disabling MSAA for future render passes: reason=%s error=%s: %s",
                runtimeMsaaDisableReason,
                t == null ? "none" : t.getClass().getSimpleName(),
                t == null ? "" : t.getMessage());
    }

    public static boolean forceDisableCheckpoints() {
        return VulkanRuntimeGuards.FORCE_DISABLE_VULKAN_CHECKPOINTS;
    }

    public static boolean forceDisableDebugLabels() {
        return VulkanRuntimeGuards.FORCE_DISABLE_VULKAN_DEBUG_LABELS;
    }

    private static final ThreadLocal<Integer> REQUESTED_TEXTURE_SAMPLES = ThreadLocal.withInitial(() -> 1);
    private static final ThreadLocal<RenderPipeline> COMPILING_PIPELINE = new ThreadLocal<>();
    private static volatile boolean vulkanBackendActive;
    private static volatile int currentRenderPassSamples = 1;
    private static volatile StencilMode stencilMode = StencilMode.DISABLED;
    private static volatile int stencilReference;
    private static volatile int stencilCompareReference;
    private static volatile boolean stencilClearRequested;
    private static volatile boolean currentRenderPassStencilAttachment;
    private static volatile boolean runtimeStencilDisabled;
    private static volatile String runtimeStencilDisableReason = "none";
    private static volatile boolean runtimeMsaaDisabled;
    private static volatile String runtimeMsaaDisableReason = "none";
    private static volatile int supportedFramebufferSampleCounts = VK_SAMPLE_COUNT_1_BIT;
    private static volatile int supportedDepthResolveModes;
    private static volatile boolean independentResolveNone;
    private static volatile boolean computeShadersSupported;
    private static volatile boolean tessellationShadersSupported;
    private static volatile boolean geometryShadersSupported;
    private static volatile boolean shaderStorageBuffersSupported;

    public static void setVulkanBackendActive(boolean active) {
        vulkanBackendActive = active;
        if (!active) {
            currentRenderPassSamples = 1;
            stencilMode = StencilMode.DISABLED;
            stencilReference = 0;
            stencilCompareReference = 0;
            stencilClearRequested = false;
            currentRenderPassStencilAttachment = false;
            runtimeStencilDisabled = false;
            runtimeStencilDisableReason = "none";
            runtimeMsaaDisabled = false;
            runtimeMsaaDisableReason = "none";
            COMPILING_PIPELINE.remove();
        }
    }

    public static boolean vulkanBackendActive() {
        return vulkanBackendActive;
    }

    public static int requestedTextureSamples() {
        if (forceDisableMsaa()) return 1;
        return selectSupportedSamples(REQUESTED_TEXTURE_SAMPLES.get());
    }

    public static <T> T withTextureSamples(int samples, java.util.function.Supplier<T> factory) {
        int previous = REQUESTED_TEXTURE_SAMPLES.get();
        REQUESTED_TEXTURE_SAMPLES.set(forceDisableMsaa() ? 1 : selectSupportedSamples(samples));
        try {
            return factory.get();
        } finally {
            REQUESTED_TEXTURE_SAMPLES.set(previous);
        }
    }

    public static int normalizeSamples(int samples) {
        return forceDisableMsaa() ? 1 : selectSupportedSamples(samples);
    }

    public static int currentRenderPassSamples() {
        return Math.max(1, currentRenderPassSamples);
    }

    public static int currentVkSampleCount() {
        return Math.max(1, currentRenderPassSamples);
    }

    public static void configureMsaaCapabilities(int framebufferSampleCounts,
                                                 int depthResolveModes,
                                                 boolean supportsIndependentResolveNone) {
        supportedFramebufferSampleCounts = framebufferSampleCounts | VK_SAMPLE_COUNT_1_BIT;
        supportedDepthResolveModes = depthResolveModes;
        independentResolveNone = supportsIndependentResolveNone;
    }

    public static void configureAdvancedShaderCapabilities(boolean compute,
                                                           boolean tessellation,
                                                           boolean geometry,
                                                           boolean storageBuffers) {
        computeShadersSupported = compute;
        tessellationShadersSupported = tessellation;
        geometryShadersSupported = geometry;
        shaderStorageBuffersSupported = storageBuffers;
    }

    public static boolean computeShadersSupported() {
        return computeShadersSupported;
    }

    public static boolean tessellationShadersSupported() {
        return tessellationShadersSupported;
    }

    public static boolean geometryShadersSupported() {
        return geometryShadersSupported;
    }

    public static boolean shaderStorageBuffersSupported() {
        return shaderStorageBuffersSupported;
    }

    public static boolean msaaSupported() {
        return !forceDisableMsaa()
                && (supportedFramebufferSampleCounts & ~VK_SAMPLE_COUNT_1_BIT) != 0
                && supportedDepthResolveModes != VK_RESOLVE_MODE_NONE;
    }

    public static int depthResolveMode() {
        // Minecraft uses reversed-Z, so larger depth is closer. Prefer MAX to preserve the
        // closest covered sample when resolving multisampled scene depth. SAMPLE_ZERO can
        // punch holes at geometry edges depending on which sample happens to be selected.
        if ((supportedDepthResolveModes & VK_RESOLVE_MODE_MAX_BIT) != 0) {
            return VK_RESOLVE_MODE_MAX_BIT;
        }
        if ((supportedDepthResolveModes & VK_RESOLVE_MODE_SAMPLE_ZERO_BIT) != 0) {
            return VK_RESOLVE_MODE_SAMPLE_ZERO_BIT;
        }
        throw new IllegalStateException("Vulkan device exposes no supported depth resolve mode");
    }

    public static boolean independentResolveNone() {
        return independentResolveNone;
    }

    private static int selectSupportedSamples(int requested) {
        if (requested <= 1) return 1;
        int capped = Math.min(requested, 16);
        for (int candidate = 16; candidate >= 2; candidate >>= 1) {
            if (candidate <= capped && (supportedFramebufferSampleCounts & candidate) != 0) {
                return candidate;
            }
        }
        return 1;
    }

    public static void beginRenderPass(RenderPassDescriptor descriptor) {
        if (!vulkanBackendActive) return;
        int samples = validateAndGetSamples(descriptor);
        currentRenderPassSamples = samples;
        currentRenderPassStencilAttachment = !forceDisableStencil() && stencilAttachmentRequiredForNextPass();
        DebugLog.stencilOnChange("vulkan.renderpass.compat",
                samples + "|" + currentRenderPassStencilAttachment + "|" + stencilMode,
                "[Vulkan/RHI] render pass compatibility: samples=%d stencilAttachment=%s stencilMode=%s",
                samples, currentRenderPassStencilAttachment, stencilMode);
    }

    public static void endRenderPass() {
        currentRenderPassSamples = 1;
        currentRenderPassStencilAttachment = false;
        COMPILING_PIPELINE.remove();
    }

    private static boolean stencilAttachmentRequiredForNextPass() {
        if (forceDisableStencil()) return false;
        return stencilMode != StencilMode.DISABLED || stencilClearRequested;
    }

    public static boolean currentRenderPassNeedsStencilAttachment() {
        return vulkanBackendActive && !forceDisableStencil() && currentRenderPassStencilAttachment;
    }

    public static void markCurrentRenderPassStencilAttachment(boolean attached) {
        currentRenderPassStencilAttachment = vulkanBackendActive && !forceDisableStencil() && attached;
    }

    public static boolean currentRenderPassHasStencilAttachment() {
        return vulkanBackendActive && !forceDisableStencil() && currentRenderPassStencilAttachment;
    }

    public static int samplesFor(@Nullable RenderPassDescriptor descriptor) {
        return validateAndGetSamples(descriptor);
    }

    private static int validateAndGetSamples(@Nullable RenderPassDescriptor descriptor) {
        if (descriptor == null) return 1;
        int samples = 0;
        List<RenderPassDescriptor.Attachment<java.util.Optional<org.joml.Vector4fc>>> colors = descriptor.colorAttachments();
        if (colors != null) {
            for (RenderPassDescriptor.Attachment<?> attachment : colors) {
                samples = mergeAttachmentSamples(samples, samplesFor(attachment));
            }
        }
        samples = mergeAttachmentSamples(samples, samplesFor(descriptor.depthAttachment()));
        if (samples == 0) samples = 1;
        if (samples > 1 && (forceDisableMsaa() || (supportedFramebufferSampleCounts & samples) == 0)) {
            throw new IllegalStateException("Unsupported Vulkan render-pass sample count: " + samples
                    + " (supported mask=0x" + Integer.toHexString(supportedFramebufferSampleCounts) + ")");
        }
        return samples;
    }

    private static int mergeAttachmentSamples(int current, int attachmentSamples) {
        if (attachmentSamples <= 0) return current;
        if (current == 0) return attachmentSamples;
        if (current != attachmentSamples) {
            throw new IllegalStateException("Vulkan render-pass attachment sample mismatch: "
                    + current + "x versus " + attachmentSamples + "x");
        }
        return current;
    }

    private static int samplesFor(@Nullable RenderPassDescriptor.Attachment<?> attachment) {
        if (attachment == null || attachment.textureView() == null || attachment.textureView().texture() == null) {
            return 0;
        }
        if (attachment.textureView().texture() instanceof IMsaaTexture texture && texture.combatant$isMsaa()) {
            return Math.max(1, texture.combatant$getSamples());
        }
        return 1;
    }

    public static int samplesFor(@Nullable GpuTextureView view) {
        if (view == null || view.texture() == null) return 1;
        if (view.texture() instanceof IMsaaTexture texture && texture.combatant$isMsaa()) {
            return Math.max(1, texture.combatant$getSamples());
        }
        return 1;
    }

    public static void setStencilMode(StencilMode mode, int reference, int compareReference) {
        if (forceDisableStencil()) {
            stencilMode = StencilMode.DISABLED;
            stencilReference = 0;
            stencilCompareReference = 0;
            stencilClearRequested = false;
            currentRenderPassStencilAttachment = false;
            return;
        }
        stencilMode = mode == null ? StencilMode.DISABLED : mode;
        stencilReference = clampStencilReference(reference);
        stencilCompareReference = clampStencilReference(compareReference);
    }

    public static void requestStencilClear() {
        if (forceDisableStencil()) return;
        stencilClearRequested = true;
    }

    public static boolean consumeStencilClearRequested() {
        if (forceDisableStencil()) {
            stencilClearRequested = false;
            return false;
        }
        boolean requested = stencilClearRequested;
        stencilClearRequested = false;
        return requested;
    }

    public static StencilMode stencilMode() {
        return stencilMode;
    }

    public static int stencilReference() {
        return stencilReference;
    }

    public static int stencilCompareReference() {
        return stencilCompareReference;
    }

    public static boolean pipelineParticipatesInShapeClip(RenderPipeline pipeline) {
        if (forceDisableStencil()) return false;
        if (!(pipeline instanceof IRenderPipeline combatantPipeline)) return false;
        ShapeClipRenderPassContract contract = combatantPipeline.combatant$getShapeClipContract();
        return contract != null && contract.participates();
    }

    /**
     * Dynamic rendering compatibility is per active render pass, not per logical clip contract.
     * If pStencilAttachment is present, every graphics pipeline used in that dynamic rendering scope
     * must be compiled with the same stencilAttachmentFormat. Non-participating pipelines simply keep
     * stencilTestEnable=false.
     */
    public static boolean pipelineNeedsStencilFormat(RenderPipeline pipeline) {
        return currentRenderPassHasStencilAttachment();
    }

    public static boolean stencilTestEnabledForCurrentPipeline(RenderPipeline pipeline) {
        return currentRenderPassHasStencilAttachment()
                && pipelineParticipatesInShapeClip(pipeline)
                && stencilMode != StencilMode.DISABLED;
    }

    public static boolean suppressColorWrites() {
        RenderPipeline pipeline = COMPILING_PIPELINE.get();
        return currentRenderPassHasStencilAttachment()
                && pipeline != null
                && pipelineParticipatesInShapeClip(pipeline)
                && (stencilMode == StencilMode.WRITE || stencilMode == StencilMode.RESTORE);
    }

    public static void beginPipelineCompile(RenderPipeline pipeline) {
        COMPILING_PIPELINE.set(pipeline);
    }

    public static void endPipelineCompile() {
        COMPILING_PIPELINE.remove();
    }

    public static boolean needsPipelineVariant(RenderPipeline pipeline) {
        if (!vulkanBackendActive) return false;
        return currentRenderPassHasStencilAttachment() || currentRenderPassSamples() != 1;
    }

    public static PipelineVariantKey pipelineVariantKey(RenderPipeline pipeline) {
        StencilMode effectiveMode = stencilTestEnabledForCurrentPipeline(pipeline) ? stencilMode : StencilMode.DISABLED;
        boolean stencilFormat = currentRenderPassHasStencilAttachment();
        return new PipelineVariantKey(
                pipeline,
                currentRenderPassSamples(),
                stencilFormat,
                effectiveMode,
                pipelineParticipatesInShapeClip(pipeline)
        );
    }

    public static int dynamicStencilReference() {
        return stencilCompareReference;
    }

    private static int clampStencilReference(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public enum StencilMode {
        DISABLED,
        WRITE,
        RESTORE,
        TEST
    }

    public static final class PipelineVariantKey {
        private final RenderPipeline pipeline;
        private final int pipelineIdentity;
        private final int samples;
        private final boolean stencilFormat;
        private final StencilMode stencilMode;
        private final boolean shapeClipParticipant;

        public PipelineVariantKey(RenderPipeline pipeline, int samples, boolean stencilFormat, StencilMode stencilMode, boolean shapeClipParticipant) {
            this.pipeline = pipeline;
            this.pipelineIdentity = System.identityHashCode(pipeline);
            this.samples = samples;
            this.stencilFormat = stencilFormat;
            this.stencilMode = stencilMode == null ? StencilMode.DISABLED : stencilMode;
            this.shapeClipParticipant = shapeClipParticipant;
        }

        public boolean usesMsaa() {
            return samples > 1;
        }

        public boolean usesStencil() {
            return stencilFormat;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof PipelineVariantKey other)) return false;
            return pipeline == other.pipeline
                    && samples == other.samples
                    && stencilFormat == other.stencilFormat
                    && stencilMode == other.stencilMode
                    && shapeClipParticipant == other.shapeClipParticipant;
        }

        @Override
        public int hashCode() {
            int result = pipelineIdentity;
            result = 31 * result + samples;
            result = 31 * result + (stencilFormat ? 1 : 0);
            result = 31 * result + stencilMode.hashCode();
            result = 31 * result + (shapeClipParticipant ? 1 : 0);
            return result;
        }

        @Override
        public String toString() {
            return "PipelineVariantKey{" +
                    "pipeline=" + pipeline +
                    ", samples=" + samples +
                    ", stencilFormat=" + stencilFormat +
                    ", stencilMode=" + stencilMode +
                    ", shapeClipParticipant=" + shapeClipParticipant +
                    '}';
        }
    }
}
