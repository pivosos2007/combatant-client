/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.donut;

import combatant.client.config.values.ItemIdSetValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.server.DonutAuctionParser;
import combatant.client.util.server.DonutSmp;
import combatant.client.util.text.ServerChat;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

/**
 * Opens DonutSMP's {@code /sell} menu whenever the inventory holds a listed item, shift-moves the listed items into
 * it one click at a time and presses the confirm button. Both the click rate and the {@code /sell} rate are limited.
 */
@ModuleInfo(
        id = "autosell",
        displayName = "AutoSell",
        aliases = {"DonutSell"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.autosell.description")
public final class AutoSell extends Module {

    /** Hotbar plus main inventory; armor and offhand are never sold. */
    private static final int MAIN_SLOTS = 36;

    private final Minecraft mc = Minecraft.getInstance();

    private final ItemIdSetValue items = itemList("autoSellItems", "items", TextListSetting.PickerMode.ITEMS);
    private final NumberValue<Integer> moveDelay = num("autoSellMoveDelay", "move_delay_ticks", 2, 1, 40);
    private final NumberValue<Integer> confirmDelay = num("autoSellConfirmDelay", "confirm_delay_ticks", 6, 2, 100);
    private final NumberValue<Integer> commandCooldown =
            num("autoSellCommandCooldown", "command_cooldown_ticks", 80, 20, 1200);

    private long ticks;
    private long nextActionTick;
    private long nextCommandTick;
    private boolean awaitingMenu;
    private int movedItems;
    private int soldItems;

    @Override
    public void onEnable() {
        ticks = 0;
        nextActionTick = 0;
        nextCommandTick = 0;
        awaitingMenu = false;
        movedItems = 0;
        soldItems = 0;
        if (items.get().isEmpty()) {
            Notifier.error(I18n.get("notification.autosell.no_items"));
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        LocalPlayer player = mc.player;
        if (player != null && awaitingMenu && DonutSmp.isMenuOpen(mc)) player.closeContainer();
        if (soldItems > 0) Notifier.info(I18n.get("notification.autosell.summary", soldItems));
        awaitingMenu = false;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            setEnabled(false);
            return;
        }
        ticks++;

        if (!DonutSmp.isMenuOpen(mc)) {
            awaitingMenu = false;
            if (ticks >= nextCommandTick && ClientScreen.current(mc) == null && hasSellable(player.getInventory())) {
                ServerChat.command("sell");
                nextCommandTick = ticks + commandCooldown.get();
                awaitingMenu = true;
            }
            return;
        }
        DonutAuctionParser.Menu kind = DonutAuctionParser.menuOf(DonutSmp.menuTitle(mc));
        if (kind != DonutAuctionParser.Menu.SELL && !(awaitingMenu && kind == DonutAuctionParser.Menu.OTHER)) return;
        if (ticks < nextActionTick) return;

        AbstractContainerMenu menu = player.containerMenu;
        Slot sellable = firstSellable(menu);
        if (sellable != null) {
            DonutSmp.click(mc, sellable.index, 0, ContainerInput.QUICK_MOVE);
            movedItems += sellable.getItem().getCount();
            nextActionTick = ticks + moveDelay.get();
            return;
        }

        if (movedItems > 0) {
            int confirm = DonutSmp.findMenuSlotByName(menu, "confirm");
            if (confirm < 0) confirm = lastMenuSlot(menu);
            if (confirm >= 0) DonutSmp.click(mc, confirm);
            soldItems += movedItems;
            movedItems = 0;
            nextActionTick = ticks + confirmDelay.get();
            return;
        }
        player.closeContainer();
        awaitingMenu = false;
    }

    private boolean hasSellable(Inventory inventory) {
        for (int i = 0; i < MAIN_SLOTS; i++) {
            if (matches(inventory.getItem(i))) return true;
        }
        return false;
    }

    private boolean matches(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && items.get().contains(DonutAuctionParser.itemId(stack));
    }

    private Slot firstSellable(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory && slot.getContainerSlot() < MAIN_SLOTS
                    && slot.hasItem() && matches(slot.getItem())) {
                return slot;
            }
        }
        return null;
    }

    private static int lastMenuSlot(AbstractContainerMenu menu) {
        int last = -1;
        for (Slot slot : menu.slots) {
            if (DonutSmp.isMenuSlot(slot)) last = slot.index;
        }
        return last;
    }
}
