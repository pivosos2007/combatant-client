/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.server;

import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public final class DonutSmp {

    private DonutSmp() {
    }

    public static boolean isConnected() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return false;
        ServerData server = mc.getCurrentServer();
        return server != null && isDonutAddress(server.ip);
    }

    public static boolean isDonutAddress(String address) {
        return address != null && address.toLowerCase(Locale.ROOT).contains("donutsmp");
    }

    /** True when a server-side chest-style menu (not the player's own inventory) is open on screen. */
    public static boolean isMenuOpen(Minecraft mc) {
        LocalPlayer player = mc.player;
        return player != null && ClientScreen.current(mc) instanceof AbstractContainerScreen<?>
                && player.containerMenu != player.inventoryMenu;
    }

    /** Title of the open container screen, or an empty string. */
    public static String menuTitle(Minecraft mc) {
        return ClientScreen.current(mc) instanceof AbstractContainerScreen<?> screen ? screen.getTitle().getString() : "";
    }

    /** Slots that belong to the server menu rather than the player inventory. */
    public static boolean isMenuSlot(Slot slot) {
        return !(slot.container instanceof Inventory);
    }

    /** Index of the first menu slot whose item name contains {@code keyword} (case/small-caps insensitive), or -1. */
    public static int findMenuSlotByName(AbstractContainerMenu menu, String keyword) {
        String wanted = keyword.toLowerCase(Locale.ROOT);
        for (Slot slot : menu.slots) {
            if (!isMenuSlot(slot) || !slot.hasItem()) continue;
            ItemStack stack = slot.getItem();
            if (DonutAuctionParser.normalize(stack.getHoverName().getString()).contains(wanted)) return slot.index;
        }
        return -1;
    }

    /** Left-clicks a menu slot as the vanilla container screen would. */
    public static void click(Minecraft mc, int slot) {
        click(mc, slot, 0, ContainerInput.PICKUP);
    }

    public static void click(Minecraft mc, int slot, int button, ContainerInput type) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;
        mc.gameMode.handleContainerInput(player.containerMenu.containerId, slot, button, type, player);
    }
}
