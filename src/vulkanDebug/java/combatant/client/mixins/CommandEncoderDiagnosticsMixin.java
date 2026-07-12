package combatant.client.mixins;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.render.engine.rhi.backend.vulkan.debug.VulkanCrashDiagnostics;

@Mixin(CommandEncoder.class)
public abstract class CommandEncoderDiagnosticsMixin {
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

    @Unique
    private static String combatant$label(RenderPassDescriptor descriptor) {
        if (descriptor == null || descriptor.label() == null) return "<null-label>";
        try {
            String label = descriptor.label().get();
            return label == null ? "<null-label>" : label;
        } catch (Throwable t) {
            return "<label-error:" + t.getClass().getSimpleName() + ">";
        }
    }

    @Inject(method = "createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)Lcom/mojang/blaze3d/systems/RenderPass;", at = @At("HEAD"))
    private void combatant$wrapperCreateHead(RenderPassDescriptor descriptor, CallbackInfoReturnable<RenderPass> cir) {
        if (!combatant$isVulkanBackend()) return;
        VulkanCrashDiagnostics.breadcrumbQuiet("commandencoder.wrapper.create.head",
                "label", combatant$label(descriptor),
                "colors", descriptor == null || descriptor.colorAttachments() == null ? -1 : descriptor.colorAttachments().size(),
                "depth", descriptor != null && descriptor.depthAttachment() != null);
    }

    @Inject(method = "createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)Lcom/mojang/blaze3d/systems/RenderPass;", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoderBackend;createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)Lcom/mojang/blaze3d/systems/RenderPassBackend;", shift = At.Shift.BEFORE))
    private void combatant$backendCreateBefore(RenderPassDescriptor descriptor, CallbackInfoReturnable<RenderPass> cir) {
        if (!combatant$isVulkanBackend()) return;
        VulkanCrashDiagnostics.breadcrumbQuiet("commandencoder.wrapper.backend.create.before", "label", combatant$label(descriptor));
    }

    @Inject(method = "createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)Lcom/mojang/blaze3d/systems/RenderPass;", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoderBackend;createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)Lcom/mojang/blaze3d/systems/RenderPassBackend;", shift = At.Shift.AFTER))
    private void combatant$backendCreateAfter(RenderPassDescriptor descriptor, CallbackInfoReturnable<RenderPass> cir) {
        if (!combatant$isVulkanBackend()) return;
        VulkanCrashDiagnostics.breadcrumbQuiet("commandencoder.wrapper.backend.create.after", "label", combatant$label(descriptor));
    }

    @Inject(method = "createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)Lcom/mojang/blaze3d/systems/RenderPass;", at = @At("RETURN"))
    private void combatant$wrapperCreateReturn(RenderPassDescriptor descriptor, CallbackInfoReturnable<RenderPass> cir) {
        if (!combatant$isVulkanBackend()) return;
        VulkanCrashDiagnostics.breadcrumbQuiet("commandencoder.wrapper.create.return",
                "label", combatant$label(descriptor),
                "pass", cir.getReturnValue() == null ? "<null>" : cir.getReturnValue().getClass().getName());
    }

    @Inject(method = "submitRenderPass", at = @At("HEAD"))
    private void combatant$wrapperSubmitHead(CallbackInfo ci) {
        if (!combatant$isVulkanBackend()) return;
        VulkanCrashDiagnostics.breadcrumbQuiet("commandencoder.wrapper.submit.head");
    }

    @Inject(method = "submitRenderPass", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoderBackend;submitRenderPass()V", shift = At.Shift.BEFORE))
    private void combatant$backendSubmitBefore(CallbackInfo ci) {
        if (!combatant$isVulkanBackend()) return;
        VulkanCrashDiagnostics.breadcrumbQuiet("commandencoder.wrapper.backend.submit.before");
    }

    @Inject(method = "submitRenderPass", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoderBackend;submitRenderPass()V", shift = At.Shift.AFTER))
    private void combatant$backendSubmitAfter(CallbackInfo ci) {
        if (!combatant$isVulkanBackend()) return;
        VulkanCrashDiagnostics.breadcrumbQuiet("commandencoder.wrapper.backend.submit.after");
    }

    @Inject(method = "submitRenderPass", at = @At("TAIL"))
    private void combatant$wrapperSubmitTail(CallbackInfo ci) {
        if (!combatant$isVulkanBackend()) return;
        VulkanCrashDiagnostics.breadcrumbQuiet("commandencoder.wrapper.submit.tail");
    }
}
