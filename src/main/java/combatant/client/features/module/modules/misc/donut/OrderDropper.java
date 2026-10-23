/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.donut;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ItemIdSetValue;
import combatant.client.config.values.NumberValue;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Works through listed items either by dropping whole stacks (from the inventory, or from an open chest) or, with
 * "fulfill orders" on, by opening DonutSMP's {@code /order} menu, picking an order for a listed item that pays at
 * least the configured per-item price, and shift-moving the listed items into its delivery menu. One action per
 * configurable delay.
 */
@ModuleInfo(
        id = "orderdropper",
        displayName = "OrderDropper",
        aliases = {"OrderFulfill", "DonutDropper"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.orderdropper.description")
public final class OrderDropper extends Module {

    private static final int NAV_ROW = 9;
    private static final int MAIN_SLOTS = 36;

    private final Minecraft mc = Minecraft.getInstance();

    private final ItemIdSetValue items = itemList("orderDropperItems", "items", TextListSetting.PickerMode.ITEMS);
    private final NumberValue<Integer> actionDelay = num("orderDropperDelay", "action_delay_ticks", 4, 1, 100);
    private final BooleanValue fulfill = bool("orderDropperFulfill", "fulfill_orders", false);
    private final StringValue minUnitPrice = text("orderDropperMinPrice", "min_unit_price", "");
    private final NumberValue<Integer> orderCooldown =
            num("orderDropperOrderCooldown", "order_cooldown_ticks", 100, 20, 1200);
    private final BooleanValue fromChest = bool("orderDropperFromChest", "drop_from_chest", false);

    private long ticks;
    private long nextActionTick;
    private long nextOrderTick;
    private int handled;
    private double minPrice;

    @Override
    public void onEnable() {
        ticks = 0;
        nextActionTick = 0;
        nextOrderTick = 0;
        handled = 0;
        minPrice = minUnitPrice.get().isBlank() ? 0.0 : DonutAuctionParser.parseMoney(minUnitPrice.get());
        if (items.get().isEmpty()) {
            Notifier.error(I18n.get("notification.orderdropper.no_items"));
            setEnabled(false);
        } else if (minPrice < 0.0) {
            Notifier.error(I18n.get("notification.orderdropper.bad_price"));
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        if (handled > 0) Notifier.info(I18n.get("notification.orderdropper.summary", handled));
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
        if (ticks < nextActionTick) return;

        if (!DonutSmp.isMenuOpen(mc)) {
            if (ClientScreen.current(mc) != null) return;
            if (fulfill.get()) {
                if (ticks >= nextOrderTick && hasListed(player.getInventory())) {
                    ServerChat.command("order");
                    nextOrderTick = ticks + orderCooldown.get();
                }
            } else {
                dropFrom(player.inventoryMenu, player);
            }
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        switch (DonutAuctionParser.menuOf(DonutSmp.menuTitle(mc))) {
            case ORDERS -> {
                if (fulfill.get()) pickOrder(player, menu);
            }
            case ORDER_DELIVER -> {
                if (fulfill.get()) deliver(player, menu);
            }
            case OTHER -> {
                if (!fulfill.get() && fromChest.get()) dropFrom(menu, player);
            }
            default -> {
            }
        }
    }

    private void pickOrder(LocalPlayer player, AbstractContainerMenu menu) {
        int menuSlots = 0;
        for (Slot slot : menu.slots) {
            if (DonutSmp.isMenuSlot(slot)) menuSlots++;
        }
        for (Slot slot : menu.slots) {
            if (!DonutSmp.isMenuSlot(slot) || slot.index >= menuSlots - NAV_ROW || !slot.hasItem()) continue;
            ItemStack stack = slot.getItem();
            if (!items.get().contains(DonutAuctionParser.itemId(stack)) || !holds(player.getInventory(), stack)) continue;
            DonutAuctionParser.Listing order = DonutAuctionParser.parse(stack);
            if (order == null || !order.priced() || order.unitPrice() < minPrice) continue;
            DonutSmp.click(mc, slot.index);
            nextActionTick = ticks + actionDelay.get();
            return;
        }
        // Nothing worth delivering right now; close so the open timer retries later.
        player.closeContainer();
        nextOrderTick = ticks + orderCooldown.get();
    }

    private void deliver(LocalPlayer player, AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory && slot.getContainerSlot() < MAIN_SLOTS
                    && slot.hasItem() && matches(slot.getItem())) {
                handled += slot.getItem().getCount();
                DonutSmp.click(mc, slot.index, 0, ContainerInput.QUICK_MOVE);
                nextActionTick = ticks + actionDelay.get();
                return;
            }
        }
        int confirm = DonutSmp.findMenuSlotByName(menu, "confirm");
        if (confirm >= 0) {
            DonutSmp.click(mc, confirm);
            nextActionTick = ticks + actionDelay.get();
            return;
        }
        player.closeContainer();
        nextOrderTick = ticks + orderCooldown.get();
    }

    /** Throws the first listed whole stack found in the given menu's inventory (or chest) slots. */
    private void dropFrom(AbstractContainerMenu menu, LocalPlayer player) {
        boolean chest = menu != player.inventoryMenu;
        for (Slot slot : menu.slots) {
            boolean eligible = chest
                    ? DonutSmp.isMenuSlot(slot)
                    : slot.container instanceof Inventory && slot.getContainerSlot() < MAIN_SLOTS;
            if (!eligible || !slot.hasItem() || !matches(slot.getItem())) continue;
            handled += slot.getItem().getCount();
            mc.gameMode.handleContainerInput(menu.containerId, slot.index, 1, ContainerInput.THROW, player);
            nextActionTick = ticks + actionDelay.get();
            return;
        }
    }

    private boolean matches(ItemStack stack) {
        return !stack.isEmpty() && items.get().contains(DonutAuctionParser.itemId(stack));
    }

    private boolean hasListed(Inventory inventory) {
        for (int i = 0; i < MAIN_SLOTS; i++) {
            if (matches(inventory.getItem(i))) return true;
        }
        return false;
    }

    /** True when the inventory holds the same item as the order shows. */
    private static boolean holds(Inventory inventory, ItemStack order) {
        for (int i = 0; i < MAIN_SLOTS; i++) {
            ItemStack have = inventory.getItem(i);
            if (!have.isEmpty() && have.getItem() == order.getItem()) return true;
        }
        return false;
    }
}
