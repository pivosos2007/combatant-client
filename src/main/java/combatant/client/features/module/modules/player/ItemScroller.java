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
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.PacketWindow;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
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

    private static final long TICK_MS = 50L;
    private static final int MAX_QUEUED = 64;

    // Every click goes through the serialized window-click queue, at most one operation per client tick: Grim flags
    // PICKUP and QUICK_MOVE clicks mixed in one tick (PacketOrderA), and clicks while the server sees movement.
    private final NumberValue<Integer> delayMs = num("delay", 50, 0, 250);
    private final BooleanValue wheelTransfer = bool("itemScrollerWheel", "wheel_transfer", true);
    private final BooleanValue invertWheel = visibleWhen(
            bool("itemScrollerInvertWheel", "invert_wheel", false), wheelTransfer::get);

    private final Minecraft mc = Minecraft.getInstance();
    private final Set<Integer> movedThisDrag = new HashSet<>();
    private final ArrayDeque<Op> pending = new ArrayDeque<>();
    private int dragContainerId = -1;
    private long tickCounter;
    private long nextOpTick;

    /** One queued transfer: a shift-click of {@code source} (target < 0) or a one-item move from source to target. */
    private record Op(int containerId, int source, int target) {
    }

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

        // A queued move was planned against the menu as it is now; plan the next one only after it ran.
        if (!pending.isEmpty()) return;

        boolean push = (event.vertical() < 0.0) != invertWheel.get();
        if (push) pushOne(player, menu, hovered);
        else pullOne(player, menu, hovered);
    }

    // Collects hovered slots per frame (a quick drag crosses several slots within one tick); clicks run in onTick.
    @Override
    public void onFrame(float tickDelta) {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null
                || !(ClientScreen.current() instanceof AbstractContainerScreen<?> screen)
                || !isDragging()) {
            movedThisDrag.clear();
            dragContainerId = -1;
            return;
        }

        int containerId = screen.getMenu().containerId;
        if (containerId != dragContainerId) {
            resetDrag();
            dragContainerId = containerId;
        }

        Slot slot = ((AbstractContainerScreenAccessor) screen).combatant$getHoveredSlot();
        // Slots crossed during the delay are queued, not skipped, so a fast drag still moves them all.
        if (slot != null && slot.hasItem() && pending.size() < MAX_QUEUED && movedThisDrag.add(slot.index)) {
            pending.addLast(new Op(containerId, slot.index, -1));
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        tickCounter++;
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null
                || !(ClientScreen.current() instanceof AbstractContainerScreen<?> screen)) {
            pending.clear();
            return;
        }
        Op op = pending.peekFirst();
        if (op == null) return;
        AbstractContainerMenu menu = screen.getMenu();
        if (menu != player.containerMenu || menu.containerId != op.containerId()) {
            pending.clear();
            return;
        }
        if (tickCounter < nextOpTick || !PacketWindow.INSTANCE.stationaryForClick()) return;

        boolean ran = InventorySwap.INSTANCE.command(null, InventoryActionKind.INVENTORY_CLICK, () -> run(op));
        if (!ran) return;
        pending.pollFirst();
        nextOpTick = tickCounter + (delayMs.get() + TICK_MS - 1) / TICK_MS;
    }

    private void run(Op op) {
        InventorySwap swap = InventorySwap.INSTANCE;
        if (op.target() < 0) {
            swap.quickMove(op.source());
            return;
        }
        if (!swap.pickupClick(op.source(), 0)) return;
        swap.pickupClick(op.target(), 1);
        swap.pickupClick(op.source(), 0);
    }

    // Takes the whole hovered stack, drops one item into the first accepting slot on the other side, puts the rest back.
    private void pushOne(LocalPlayer player, AbstractContainerMenu menu, Slot source) {
        ItemStack stack = source.getItem();
        if (stack.isEmpty() || !source.mayPickup(player)) return;
        int sourceSection = section(player, menu, source);
        if (sourceSection < 0) return;
        for (Slot target : menu.slots) {
            if (target == source || section(player, menu, target) == sourceSection || section(player, menu, target) < 0) continue;
            if (!accepts(target, stack)) continue;
            pending.addLast(new Op(menu.containerId, source.index, target.index));
            return;
        }
    }

    // Pulls one item of the hovered slot's type; an empty hovered slot cannot be pulled into (type unknown).
    private void pullOne(LocalPlayer player, AbstractContainerMenu menu, Slot target) {
        ItemStack wanted = target.getItem();
        if (wanted.isEmpty() || !accepts(target, wanted)) return;
        int targetSection = section(player, menu, target);
        if (targetSection < 0) return;
        for (Slot source : menu.slots) {
            if (source == target || section(player, menu, source) == targetSection || section(player, menu, source) < 0) continue;
            ItemStack stack = source.getItem();
            if (stack.isEmpty() || !source.mayPickup(player) || !ItemStack.isSameItemSameComponents(stack, wanted)) continue;
            pending.addLast(new Op(menu.containerId, source.index, target.index));
            return;
        }
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
