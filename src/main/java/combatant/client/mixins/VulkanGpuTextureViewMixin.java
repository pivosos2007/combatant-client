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
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static org.lwjgl.vulkan.VK12.VK_IMAGE_ASPECT_STENCIL_BIT;

@Mixin(VulkanGpuTextureView.class)
public abstract class VulkanGpuTextureViewMixin {
    /**
     * Mojang 26.2 treats every non-color view as depth. A pure S8_UINT attachment must use
     * STENCIL_BIT or vkCreateImageView is invalid and dynamic rendering cannot bind it as
     * pStencilAttachment.
     */
    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkImageSubresourceRange;aspectMask(I)Lorg/lwjgl/vulkan/VkImageSubresourceRange;")
    )
    private VkImageSubresourceRange combatant$stencilViewAspect(VkImageSubresourceRange range,
                                                                 int original,
                                                                 VulkanDevice device,
                                                                 VulkanGpuTexture texture,
                                                                 int baseMipLevel,
                                                                 int mipLevels) {
        GpuFormat format = texture == null ? null : texture.getFormat();
        if (format != null && format.hasStencilAspect() && !format.hasDepthAspect()) {
            return range.aspectMask(VK_IMAGE_ASPECT_STENCIL_BIT);
        }
        return range.aspectMask(original);
    }
}
