/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.subsystem;

import combatant.client.config.SettingDef;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.util.player.inventory.InventorySwap;

import java.util.List;

@ConfigSubsystem(value = "inventory", legacyNames = "mainconfig", settingOwner = "main_config")
public final class InventoryConfig extends SubsystemConfig {
    public static final InventoryConfig INSTANCE = new InventoryConfig();

    private final NumberValue<Integer> inventorySwapQueueMinTicks = number("inventorySwapQueueMinTicks", 1, 0, 20);
    private final NumberValue<Integer> inventorySwapQueueMaxTicks = number("inventorySwapQueueMaxTicks", 4, 0, 20);
    private final BooleanValue multitask = bool("multitask", false);

    private InventoryConfig() {
        loadConfig();
    }

    public static InventoryConfig get() {
        return INSTANCE;
    }

    public boolean isMultitask() {
        return multitask.get();
    }

    public void setMultitask(boolean enabled) {
        multitask.set(enabled);
        saveConfig();
    }

    public BooleanValue multitaskValue() {
        return multitask;
    }

    @Override
    public List<SettingDef> getSettingDefs() {
        return settings(
                SettingDef.bool(multitask).common("inventory.multitask"),
                SettingDef.number(inventorySwapQueueMinTicks).common("inventory.queued_min_ticks"),
                SettingDef.number(inventorySwapQueueMaxTicks).common("inventory.queued_max_ticks")
        );
    }

    @Override
    protected void afterLoad() {
        applyInventorySwapSettings();
    }

    @Override
    protected void beforeSave() {
        applyInventorySwapSettings();
    }

    private void applyInventorySwapSettings() {
        InventorySwap.INSTANCE.setQueuedDelayRange(inventorySwapQueueMinTicks.get(), inventorySwapQueueMaxTicks.get());
    }
}
