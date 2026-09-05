/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.rhi.scissor.GlobalScissorState;
import combatant.client.util.logging.DebugLog;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

@Mixin(CommandEncoder.class)
public abstract class CommandEncoderMixin {
    @Unique
    private static String combatant$label(Supplier<String> label) {
        try {
            return label != null ? label.get() : "<null-label>";
        } catch (Throwable t) {
            return "<label-threw-" + t.getClass().getSimpleName() + ">";
        }
    }

    @Unique
    private static @Nullable GpuTextureView combatant$firstColorView(
            List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> colorAttachments
    ) {
        if (colorAttachments == null) {
            return null;
        }
        for (RenderPassDescriptor.Attachment<Optional<Vector4fc>> attachment : colorAttachments) {
            if (attachment != null) {
                return attachment.textureView();
            }
        }
        return null;
    }

    @Unique
    private static @Nullable GpuTextureView combatant$depthView(
            @Nullable RenderPassDescriptor.Attachment<OptionalDouble> depthAttachment
    ) {
        return depthAttachment == null ? null : depthAttachment.textureView();
    }


    @Unique
    private static boolean combatant$isVulkanBackend() {
        try {
            if (RenderSystem.getDevice() == null || RenderSystem.getDevice().getDeviceInfo() == null) return false;
            String backendName = RenderSystem.getDevice().getDeviceInfo().backendName();
            return backendName != null && backendName.toLowerCase(java.util.Locale.ROOT).contains("vulkan");
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Inject(method = "createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)Lcom/mojang/blaze3d/systems/RenderPass;", at = @At("RETURN"))
    private void combatant$onCreateRenderPass(
            RenderPassDescriptor descriptor,
            CallbackInfoReturnable<RenderPass> cir
    ) {
        RenderPass pass = cir.getReturnValue();
        if (pass == null || descriptor == null) {
            return;
        }
        try {
            // A newly created Blaze3D pass is an explicit ownership boundary. GL-native state
            // not represented by GlStateManager must be considered unknown before Combatant applies it.
            CombatantRenderSystem.rhi().pipelineState().invalidateForeignState();
        } catch (Throwable t) {
            DebugLog.error("[RHI] render pass native-state boundary hook failed for %s", t, combatant$label(descriptor.label()));
        }
        try {
            GlobalScissorState.applyTo(pass, descriptor.renderArea);
        } catch (Throwable t) {
            DebugLog.error("[Scissor] render pass scissor hook failed for %s", t, combatant$label(descriptor.label()));
        }

        try {
            CombatantRenderSystem.rhi().shapeClip().beginRenderPass(
                    combatant$label(descriptor.label()),
                    combatant$firstColorView(descriptor.colorAttachments()),
                    combatant$depthView(descriptor.depthAttachment())
            );
        } catch (Throwable t) {
            DebugLog.error("[ShapeClip] beginRenderPass hook failed for %s", t, combatant$label(descriptor.label()));
        }
    }

    @Inject(method = "submitRenderPass", at = @At("HEAD"))
    private void combatant$resetRhiRenderPassState(CallbackInfo ci) {
        if (combatant$isVulkanBackend()) {
            return;
        }
        try {
            CombatantRenderSystem.rhi().pipelineState().resetRenderPassState();
        } catch (Throwable t) {
            DebugLog.error("[ShapeClip/GL] render pass reset hook failed", t);
        }
    }

    @Inject(method = "submitRenderPass", at = @At("RETURN"))
    private void combatant$invalidateRhiNativeStateAfterSubmit(CallbackInfo ci) {
        if (combatant$isVulkanBackend()) {
            return;
        }
        try {
            // Blaze3D has just executed/closed a pass and may have changed native GL state behind
            // Combatant's narrow stencil/MSAA tracker. Never carry that shadow into the next pass.
            CombatantRenderSystem.rhi().pipelineState().invalidateForeignState();
        } catch (Throwable t) {
            DebugLog.error("[RHI/GL] post-render-pass native-state invalidation failed", t);
        }
    }

    @Redirect(
            method = "submit",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoderBackend;submit()V")
    )
    private void combatant$profileCommandSubmit(CommandEncoderBackend backend) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("gpu_api:submit")) {
            backend.submit();
        }
    }

