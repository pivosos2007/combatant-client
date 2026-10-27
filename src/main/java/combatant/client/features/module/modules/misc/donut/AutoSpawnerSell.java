/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.donut;

import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.server.DonutAuctionParser;
import combatant.client.util.text.ServerChat;
import net.minecraft.network.chat.TextColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * Empties the spawner next to you on a timer and sells the loot to the best /order. Each cycle: right-click the
 * spawner, throw the full storage pages out (switching pages on a delay), then run {@code /order <item>}, pick the
 * order that pays the most per item and shift-move your inventory into it. The slot layout is DonutSMP's spawner
 * and order GUI; every click goes through the normal container-click path, one action per delay.
 */
@ModuleInfo(
        id = "autospawnersell",
        displayName = "AutoSpawnerSell",
        aliases = {"SpawnerSell", "BoneSeller", "SpawnerOrder", "SpawnerCycle", "SpawnerLoot"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.autospawnersell.description")
public final class AutoSpawnerSell extends Module {

    /** ORDER delivers loot to the best /order; SELL dumps bones and arrows into /sell. */
    private enum SellMode { ORDER, SELL }

    // DonutSMP spawner menu layout.
    private static final int SPAWNER_STORAGE_BUTTON = 11;
    private static final int SPAWNER_MAIN_MARKER = 13;
    private static final int SPAWNER_CONFIRM_MARKER = 15;
    private static final int STORAGE_PAGE_SLOTS = 45;
    private static final int STORAGE_SORT_ARROW = 48;
    private static final int STORAGE_NEXT_PAGE = 50;
    private static final int STORAGE_DROP_PAGE = 52;
    private static final int STORAGE_MARKER = 53;
    private static final int STORAGE_DROP_ALL = 45;
    // DonutSMP /order layout.
    private static final int ORDER_ROWS = 6;
    private static final int ORDER_SORT_SLOT = 47;
    private static final int ORDER_LIST_SLOTS = 44;
    private static final int ORDER_CONFIRM_SLOT = 15;
    private static final int TICKS_PER_MINUTE = 1200;

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> dropDelayMinutes = num("autoSpawnerSellDropDelay", "drop_delay_minutes", 30, 0, 120);
    private final NumberValue<Integer> pageAmount = num("autoSpawnerSellPages", "page_amount", 2, 1, 10);
    private final NumberValue<Integer> pageSwitchSeconds = num("autoSpawnerSellPageSwitch", "page_switch_seconds", 4, 0, 60);
    private final NumberValue<Integer> actionDelay = num("autoSpawnerSellDelay", "action_delay_ticks", 2, 1, 20);
    private final NumberValue<Integer> spawnerRange = num("autoSpawnerSellRange", "spawner_range", 5, 1, 6);
    private final EnumValue<SellMode> sellMode = enumSetting("autoSpawnerSellMode", "sell_mode", SellMode.ORDER, SellMode.values());

    private int wait;
    private int pagesDropped;
    private boolean processingPage;
    private boolean selling;
    private boolean sellCommandSent;
    private int cycles;

    @Override
    public void onEnable() {
        wait = 20;
        pagesDropped = 0;
        processingPage = false;
        selling = false;
        cycles = 0;
    }

    @Override
    public void onDisable() {
        LocalPlayer player = mc.player;
        if (player != null && SpawnerGui.chest(player) != null) player.closeContainer();
        if (cycles > 0) Notifier.info(I18n.get("notification.autospawnersell.summary", cycles));
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) return;
        if (wait > 0) {
            wait--;
            return;
        }
        if (pagesDropped >= pageAmount.get() && !selling) beginSelling();
        if (selling) sellStep(player);
        else dropStep(player);
    }

    private void beginSelling() {
        selling = true;
        pagesDropped = 0;
        processingPage = false;
        wait = 40;
        sellCommandSent = false;
    }

    private void dropStep(LocalPlayer player) {
        ChestMenu menu = SpawnerGui.chest(player);
        if (menu == null) {
            BlockPos spawner = SpawnerGui.nearestSpawner(mc, spawnerRange.get());
            if (spawner == null) {
                Notifier.warning(I18n.get("notification.autospawnersell.no_spawner"));
                setEnabled(false);
                return;
            }
            SpawnerGui.open(mc, spawner);
            wait = 20;
            return;
        }
        if (SpawnerGui.slotIs(menu, SPAWNER_CONFIRM_MARKER, Items.STAINED_GLASS_PANE.lime())) {
            SpawnerGui.click(mc, menu, SPAWNER_CONFIRM_MARKER, 1, ContainerInput.QUICK_MOVE);
            wait = 10;
            return;
        }
        if (SpawnerGui.slotIs(menu, SPAWNER_MAIN_MARKER, Items.SKELETON_SKULL)) {
            SpawnerGui.click(mc, menu, SPAWNER_STORAGE_BUTTON, 0, ContainerInput.PICKUP);
            wait = 20;
            return;
        }
        if (!SpawnerGui.slotIs(menu, STORAGE_MARKER, Items.GOLD_INGOT)) {
            player.closeContainer();
            wait = 20;
            return;
        }
        if (SpawnerGui.slotIs(menu, STORAGE_SORT_ARROW, Items.ARROW)) {
            SpawnerGui.click(mc, menu, STORAGE_SORT_ARROW, 0, ContainerInput.PICKUP);
            wait = 20;
            return;
        }
        if (pageIsFullOfBones(menu)) {
            SpawnerGui.click(mc, menu, STORAGE_DROP_PAGE, 1, ContainerInput.THROW);
            processingPage = true;
            pagesDropped++;
            wait = Math.max(actionDelay.get(), pageSwitchSeconds.get() * 20);
        } else if (processingPage) {
            processingPage = false;
            SpawnerGui.click(mc, menu, STORAGE_NEXT_PAGE, 0, ContainerInput.PICKUP);
            wait = 20;
        } else if (pagesDropped > 0) {
            beginSelling();
        } else {
            SpawnerGui.click(mc, menu, STORAGE_DROP_ALL, 1, ContainerInput.THROW);
            wait = Math.max(actionDelay.get(), dropDelayMinutes.get() * TICKS_PER_MINUTE);
            cycles++;
        }
    }

    private static boolean pageIsFullOfBones(ChestMenu menu) {
        for (int slot = 0; slot < STORAGE_PAGE_SLOTS; slot++) {
            if (!menu.getSlot(slot).getItem().is(Items.BONE)) return false;
        }
        return true;
    }

    private void sellCommandStep(LocalPlayer player) {
        ChestMenu menu = SpawnerGui.chest(player);
        if (menu == null) {
            if (sellCommandSent || !hasLoot(player)) {
                finishSelling();
                return;
            }
            ServerChat.command("sell");
            sellCommandSent = true;
            wait = Math.max(20, actionDelay.get());
            return;
        }
        int menuSlots = menu.getRowCount() * 9;
        for (int slot = menuSlots; slot < menu.slots.size(); slot++) {
            if (isLoot(menu.getSlot(slot).getItem())) {
                SpawnerGui.click(mc, menu, slot, 0, ContainerInput.QUICK_MOVE);
                wait = actionDelay.get();
                return;
            }
        }
        for (int slot = 0; slot < menuSlots; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (stack.is(Items.STAINED_GLASS_PANE.lime()) || stack.is(Items.STAINED_GLASS_PANE.green())) {
                SpawnerGui.click(mc, menu, slot, 0, ContainerInput.PICKUP);
                break;
            }
        }
        player.closeContainer();
        finishSelling();
    }

    private static boolean isLoot(ItemStack stack) {
        return stack.is(Items.BONE) || stack.is(Items.ARROW);
    }

    private static boolean hasLoot(LocalPlayer player) {
        for (int i = 0; i < 36; i++) {
            if (isLoot(player.getInventory().getItem(i))) return true;
        }
        return false;
    }

    private void sellStep(LocalPlayer player) {
        if (sellMode.get() == SellMode.SELL) {
            sellCommandStep(player);
            return;
        }
        Item item = SpawnerGui.firstInventoryItem(player);
        ChestMenu menu = SpawnerGui.chest(player);
        if (menu == null) {
            if (item == null) {
                finishSelling();
                return;
            }
            ServerChat.command("order " + (item == Items.BONE ? "Bones" : new ItemStack(item).getHoverName().getString()));
            wait = 20;
            return;
        }
        int rows = menu.getRowCount();
        if (rows == ORDER_ROWS) {
            pickOrder(player, menu, item);
        } else if (rows == 4) {
            deliverItems(player, menu, item);
        } else if (rows == 3) {
            SpawnerGui.click(mc, menu, ORDER_CONFIRM_SLOT, 1, ContainerInput.QUICK_MOVE);
            wait = 10;
            if (item == null) finishSelling();
        }
    }

    private void pickOrder(LocalPlayer player, ChestMenu menu, Item item) {
        if (item == null || menu.getSlot(ORDER_SORT_SLOT).getItem().isEmpty()) {
            wait = 2;
            player.closeContainer();
            return;
        }
        if (sortedByMostMoney(menu.getSlot(ORDER_SORT_SLOT).getItem())) {
            SpawnerGui.click(mc, menu, ORDER_SORT_SLOT, 1, ContainerInput.QUICK_MOVE);
            wait = 5;
            return;
        }
        for (int slot = 0; slot < ORDER_LIST_SLOTS; slot++) {
            if (menu.getSlot(slot).getItem().is(item)) {
                SpawnerGui.click(mc, menu, slot, 1, ContainerInput.QUICK_MOVE);
                wait = 10;
                return;
            }
        }
        wait = 40;
        player.closeContainer();
    }

    private void deliverItems(LocalPlayer player, ChestMenu menu, Item item) {
        int menuSlots = menu.getRowCount() * 9;
        for (int slot = menuSlots; slot < menu.slots.size(); slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (!stack.isEmpty() && (item == null || stack.is(item))) {
                SpawnerGui.click(mc, menu, slot, 1, ContainerInput.QUICK_MOVE);
                wait = actionDelay.get();
                return;
            }
        }
        player.closeContainer();
        wait = 10;
    }

    private void finishSelling() {
        selling = false;
        sellCommandSent = false;
        cycles++;
        LocalPlayer player = mc.player;
        if (player != null && SpawnerGui.chest(player) != null) player.closeContainer();
        wait = Math.max(actionDelay.get(), dropDelayMinutes.get() * TICKS_PER_MINUTE);
    }

    /** The sort button lists its options in lore; the active one is drawn white. */
    private static boolean sortedByMostMoney(ItemStack button) {
        for (Component line : button.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
            String text = DonutAuctionParser.normalize(line.getString());
            if (text.contains("most money per item") && isWhite(line)) return true;
        }
        return false;
    }

    private static boolean isWhite(Component line) {
        var color = line.getStyle().getColor();
        if (color != null && color.getValue() == TextColor.WHITE.getValue()) return true;
        for (Component sibling : line.getSiblings()) {
            if (isWhite(sibling)) return true;
        }
        return false;
    }
}
