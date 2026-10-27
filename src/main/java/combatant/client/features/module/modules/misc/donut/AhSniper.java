/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.donut;

import combatant.client.config.values.ColorListValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBColorValue;
import combatant.client.config.values.StringValue;
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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Set;

/**
 * Watches DonutSMP's {@code /ah} menu and buys listed items whose per-item price is under a limit. It opens the menu,
 * scans the listing slots, clicks the cheapest matching entry, confirms the purchase and presses the menu's refresh
 * button between scans. Each click is spaced by a configurable delay.
 */
@ModuleInfo(
        id = "ahsniper",
        displayName = "AH Sniper",
        aliases = {"AuctionSniper", "AhBuyer"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.ahsniper.description")
public final class AhSniper extends Module {

    /** Bottom row of a double chest holds navigation/refresh buttons, not listings. */
    private static final int NAV_ROW = 9;
    private static final int REOPEN_TICKS = 40;

    private final Minecraft mc = Minecraft.getInstance();

    private final RGBColorValue defaultColor = colorNoAlpha("ahSniperDefaultColor", "default_color", "#55FF55");
    private final ColorListValue items = colorList(
            "ahSniperItems", "items", TextListSetting.PickerMode.ITEMS, Set.of(), defaultColor);
    private final StringValue maxPricePerItem = text("ahSniperMaxPrice", "max_price_per_item", "");
    private final StringValue maxTotalSpend = text("ahSniperMaxSpend", "max_total_spend", "");
    private final NumberValue<Integer> clickDelay = num("ahSniperClickDelay", "click_delay_ticks", 3, 1, 40);
    private final NumberValue<Integer> refreshDelay = num("ahSniperRefreshDelay", "refresh_delay_ticks", 8, 2, 100);
    private final NumberValue<Integer> maxBuys = num("ahSniperMaxBuys", "max_buys", 0, 0, 500);

    private long ticks;
    private long nextActionTick;
    private long nextOpenTick;
    private int buys;
    private double spent;
    private double pendingPrice;
    private double maxPrice;
    private double spendLimit;

    @Override
    public void onEnable() {
        ticks = 0;
        nextActionTick = 0;
        nextOpenTick = 0;
        buys = 0;
        spent = 0;
        pendingPrice = 0;
        maxPrice = DonutAuctionParser.parseMoney(maxPricePerItem.get());
        spendLimit = maxTotalSpend.get().isBlank() ? -1.0 : DonutAuctionParser.parseMoney(maxTotalSpend.get());
        if (maxPrice <= 0.0) {
            Notifier.error(I18n.get("notification.ahsniper.no_price"));
            setEnabled(false);
        } else if (items.get().isEmpty()) {
            Notifier.error(I18n.get("notification.ahsniper.no_items"));
            setEnabled(false);
        } else if (!maxTotalSpend.get().isBlank() && spendLimit <= 0.0) {
            Notifier.error(I18n.get("notification.ahsniper.bad_spend"));
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        LocalPlayer player = mc.player;
        if (player != null && DonutSmp.isMenuOpen(mc)) player.closeContainer();
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
            if (ticks >= nextOpenTick && ClientScreen.current(mc) == null) {
                ServerChat.command("ah");
                nextOpenTick = ticks + REOPEN_TICKS;
            }
            return;
        }
        if (ticks < nextActionTick) return;

        AbstractContainerMenu menu = player.containerMenu;
        switch (DonutAuctionParser.menuOf(DonutSmp.menuTitle(mc))) {
            case AUCTION -> scan(player, menu);
            case AUCTION_CONFIRM -> confirm(menu);
            default -> {
            }
        }
    }

    private void scan(LocalPlayer player, AbstractContainerMenu menu) {
        int menuSlots = 0;
        for (Slot slot : menu.slots) {
            if (DonutSmp.isMenuSlot(slot)) menuSlots++;
        }
        int listingEnd = menuSlots - NAV_ROW;
        String self = player.getName().getString();

        int bestSlot = -1;
        double bestUnit = Double.MAX_VALUE;
        for (Slot slot : menu.slots) {
            if (!DonutSmp.isMenuSlot(slot) || slot.index >= listingEnd || !slot.hasItem()) continue;
            ItemStack stack = slot.getItem();
            if (!items.get().contains(DonutAuctionParser.itemId(stack))) continue;
            DonutAuctionParser.Listing listing = DonutAuctionParser.parse(stack);
            if (listing == null || !listing.priced() || listing.unitPrice() > maxPrice) continue;
            if (!listing.seller().isEmpty() && listing.seller().equalsIgnoreCase(self)) continue;
            if (spendLimit > 0.0 && spent + listing.totalPrice() > spendLimit) continue;
            if (listing.unitPrice() < bestUnit) {
                bestUnit = listing.unitPrice();
                bestSlot = slot.index;
                pendingPrice = listing.totalPrice();
            }
        }

        if (bestSlot >= 0) {
            DonutSmp.click(mc, bestSlot);
            nextActionTick = ticks + clickDelay.get();
            return;
        }

        int refresh = DonutSmp.findMenuSlotByName(menu, "refresh");
        if (refresh >= 0) {
            DonutSmp.click(mc, refresh);
            nextActionTick = ticks + refreshDelay.get();
        } else {
            // No refresh button: close and let the open timer reissue /ah.
            player.closeContainer();
            nextOpenTick = ticks + refreshDelay.get();
        }
    }

    private void confirm(AbstractContainerMenu menu) {
        int slot = DonutSmp.findMenuSlotByName(menu, "confirm");
        if (slot < 0) {
            for (Slot s : menu.slots) {
                if (DonutSmp.isMenuSlot(s) && s.hasItem()
                        && DonutAuctionParser.itemId(s.getItem()).contains("lime")) {
                    slot = s.index;
                    break;
                }
            }
        }
        if (slot < 0) {
            nextActionTick = ticks + clickDelay.get();
            return;
        }
        DonutSmp.click(mc, slot);
        buys++;
        spent += pendingPrice;
        nextActionTick = ticks + clickDelay.get();
        Notifier.success(I18n.get("notification.ahsniper.bought", format(pendingPrice), buys));
        pendingPrice = 0;

        int limit = maxBuys.get();
        boolean spendDone = spendLimit > 0.0 && spent >= spendLimit;
        if (limit > 0 && buys >= limit || spendDone) {
            Notifier.info(I18n.get("notification.ahsniper.done", buys, format(spent)));
            setEnabled(false);
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%,.0f", value);
    }
}
