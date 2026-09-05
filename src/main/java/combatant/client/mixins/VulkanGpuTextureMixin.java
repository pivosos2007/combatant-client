/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRenderStateBridge;
import combatant.client.render.engine.rhi.shader.RhiTextureUsage;
import org.lwjgl.vulkan.VkImageSubresourceRange;

import static org.lwjgl.vulkan.VK12.*;

@Mixin(VulkanGpuTexture.class)
public abstract class VulkanGpuTextureMixin implements IMsaaTexture {
    @Unique
    private int combatant$samples = 1;

    /**
     * Mojang 26.2 maps render-attachment usage only for color/depth aspects. Pure S8_UINT
     * is a valid Vulkan stencil attachment, but without this OR the image is created without
     * VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT and using it in pStencilAttachment is invalid.
     */
    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vulkan/VulkanConst;textureUsageToVk(ILcom/mojang/blaze3d/GpuFormat;)I")
    )
    private int combatant$stencilAttachmentUsage(int usage, GpuFormat format) {
        int vkUsage = com.mojang.blaze3d.vulkan.VulkanConst.textureUsageToVk(usage, format);
        if (format != null && format.hasStencilAspect() && (usage & com.mojang.blaze3d.textures.GpuTexture.USAGE_RENDER_ATTACHMENT) != 0) {
            vkUsage |= VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
        }
        if ((usage & RhiTextureUsage.STORAGE_IMAGE) != 0) {
            vkUsage |= VK_IMAGE_USAGE_STORAGE_BIT;
        }
        return vkUsage;
    }

    /**
     * Mojang picks DEPTH_BIT for every non-color format. For S8_UINT this records an invalid
     * initialization barrier and later the image view is invalid too. Keep the patch scoped to
     * actual stencil formats; color/depth formats stay vanilla.
     */
    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkImageSubresourceRange;aspectMask(I)Lorg/lwjgl/vulkan/VkImageSubresourceRange;")
    )
    private VkImageSubresourceRange combatant$stencilImageAspect(VkImageSubresourceRange range,
                                                                  int original,
                                                                  VulkanDevice device,
                                                                  int usage,
                                                                  String label,
                                                                  GpuFormat format,
                                                                  int width,
                                                                  int height,
                                                                  int depthOrLayers,
                                                                  int mipLevels) {
        if (format != null && format.hasStencilAspect() && !format.hasDepthAspect()) {
            return range.aspectMask(VK_IMAGE_ASPECT_STENCIL_BIT);
        }
        return range.aspectMask(original);
    }

    @ModifyArg(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkImageCreateInfo;samples(I)Lorg/lwjgl/vulkan/VkImageCreateInfo;"),
            index = 0
    )
    private int combatant$useRequestedMsaaSamples(int original) {
        return VulkanRenderStateBridge.requestedTextureSamples();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void combatant$rememberSamples(VulkanDevice device,
                                           int usage,
                                           String label,
                                           GpuFormat format,
                                           int width,
                                           int height,
                                           int depthOrLayers,
                                           int mipLevels,
                                           CallbackInfo ci) {
        combatant$samples = VulkanRenderStateBridge.requestedTextureSamples();
    }

    @Override
    public void combatant$setSamples(int samples) {
        combatant$samples = VulkanRenderStateBridge.normalizeSamples(samples);
    }

    @Override
    public int combatant$getSamples() {
        return combatant$samples;
    }

    @Override
    public boolean combatant$isMsaa() {
        return combatant$samples > 1;
    }

    @Override
    public int combatant$getGlId() {
        return 0;
    }
}
