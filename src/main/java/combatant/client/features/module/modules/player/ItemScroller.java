/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.mixins.accessors.AbstractContainerScreenAccessor;
import combatant.client.util.screen.ClientScreen;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Hold Shift and drag with the left mouse button to shift-click every slot the cursor
 * passes over. Each slot is moved at most once per drag so a stack is not bounced back.
 */
@ModuleInfo(
        id = "itemscroller",
        displayName = "ItemScroller",
        aliases = {"MouseTweaks"},
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.itemscroller.description")
public final class ItemScroller extends Module {

    // Inventory anticheats count clicks per tick; a small gap keeps a fast drag under that.
    private final NumberValue<Integer> delayMs = num("delay", 0, 0, 250);

    private final Minecraft mc = Minecraft.getInstance();
    private final Set<Integer> movedThisDrag = new HashSet<>();
    private final ArrayDeque<Integer> pending = new ArrayDeque<>();
    private int dragContainerId = -1;
    private long nextMoveAtMs;

    @Override
    public void onDisable() {
        resetDrag();
    }

    // Runs per frame, not per tick: a quick drag crosses several slots within one tick.
    @Override
    public void onFrame(float tickDelta) {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null
                || !(ClientScreen.current() instanceof AbstractContainerScreen<?> screen)
                || !isDragging()) {
            resetDrag();
            return;
        }

        int containerId = screen.getMenu().containerId;
        if (containerId != dragContainerId) {
            resetDrag();
            dragContainerId = containerId;
        }

        Slot slot = ((AbstractContainerScreenAccessor) screen).combatant$getHoveredSlot();
        // Slots crossed during the delay are queued, not skipped, so a fast drag still moves them all.
        if (slot != null && slot.hasItem() && movedThisDrag.add(slot.index)) pending.addLast(slot.index);

        long now = System.currentTimeMillis();
        if (pending.isEmpty() || now < nextMoveAtMs) return;
        nextMoveAtMs = now + delayMs.get();
        mc.gameMode.handleContainerInput(containerId, pending.pollFirst(), 0, ContainerInput.QUICK_MOVE, player);
    }

    private boolean isDragging() {
        var window = mc.getWindow();
        boolean shift = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        return shift && GLFW.glfwGetMouseButton(window.handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private void resetDrag() {
        movedThisDrag.clear();
        pending.clear();
        dragContainerId = -1;
    }
}
