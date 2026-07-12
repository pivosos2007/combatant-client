/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.scissor.GlobalScissorState;
import combatant.client.util.logging.DebugLog;

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
}
