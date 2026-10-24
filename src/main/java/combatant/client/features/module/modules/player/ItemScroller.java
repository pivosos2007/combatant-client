/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.input.CombatantInput;
import combatant.client.input.InputDevice;
import combatant.client.input.InputKeys;
import combatant.client.input.InputState;
import combatant.client.input.event.ScrollInputEvent;
import combatant.client.mixins.accessors.AbstractContainerScreenAccessor;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Inventory conveniences for open container screens:
 * <ul>
 *   <li>hold Shift and drag with the left mouse button to shift-click every slot the cursor passes over; each slot
 *       is moved at most once per drag so a stack is not bounced back;</li>
 *   <li>mouse wheel over a slot moves a single item: scroll down pushes one item from the hovered slot to the other
 *       side, scroll up pulls one matching item into the hovered slot.</li>
 * </ul>
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
    private final BooleanValue wheelTransfer = bool("itemScrollerWheel", "wheel_transfer", true);
    private final BooleanValue invertWheel = visibleWhen(
            bool("itemScrollerInvertWheel", "invert_wheel", false), wheelTransfer::get);

    private final Minecraft mc = Minecraft.getInstance();
    private final Set<Integer> movedThisDrag = new HashSet<>();
    private final ArrayDeque<Integer> pending = new ArrayDeque<>();
    private int dragContainerId = -1;
    private long nextMoveAtMs;

    @Override
    public void onDisable() {
        resetDrag();
    }

    @EventHandler
    private void onScroll(ScrollInputEvent event) {
        if (!isEnabled() || !wheelTransfer.get()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null
                || !(ClientScreen.current() instanceof AbstractContainerScreen<?> screen)) return;
        AbstractContainerMenu menu = screen.getMenu();
        if (!menu.getCarried().isEmpty()) return;
        Slot hovered = ((AbstractContainerScreenAccessor) screen).combatant$getHoveredSlot();
        if (hovered == null || event.vertical() == 0.0) return;
        // Swallow the wheel over a slot even when throttled, so the screen does not scroll underneath.
        event.cancel();

        long now = System.currentTimeMillis();
        if (now < nextMoveAtMs) return;

        boolean push = (event.vertical() < 0.0) != invertWheel.get();
        boolean done = push ? pushOne(player, menu, hovered) : pullOne(player, menu, hovered);
        if (done) nextMoveAtMs = now + delayMs.get();
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

    // Takes the whole hovered stack, drops one item into the first accepting slot on the other side, puts the rest back.
    private boolean pushOne(LocalPlayer player, AbstractContainerMenu menu, Slot source) {
        ItemStack stack = source.getItem();
        if (stack.isEmpty() || !source.mayPickup(player)) return false;
        int sourceSection = section(player, menu, source);
        if (sourceSection < 0) return false;
        for (Slot target : menu.slots) {
            if (target == source || section(player, menu, target) == sourceSection || section(player, menu, target) < 0) continue;
            if (!accepts(target, stack)) continue;
            moveOne(player, menu, source, target);
            return true;
        }
        return false;
    }

    // Pulls one item of the hovered slot's type; an empty hovered slot cannot be pulled into (type unknown).
    private boolean pullOne(LocalPlayer player, AbstractContainerMenu menu, Slot target) {
        ItemStack wanted = target.getItem();
        if (wanted.isEmpty() || !accepts(target, wanted)) return false;
        int targetSection = section(player, menu, target);
        if (targetSection < 0) return false;
        for (Slot source : menu.slots) {
            if (source == target || section(player, menu, source) == targetSection || section(player, menu, source) < 0) continue;
            ItemStack stack = source.getItem();
            if (stack.isEmpty() || !source.mayPickup(player) || !ItemStack.isSameItemSameComponents(stack, wanted)) continue;
            moveOne(player, menu, source, target);
            return true;
        }
        return false;
    }

    private void moveOne(LocalPlayer player, AbstractContainerMenu menu, Slot source, Slot target) {
        int id = menu.containerId;
        mc.gameMode.handleContainerInput(id, source.index, 0, ContainerInput.PICKUP, player);
        mc.gameMode.handleContainerInput(id, target.index, 1, ContainerInput.PICKUP, player);
        mc.gameMode.handleContainerInput(id, source.index, 0, ContainerInput.PICKUP, player);
    }

    private static boolean accepts(Slot slot, ItemStack stack) {
        if (!slot.mayPlace(stack)) return false;
        ItemStack present = slot.getItem();
        if (present.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(present, stack)
                && present.getCount() < Math.min(present.getMaxStackSize(), slot.getMaxStackSize(present));
    }

    // 0 = container, 1 = hotbar, 2 = main inventory, -1 = armor/offhand/crafting (never a transfer target).
    private static int section(LocalPlayer player, AbstractContainerMenu menu, Slot slot) {
        if (slot.container != player.getInventory()) return menu == player.inventoryMenu ? -1 : 0;
        int index = slot.getContainerSlot();
        if (index < 9) return 1;
        return index < 36 ? 2 : -1;
    }

    private boolean isDragging() {
        InputState state = CombatantInput.state();
        boolean shift = state.isPressed(InputDevice.KEYBOARD, InputKeys.LEFT_SHIFT)
                || state.isPressed(InputDevice.KEYBOARD, InputKeys.RIGHT_SHIFT);
        return shift && state.isPressed(InputDevice.MOUSE, InputKeys.MOUSE_LEFT);
    }

    private void resetDrag() {
        movedThisDrag.clear();
        pending.clear();
        dragContainerId = -1;
    }
}
