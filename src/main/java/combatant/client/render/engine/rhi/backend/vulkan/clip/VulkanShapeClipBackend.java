/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.vulkan.clip;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;
import combatant.client.render.engine.rhi.clip.ShapeClipBackend;
import combatant.client.render.engine.rhi.clip.ShapeClipRenderPassContract;
import combatant.client.util.logging.DebugLog;

/**
 * Vulkan implementation of Combatant shape clipping using an injected S8 dynamic-rendering attachment.
 */
public final class VulkanShapeClipBackend implements ShapeClipBackend, AutoCloseable {
    private final VulkanStencilAttachmentManager attachments = new VulkanStencilAttachmentManager();

    private VulkanRenderStateBridge.StencilMode mode = VulkanRenderStateBridge.StencilMode.DISABLED;
    private int reference;
    private int compareReference;
    private boolean renderPassAttachmentRequired;
    private String attachmentReason = "none";
    private String currentPassLabel = "<no-pass>";
    private String currentPipelineName = "<no-pipeline>";
    private ShapeClipRenderPassContract currentPipelineContract = ShapeClipRenderPassContract.NONE;

    public VulkanShapeClipBackend() {
        VulkanShapeClipBridge.install(attachments);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @Override
    public boolean supported() {
        return VulkanRenderStateBridge.vulkanBackendActive() && !VulkanRenderStateBridge.forceDisableStencil();
    }

    @Override
    public boolean isActive() {
        return supported() && mode != VulkanRenderStateBridge.StencilMode.DISABLED;
    }

    @Override
    public void requireRenderPassAttachment(String reason) {
        if (!supported()) return;
        renderPassAttachmentRequired = true;
        attachmentReason = reason == null ? "unspecified" : reason;
    }

    @Override
    public void requestClear(String reason) {
        if (!supported()) return;
        VulkanRenderStateBridge.requestStencilClear();
    }

    @Override
    public void beginRenderPass(String label, @Nullable GpuTextureView colorView, @Nullable GpuTextureView depthView) {
        currentPassLabel = label == null ? "<unnamed-pass>" : label;
        if (renderPassAttachmentRequired || isActive()) {
            DebugLog.stencilOnChange("shapeclip.vulkan.pass.required", currentPassLabel + "|" + attachmentReason,
                    "[ShapeClip/Vulkan] stencil attachment required: pass=%s reason=%s",
                    currentPassLabel, attachmentReason);
        }
    }

    @Override
    public void endRenderPass() {
        currentPassLabel = "<no-pass>";
        renderPassAttachmentRequired = false;
        attachmentReason = "none";
        currentPipelineContract = ShapeClipRenderPassContract.NONE;
        currentPipelineName = "<no-pipeline>";
    }

    @Override
    public void bindPipeline(RenderPipeline pipeline, ShapeClipRenderPassContract contract) {
        currentPipelineContract = contract == null ? ShapeClipRenderPassContract.NONE : contract;
        currentPipelineName = pipeline == null || pipeline.getLocation() == null ? "<unknown>" : pipeline.getLocation().toString();
        if (mode != VulkanRenderStateBridge.StencilMode.DISABLED && currentPipelineContract == ShapeClipRenderPassContract.NONE) {
            DebugLog.warnOnce(
                    "shapeclip.vulkan.stale.pipeline.contract." + currentPipelineName,
                    "[ShapeClip/Vulkan] stale clip state risk: active mode=%s ref=%d but pipeline has no shape-clip contract. pipeline=%s pass=%s",
                    mode,
                    reference,
                    currentPipelineName,
                    currentPassLabel
            );
        }
        if (currentPipelineContract.requiresAttachment(mode != VulkanRenderStateBridge.StencilMode.DISABLED || renderPassAttachmentRequired)) {
            requireRenderPassAttachment("pipeline contract " + currentPipelineContract + " for " + currentPipelineName);
        }
    }

    @Override
    public boolean prepareMainTarget() {
        return supported();
    }

    @Override
    public boolean prepare(@Nullable GpuTextureView colorView, @Nullable GpuTextureView depthView) {
        return supported();
    }

    @Override
    public boolean clearMainTarget() {
        requestClear("manual clearMainTarget");
        return supported();
    }

    @Override
    public void beginWrite(int parentReference, int newReference) {
        if (!supported()) return;
        mode = VulkanRenderStateBridge.StencilMode.WRITE;
        compareReference = clamp(parentReference);
        reference = clamp(newReference);
        requireRenderPassAttachment("mask write parent=" + compareReference + " ref=" + reference);
        VulkanRenderStateBridge.setStencilMode(mode, reference, compareReference);
    }

    @Override
    public void beginRestore(int currentReference, int parentReference) {
        if (!supported()) return;
        mode = VulkanRenderStateBridge.StencilMode.RESTORE;
        compareReference = clamp(currentReference);
        reference = clamp(parentReference);
        requireRenderPassAttachment("mask restore current=" + compareReference + " parent=" + reference);
        VulkanRenderStateBridge.setStencilMode(mode, reference, compareReference);
    }

    @Override
    public void beginTest(int activeReference) {
        if (!supported()) return;
        mode = VulkanRenderStateBridge.StencilMode.TEST;
        reference = clamp(activeReference);
        compareReference = reference;
        requireRenderPassAttachment("content test ref=" + reference);
        VulkanRenderStateBridge.setStencilMode(mode, reference, compareReference);
    }

    @Override
    public void disable() {
        mode = VulkanRenderStateBridge.StencilMode.DISABLED;
        reference = 0;
        compareReference = 0;
        renderPassAttachmentRequired = false;
        attachmentReason = "none";
        VulkanRenderStateBridge.setStencilMode(mode, 0, 0);
    }

    @Override
    public void applyNativeState() {
        if (!supported()) {
            VulkanRenderStateBridge.setStencilMode(VulkanRenderStateBridge.StencilMode.DISABLED, 0, 0);
            return;
        }
        VulkanRenderStateBridge.setStencilMode(mode, reference, compareReference);
    }

    @Override
    public String debugState() {
        return "VulkanShapeClipBackend{" +
                "supported=" + supported() +
                ", runtimeDisabled=" + VulkanRenderStateBridge.forceDisableStencil() +
                ", disableReason=" + VulkanRenderStateBridge.stencilDisableReason() +
                ", mode=" + mode +
                ", ref=" + reference +
                ", compareRef=" + compareReference +
                ", pass='" + currentPassLabel + '\'' +
                ", pipeline='" + currentPipelineName + '\'' +
                ", contract=" + currentPipelineContract +
                '}';
    }

    @Override
    public String lastFailureReason() {
        return "none";
    }

    @Override
    public void close() {
        VulkanShapeClipBridge.uninstall(attachments);
        attachments.close();
        disable();
    }
}
