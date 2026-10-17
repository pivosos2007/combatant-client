/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixininterface;

import com.mojang.renderpearl.backend.vulkan.VulkanPhysicalDevice;
import org.lwjgl.vulkan.VkDevice;

/**
 * Minimal bridge to the exact Vulkan device/VMA allocator already owned by Mojang.
 * Combatant must not create a second VkDevice or VMA allocator.
 */
public interface IVulkanBackendInfo {
    VkDevice combatant$vkDevice();

    long combatant$vma();

    VulkanPhysicalDevice combatant$physicalDevice();

    /**
     * Immutable device-limit snapshot captured while Mojang's VulkanPhysicalDevice properties
     * are known to be valid. Do not re-read native-backed VkPhysicalDeviceProperties later.
     */
    long combatant$minStorageBufferOffsetAlignment();

    long combatant$maxStorageBufferRange();
}
