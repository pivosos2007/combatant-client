/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.automation.ActionPriority;
import combatant.client.automation.AutomationRuntime;
import combatant.client.automation.AutomationTask;
import combatant.client.automation.actions.UseItemAction;
import combatant.client.config.values.BindMode;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.ItemIdSetValue;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.anticheat.AntiCheatPreset;
import combatant.client.util.player.inventory.InventorySwapMode;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * One key press uses the configured item (ender pearl by default): {@link UseItemAction} brings it to the hand with
 * a hotbar lease (or an inventory swap, per mode) and uses it. Silent modes restore the previous selection; normal modes
 * switch visibly and stay on the item, like every other {@code NORMAL} swap in the client.
 */
@ModuleInfo(
        id = "quickuse",
        displayName = "QuickUse",
        aliases = {"HotUse", "QuickItem"},
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.quickuse.description")
public final class QuickUse extends Module {

    private static final String ACTION_USE = "use";
    private static final int FAIL_SAFE_TICKS = 60;

    private final Minecraft mc = Minecraft.getInstance();

    // grim = strict swap policy; vanilla = plain swaps.
    private final EnumValue<AntiCheatPreset> anticheat = enumMode("anticheat_mode", AntiCheatPreset.VANILLA);
    private final ItemIdSetValue items = itemList(
            "quickUseItems", "items", TextListSetting.PickerMode.ITEMS, Set.of("minecraft:ender_pearl"));
    private final EnumValue<InventorySwapMode> swapMode = enumSetting(
            "quickUseSwapMode", "swap_mode", InventorySwapMode.SILENT_FULL,
            InventorySwapMode.SILENT, InventorySwapMode.SILENT_FULL, InventorySwapMode.NORMAL,
            InventorySwapMode.NORMAL_FULL);
    private final BooleanValue swing = bool("quickUseSwing", "swing", true);

    private AutomationTask task;

    public QuickUse() {
        action(ACTION_USE, "NONE", BindMode.PRESS);
    }

    @Override
    public void onDisable() {
        AutomationRuntime.INSTANCE.cancelOwner(this, "quickuse disabled");
        task = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (task != null && task.isDone()) task = null;
        boolean pressed = isActionPressedOnce(ACTION_USE);
        if (!pressed || task != null) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null
                || combatant.client.util.screen.ClientScreen.current() != null) return;

        Set<String> ids = items.get();
        if (ids.isEmpty()) return;
        InventorySwapPolicy policy = anticheat.get() == AntiCheatPreset.GRIM
                ? InventorySwapPolicy.GRIM_STRICT : InventorySwapPolicy.NONE;
        UseItemAction use = UseItemAction.builder(stack -> matches(stack, ids))
                .mode(swapMode.get())
                .policy(policy)
                .swing(swing.get())
                .build();
        task = AutomationRuntime.INSTANCE.submit(AutomationTask
                .builder(this, "QuickUse", combatant.client.automation.Actions.timeout(use, FAIL_SAFE_TICKS))
                .priority(ActionPriority.UTILITY)
                .build());
    }

    private static boolean matches(ItemStack stack, Set<String> ids) {
        return !stack.isEmpty() && ids.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
}
