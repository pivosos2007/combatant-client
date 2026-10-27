/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ItemIdSetValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.PacketWindow;
import combatant.client.util.screen.ClientScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ModuleInfo(
        id = "shitdropper",
        displayName = "ShitDropper",
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.shitdropper.description")
public class ShitDropper extends Module {

    private static final Minecraft mc = Minecraft.getInstance();

    private static final String SETTING_TRASH_ITEM_IDS = "trash_item_ids";
    private static final String SETTING_MAX_TRASH_SLOTS = "max_trash_slots";
    private static final String SETTING_STACKS_PER_TICK = "stacks_per_tick";
    private static final String SETTING_DELAY_TICKS = "delay_ticks";
    private final ItemIdSetValue trashItemsValue =
            itemList("shitDropperTrashItems", SETTING_TRASH_ITEM_IDS, TextListSetting.PickerMode.ALL);

    /**
     * How many trash stacks are allowed to exist simultaneously in occupied slots. 0 = drop all.
     */
    private final NumberValue<Integer> maxTrashSlotsValue = num(
            "shitDropperMaxTrashSlots",
            SETTING_MAX_TRASH_SLOTS,
            0,
            0,
            36
    );

    /**
     * Maximum amount of stacks to drop per tick (to reduce server spam).
     */
    private final NumberValue<Integer> stacksPerTickValue = num(
            "shitDropperStacksPerTick",
            SETTING_STACKS_PER_TICK,
            1,
            1,
            9
    );

    /**
     * Delay between drop batches (ticks).
     */
    private final NumberValue<Integer> delayTicksValue = num(
            "shitDropperDelayTicks",
            SETTING_DELAY_TICKS,
            2,
            0,
            40
    );

    /**
     * Only drop while the player's own inventory screen is open (the server then sees a click context a human can
     * produce); off lets it act from the HUD as well, still only while the server sees a stationary player.
     */
    private final BooleanValue inventoryOnly = bool("shitDropperInventoryOnly", "inventory_only", true);

    private int cooldownTicks;

    @Override
    public void onEnable() {
        cooldownTicks = 0;
        Set<String> targets = trashItemsValue.get();
        if (targets == null || targets.isEmpty()) Notifier.warning(I18n.get("notification.shitdropper.empty"));
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.gameMode == null) return;

        var handler = mc.player.containerMenu;
        if (handler == null) return;

        if (!handler.getCarried().isEmpty()) return;

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        if (inventoryOnly.get() && !(ClientScreen.current() instanceof InventoryScreen)) return;
        // A click while the server sees movement would be queued and freeze the player; wait for a stop instead.
        if (!PacketWindow.INSTANCE.stationaryForClick()) return;

        var targets = trashItemsValue.get();
        if (targets == null || targets.isEmpty()) return;

        // Only the player's own inventory slots (0..35) count as trash.
        List<Integer> trashSlots = new ArrayList<>();

        for (int screenSlot = 0; screenSlot < handler.slots.size(); screenSlot++) {
            var slot = handler.slots.get(screenSlot);
            if (slot == null || !slot.hasItem()) continue;

            if (!(slot.container instanceof net.minecraft.world.entity.player.Inventory inv)) continue;
            if (inv.player != mc.player) continue;

            int invIndex = slot.getContainerSlot();
            if (invIndex < 0 || invIndex >= 36) continue;

            var stack = slot.getItem();
            if (stack == null || stack.isEmpty()) continue;

            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();
            if (targets.contains(id)) {
                trashSlots.add(screenSlot);
            }
        }

        int allowed = Math.max(0, maxTrashSlotsValue.get());
        int over = trashSlots.size() - allowed;
        if (over <= 0) return;

        int perTick = Math.max(1, stacksPerTickValue.get());
        int toDrop = Math.min(perTick, over);

        int dropped = 0;
        for (int i = 0; i < toDrop; i++) {
            if (!InventorySwap.INSTANCE.dropStack(handler.slots.get(trashSlots.get(i)))) break;
            dropped++;
        }

        // A refused drop is retried next tick instead of waiting out the cooldown.
        if (dropped > 0) cooldownTicks = Math.max(0, delayTicksValue.get());
    }

}
