/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixininterface;

import org.lwjgl.vulkan.VkCommandBuffer;

/** Access to Mojang's current Vulkan command buffer so native Combatant commands share its submission. */
public interface IVulkanCommandEncoderAccess {
    VkCommandBuffer combatant$commandBuffer();

    /** True while Mojang owns an active dynamic-rendering pass on this encoder. */
    boolean combatant$renderPassActive();

    /** Mojang submission generation; used only for submit-local transient native resources. */
    long combatant$currentSubmitIndex();
}
