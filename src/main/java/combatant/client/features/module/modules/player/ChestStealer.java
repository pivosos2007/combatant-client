/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.ItemIdSetValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.player.inventory.ContainerKinds;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(
        id = "cheststealer",
        displayName = "ChestStealer",
        aliases = {"Looter", "ContainerStealer"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.cheststealer.description")
public final class ChestStealer extends Module {

    private static final long TICK_MS = 50L;

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> delayMs =
            num("chestStealerDelayMs", "delay_ms", 50, 0, 500);
    private final BooleanValue randomDelay =
            bool("chestStealerRandomDelay", "random_delay", false);
    private final BooleanValue autoClose =
            bool("chestStealerAutoClose", "auto_close", true);
    private final BooleanValue ignoreEnderChest =
            bool("chestStealerIgnoreEnderChest", "ignore_ender_chest", true);
    private final EnumValue<Order> order =
            enumSetting("chestStealerOrder", "order", Order.SLOT, Order.values());
    private final EnumValue<FilterMode> filterMode =
            enumSetting("chestStealerFilterMode", "filter_mode", FilterMode.ALL, FilterMode.values());
    private final ItemIdSetValue filterItems = visibleWhen(
            itemList("chestStealerFilterItems", "filter_items", TextListSetting.PickerMode.ITEMS),
            () -> filterMode.get() != FilterMode.ALL);

    private long tickCounter;
    private long nextActionTick;
    private boolean pendingAction;
    private boolean closed;
    private int activeContainerId = -1;
    private long generation;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        tickCounter++;

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            resetState();
            return;
        }

        Screen screen = ClientScreen.current();
        AbstractContainerMenu menu = player.containerMenu;
        if (!(screen instanceof AbstractContainerScreen<?> handled) || menu == null
                || handled.getMenu() != menu || !ContainerKinds.isLootable(menu)) {
            resetState();
            return;
        }

        if (ignoreEnderChest.get() && ContainerKinds.isEnderChest(menu, screen.getTitle())) return;

        if (menu.containerId != activeContainerId) {
            generation++;
            activeContainerId = menu.containerId;
            pendingAction = false;
            closed = false;
            scheduleNextAction();
            return;
        }

        if (pendingAction || closed || tickCounter < nextActionTick) return;

        int playerStart = InventorySwap.playerInventoryStart(menu);
        if (playerStart <= 0 || playerStart > menu.slots.size()) return;

        int slotId = pickSlot(player, menu, playerStart);
        if (slotId >= 0) {
            queueQuickMove(menu.containerId, slotId);
            return;
        }

        if (autoClose.get()) queueClose(menu.containerId);
    }

    /** The first (or, in best-first order, the most valuable) container slot whose stack passes the filter and fits. */
    private int pickSlot(LocalPlayer player, AbstractContainerMenu menu, int containerSlots) {
        int best = -1;
        int bestValue = Integer.MIN_VALUE;
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = menu.slots.get(i);
            if (slot == null || !slot.hasItem()) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !passesFilter(stack) || !canAccept(player, stack)) continue;
            if (order.get() == Order.SLOT) return i;
            int value = value(stack);
            if (value > bestValue) {
                bestValue = value;
                best = i;
            }
        }
        return best;
    }

    /** An empty list never filters, so switching the mode before picking items does not auto-close every chest. */
    private boolean passesFilter(ItemStack stack) {
        FilterMode mode = filterMode.get();
        if (mode == FilterMode.ALL) return true;
        Set<String> ids = filterItems.get();
        if (ids == null || ids.isEmpty()) return true;
        boolean listed = ids.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        return mode == FilterMode.WHITELIST ? listed : !listed;
    }

    private static int value(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        int value = stack.getRarity().ordinal() * 100;
        if (path.equals("totem_of_undying") || path.equals("elytra") || path.equals("enchanted_golden_apple")) value += 1000;
        else if (path.startsWith("netherite_")) value += 700;
        else if (path.startsWith("diamond_")) value += 500;
        else if (path.endsWith("shulker_box") || path.equals("ender_pearl") || path.equals("golden_apple")) value += 300;
        if (stack.isEnchanted()) value += 150;
        return value;
    }

    private boolean canAccept(LocalPlayer player, ItemStack incoming) {
        if (player == null || incoming == null || incoming.isEmpty()) return false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(stack, incoming)
                    && stack.getCount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private void queueQuickMove(int containerId, int slotId) {
        long expectedGeneration = generation;
        pendingAction = true;
        boolean accepted = InventorySwap.INSTANCE.command(
                null,
                InventoryActionKind.INVENTORY_CLICK,
                () -> {
                    try {
                        if (generation != expectedGeneration) return;
                        LocalPlayer player = mc.player;
                        if (player == null || player.containerMenu == null) return;
                        AbstractContainerMenu menu = player.containerMenu;
                        if (menu.containerId != containerId || slotId < 0 || slotId >= menu.slots.size()) return;
                        Slot slot = menu.slots.get(slotId);
                        if (slot == null || !slot.hasItem() || slot.getItem().isEmpty()) return;
                        InventorySwap.INSTANCE.quickMove(slotId);
                    } finally {
                        if (generation == expectedGeneration) {
                            pendingAction = false;
                            scheduleNextAction();
                        }
                    }
                }
        );
        if (!accepted) pendingAction = false;
    }

    private void queueClose(int containerId) {
        long expectedGeneration = generation;
        pendingAction = true;
        boolean accepted = InventorySwap.INSTANCE.command(
                null,
                InventoryActionKind.INVENTORY_CLOSE,
                () -> {
                    try {
                        if (generation != expectedGeneration) return;
                        LocalPlayer player = mc.player;
                        if (player == null || player.containerMenu == null) return;
                        if (player.containerMenu.containerId != containerId) return;
                        player.closeContainer();
                        ClientScreen.show(null);
                    } finally {
                        if (generation == expectedGeneration) {
                            pendingAction = false;
                            closed = true;
                        }
                    }
                }
        );
        if (!accepted) pendingAction = false;
    }

    /** Delays are counted in client ticks (50 ms each), rounded up, so every click lands on a tick boundary. */
    private void scheduleNextAction() {
        int delay = delayMs.get();
        if (randomDelay.get() && delay > 0) {
            int spread = Math.max(5, Math.min(40, delay / 3));
            delay = Math.max(0, delay + ThreadLocalRandom.current().nextInt(-spread, spread + 1));
        }
        nextActionTick = tickCounter + (delay + TICK_MS - 1) / TICK_MS;
    }

    private void resetState() {
        generation++;
        nextActionTick = 0L;
        pendingAction = false;
        closed = false;
        activeContainerId = -1;
    }

    public enum FilterMode {
        ALL,
        WHITELIST,
        BLACKLIST
    }

    public enum Order {
        SLOT,
        BEST_FIRST
    }
}