    @Redirect(
            method = "createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)Lcom/mojang/blaze3d/systems/RenderPass;",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoderBackend;createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)Lcom/mojang/blaze3d/systems/RenderPassBackend;")
    )
    private RenderPassBackend combatant$profileCreateRenderPass(CommandEncoderBackend backend, RenderPassDescriptor descriptor) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("gpu_api:create_render_pass")) {
            return backend.createRenderPass(descriptor);
        }
    }

    @Redirect(
            method = "submitRenderPass",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoderBackend;submitRenderPass()V")
    )
    private void combatant$profileSubmitRenderPass(CommandEncoderBackend backend) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("gpu_api:submit_render_pass")) {
            backend.submitRenderPass();
        }
    }

    @Redirect(
            method = "writeToBuffer",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoderBackend;writeToBuffer(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Ljava/nio/ByteBuffer;)V")
    )
    private void combatant$profileWriteBuffer(CommandEncoderBackend backend, GpuBufferSlice buffer, ByteBuffer data) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("gpu_api:write_buffer")) {
            backend.writeToBuffer(buffer, data);
        }
    }

    @Redirect(
            method = "copyToBuffer",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoderBackend;copyToBuffer(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V")
    )
    private void combatant$profileCopyBuffer(CommandEncoderBackend backend, GpuBufferSlice source, GpuBufferSlice target) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("gpu_api:copy_buffer")) {
            backend.copyToBuffer(source, target);
        }
    }

    @Redirect(
            method = "writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Ljava/nio/ByteBuffer;IIIIII)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoderBackend;writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Ljava/nio/ByteBuffer;IIIIII)V")
    )
    private void combatant$profileWriteTexture(CommandEncoderBackend backend,
                                                GpuTexture texture,
                                                ByteBuffer data,
                                                int mipLevel,
                                                int x,
                                                int y,
                                                int width,
                                                int height,
                                                int rowPitch) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("gpu_api:write_texture")) {
            backend.writeToTexture(texture, data, mipLevel, x, y, width, height, rowPitch);
        }
    }

    @Redirect(
            method = "copyBufferToTexture",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoderBackend;copyBufferToTexture(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;IIIILcom/mojang/blaze3d/textures/GpuTexture;IIIIII)V")
    )
    private void combatant$profileCopyBufferToTexture(CommandEncoderBackend backend,
                                                       GpuBufferSlice source,
                                                       int rowPitch,
                                                       int imageHeight,
                                                       int sourceOffsetX,
                                                       int sourceOffsetY,
                                                       GpuTexture target,
                                                       int mipLevel,
                                                       int targetX,
                                                       int targetY,
                                                       int width,
                                                       int height,
                                                       int depth) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("gpu_api:copy_buffer_to_texture")) {
            backend.copyBufferToTexture(source, rowPitch, imageHeight, sourceOffsetX, sourceOffsetY,
                    target, mipLevel, targetX, targetY, width, height, depth);
        }
    }

    @Redirect(
            method = "copyTextureToBuffer(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/buffers/GpuBuffer;JLjava/lang/Runnable;I)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoderBackend;copyTextureToBuffer(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/buffers/GpuBuffer;JLjava/lang/Runnable;I)V")
    )
    private void combatant$profileCopyTextureToBuffer(CommandEncoderBackend backend,
                                                       GpuTexture source,
                                                       GpuBuffer target,
                                                       long offset,
                                                       Runnable callback,
                                                       int mipLevel) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("gpu_api:readback_texture")) {
            backend.copyTextureToBuffer(source, target, offset, callback, mipLevel);
        }
    }

    @Redirect(
            method = "copyTextureToBuffer(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/buffers/GpuBuffer;JLjava/lang/Runnable;IIIII)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoderBackend;copyTextureToBuffer(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/buffers/GpuBuffer;JLjava/lang/Runnable;IIIII)V")
    )
    private void combatant$profileCopyTextureRegionToBuffer(CommandEncoderBackend backend,
                                                             GpuTexture source,
                                                             GpuBuffer target,
                                                             long offset,
                                                             Runnable callback,
                                                             int mipLevel,
                                                             int x,
                                                             int y,
                                                             int width,
                                                             int height) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("gpu_api:readback_texture_region")) {
            backend.copyTextureToBuffer(source, target, offset, callback, mipLevel, x, y, width, height);
        }
    }

    @Redirect(
            method = "copyTextureToTexture",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoderBackend;copyTextureToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/textures/GpuTexture;IIIIIII)V")
    )
    private void combatant$profileCopyTexture(CommandEncoderBackend backend,
                                               GpuTexture source,
                                               GpuTexture target,
                                               int sourceX,
                                               int sourceY,
                                               int targetX,
                                               int targetY,
                                               int width,
                                               int height,
                                               int mipLevel) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("gpu_api:copy_texture")) {
            backend.copyTextureToTexture(source, target, sourceX, sourceY, targetX, targetY, width, height, mipLevel);
        }
    }

    @Redirect(
            method = "createFence",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoderBackend;createFence()Lcom/mojang/blaze3d/buffers/GpuFence;")
    )
    private GpuFence combatant$profileCreateFence(CommandEncoderBackend backend) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("gpu_api:create_fence")) {
            return backend.createFence();
        }
    }

}
