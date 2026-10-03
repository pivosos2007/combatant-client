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
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
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

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> delayMs =
            num("chestStealerDelayMs", "delay_ms", 50, 0, 500);
    private final BooleanValue randomDelay =
            bool("chestStealerRandomDelay", "random_delay", false);
    private final BooleanValue autoClose =
            bool("chestStealerAutoClose", "auto_close", true);
    private final BooleanValue ignoreEnderChest =
            bool("chestStealerIgnoreEnderChest", "ignore_ender_chest", true);
    private final EnumValue<FilterMode> filterMode =
            enumSetting("chestStealerFilterMode", "filter_mode", FilterMode.ALL, FilterMode.values());
    private final ItemIdSetValue filterItems = visibleWhen(
            itemList("chestStealerFilterItems", "filter_items", TextListSetting.PickerMode.ITEMS),
            () -> filterMode.get() != FilterMode.ALL);

    private long nextActionAtNs;
    private boolean pendingAction;
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

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            resetState();
            return;
        }

        Screen screen = ClientScreen.current();
        if (!(screen instanceof AbstractContainerScreen<?>) || screen instanceof InventoryScreen) {
            resetState();
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            resetState();
            return;
        }

        if (ignoreEnderChest.get() && isEnderChest(screen)) return;

        if (menu.containerId != activeContainerId) {
            generation++;
            activeContainerId = menu.containerId;
            pendingAction = false;
            scheduleNextAction();
            return;
        }

        if (pendingAction || System.nanoTime() < nextActionAtNs) return;

        int playerStart = InventorySwap.playerInventoryStart(menu);
        if (playerStart <= 0 || playerStart > menu.slots.size()) return;

        int slotId = firstLootableSlot(menu, playerStart);
        if (slotId >= 0) {
            Slot slot = menu.slots.get(slotId);
            if (slot != null && canAccept(player, slot.getItem())) {
                queueQuickMove(menu.containerId, slotId);
                return;
            }
        }

        if (autoClose.get()) queueClose(menu.containerId);
    }

    private int firstLootableSlot(AbstractContainerMenu menu, int containerSlots) {
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = menu.slots.get(i);
            if (slot != null && slot.hasItem() && !slot.getItem().isEmpty() && passesFilter(slot.getItem())) return i;
        }
        return -1;
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
                InventoryActionKind.INVENTORY_CLICK,
                InventorySwapPolicy.LEGIT,
                () -> {
                    try {
                        if (generation != expectedGeneration) return;
                        LocalPlayer player = mc.player;
                        if (player == null || mc.gameMode == null || player.containerMenu == null) return;
                        AbstractContainerMenu menu = player.containerMenu;
                        if (menu.containerId != containerId || slotId < 0 || slotId >= menu.slots.size()) return;
                        Slot slot = menu.slots.get(slotId);
                        if (slot == null || !slot.hasItem() || slot.getItem().isEmpty()) return;
                        mc.gameMode.handleContainerInput(containerId, slotId, 0, ContainerInput.QUICK_MOVE, player);
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
                InventoryActionKind.INVENTORY_CLOSE,
                InventorySwapPolicy.LEGIT,
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
                            nextActionAtNs = Long.MAX_VALUE;
                        }
                    }
                }
        );
        if (!accepted) pendingAction = false;
    }

    private void scheduleNextAction() {
        int delay = delayMs.get();
        if (randomDelay.get() && delay > 0) {
            int spread = Math.max(5, Math.min(40, delay / 3));
            delay = Math.max(0, delay + ThreadLocalRandom.current().nextInt(-spread, spread + 1));
        }
        nextActionAtNs = System.nanoTime() + delay * 1_000_000L;
    }

    private boolean isEnderChest(Screen screen) {
        if (screen == null || screen.getTitle() == null) return false;
        return screen.getTitle().getString().equalsIgnoreCase(I18n.get("container.enderchest"));
    }

    private void resetState() {
        generation++;
        nextActionAtNs = 0L;
        pendingAction = false;
        activeContainerId = -1;
    }

    public enum FilterMode {
        ALL,
        WHITELIST,
        BLACKLIST
    }
}
