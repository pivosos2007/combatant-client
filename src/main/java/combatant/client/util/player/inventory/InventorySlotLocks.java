/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.player.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

import java.util.IdentityHashMap;
import java.util.Map;

/** Transient, module-owned player-inventory slot reservations (0..40). */
public final class InventorySlotLocks {
    public static final int OFFHAND = 40;
    private static final Map<Object, Reservation> RESERVATIONS = new IdentityHashMap<>();
    private static Player activePlayer;

    private record Reservation(int slot, String reason) {}

    private InventorySlotLocks() {}

    private static void validateSession() {
        Player player = Minecraft.getInstance().player;
        if (player != activePlayer) {
            RESERVATIONS.clear();
            activePlayer = player;
        }
    }

    public static void claim(Object owner, int slot, String reason) {
        if (owner == null || slot < 0 || slot > OFFHAND) return;
        validateSession();
        if (activePlayer != null) RESERVATIONS.put(owner, new Reservation(slot, reason));
    }

    public static void release(Object owner) {
        if (owner == null) return;
        validateSession();
        RESERVATIONS.remove(owner);
    }

    public static boolean isLocked(int slot) {
        return reason(slot) != null;
    }

    public static String reason(int slot) {
        validateSession();
        if (activePlayer == null) return null;
        for (Reservation reservation : RESERVATIONS.values()) {
            if (reservation.slot == slot) return reservation.reason;
        }
        return null;
    }

    public static boolean blocks(int slot, Object actor) {
        validateSession();
        if (activePlayer == null) return false;
        for (Map.Entry<Object, Reservation> entry : RESERVATIONS.entrySet()) {
            if (entry.getValue().slot == slot && entry.getKey() != actor) return true;
        }
        return false;
    }

    public static boolean isLocked(Slot slot, Player player) {
        int index = playerSlotIndex(slot, player);
        return index >= 0 && isLocked(index);
    }

    public static int playerSlotIndex(Slot slot, Player player) {
        if (slot == null || player == null || slot.container != player.getInventory()) return -1;
        int index = slot.getContainerSlot();
        return index >= 0 && index <= OFFHAND ? index : -1;
    }

    /** Slot ids in a menu are NOT indices in Player.getInventory().
     * In the vanilla inventory menu slot 40 is hotbar index 4; offhand is menu slot 45.
     */
    public static int playerSlotIndex(AbstractContainerMenu menu, int menuSlot, Player player) {
        if (menu == null || player == null || menuSlot < 0 || menuSlot >= menu.slots.size()) return -1;
        if (menu == player.inventoryMenu && menuSlot == InventoryMenu.SHIELD_SLOT) return OFFHAND;
        return playerSlotIndex(menu.slots.get(menuSlot), player);
    }

    /** Check both the clicked slot and the other endpoint of a number-key/F swap. */
    public static boolean blocks(AbstractContainerMenu menu, int menuSlot, int button,
                                 ContainerInput input, Player player, Object actor) {
        if (menu == null || player == null || input == null) return false;
        int source = playerSlotIndex(menu, menuSlot, player);
        if (source >= 0 && blocks(source, actor)) return true;
        if (input == ContainerInput.SWAP) {
            if (button == OFFHAND) return blocks(OFFHAND, actor);
            if (button >= 0 && button < 9) return blocks(button, actor);
        }
        if (input == ContainerInput.QUICK_CRAFT || input == ContainerInput.PICKUP_ALL) {
            // Quick-craft and collect-all can move items from a locked slot that was not clicked.
            for (int slot = 0; slot <= OFFHAND; slot++) if (blocks(slot, actor)) return true;
        }
        return false;
    }

    /** Render only for an identified slot, never by comparing ItemStack identities. */
    public static void renderLockOverlay(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(RenderPipelines.GUI, x, y, x + 16, y + 16, 0x714D2BB1);
        graphics.fill(RenderPipelines.GUI, x, y, x + 16, y + 1, 0xCABB8CFF);
    }
}
