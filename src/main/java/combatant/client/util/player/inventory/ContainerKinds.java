/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;

/** Classifies open container menus by their menu type instead of by window title text. */
public final class ContainerKinds {

    private static final String ENDER_CHEST_TITLE_KEY = "container.enderchest";

    private ContainerKinds() {
    }

    /** Menus that are plain item storage a player may loot: chests, shulker boxes, hoppers, dispensers/droppers. */
    public static boolean isLootable(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu
                || menu instanceof HopperMenu || menu instanceof DispenserMenu;
    }

    /**
     * The 3x9 chest menu the server opens for an ender chest. The client never sees the backing container type, so
     * the menu type is combined with the translatable title key; a renamed chest has a literal title and never
     * matches, independent of the client language.
     */
    public static boolean isEnderChest(AbstractContainerMenu menu, Component title) {
        if (!(menu instanceof ChestMenu chest) || chest.getRowCount() != 3 || title == null) return false;
        return title.getContents() instanceof TranslatableContents translatable
                && ENDER_CHEST_TITLE_KEY.equals(translatable.getKey());
    }
}
