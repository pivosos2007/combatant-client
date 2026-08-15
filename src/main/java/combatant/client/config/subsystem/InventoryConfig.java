/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.subsystem;

import combatant.client.config.SettingDef;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import combatant.client.util.player.inventory.InventorySwapVisibility;

import java.util.List;

@ConfigSubsystem(value = "inventory", legacyNames = "mainconfig", settingOwner = "main_config")
public final class InventoryConfig extends SubsystemConfig {
    public static final InventoryConfig INSTANCE = new InventoryConfig();

    private final EnumValue<InventorySwapPolicy> inventorySwapPolicy = enumValue("inventorySwapPolicy", InventorySwapPolicy.NONE, InventorySwapPolicy.class);
    private final EnumValue<InventorySearchScope> inventorySwapScope = enumValue("inventorySwapScope", InventorySearchScope.FULL, InventorySearchScope.class);
    private final EnumValue<InventorySwapVisibility> inventorySwapVisibility = enumValue("inventorySwapVisibility", InventorySwapVisibility.SILENT, InventorySwapVisibility.class);
    private final BooleanValue inventorySwapRestore = bool("inventorySwapRestore", true);
    private final BooleanValue inventorySwapPreferHotbar = bool("inventorySwapPreferHotbar", true);
    private final NumberValue<Integer> inventorySwapLegitWaitTicks = number("inventorySwapLegitWaitTicks", 2, 0, 20);
    private final NumberValue<Integer> inventorySwapStrictInventoryWaitTicks = number("inventorySwapStrictInventoryWaitTicks", 1, 0, 20);
    private final NumberValue<Integer> inventorySwapStrictMovementLockTicks = number("inventorySwapStrictMovementLockTicks", 2, 0, 20);

    private InventoryConfig() {
        loadConfig();
    }

    public static InventoryConfig get() {
        return INSTANCE;
    }

    public InventorySwapPolicy getInventorySwapPolicy() {
        return inventorySwapPolicy.get();
    }

    public void setInventorySwapPolicy(InventorySwapPolicy policy) {
        inventorySwapPolicy.set(policy != null ? policy : InventorySwapPolicy.NONE);
        saveConfig();
    }

    public InventorySearchScope getInventorySwapScope() {
        return inventorySwapScope.get();
    }

    public void setInventorySwapScope(InventorySearchScope scope) {
        inventorySwapScope.set(scope != null ? scope : InventorySearchScope.FULL);
        saveConfig();
    }

    public InventorySwapVisibility getInventorySwapVisibility() {
        return inventorySwapVisibility.get();
    }

    public void setInventorySwapVisibility(InventorySwapVisibility visibility) {
        inventorySwapVisibility.set(visibility != null ? visibility : InventorySwapVisibility.SILENT);
        saveConfig();
    }

    public boolean isInventorySwapRestore() {
        return inventorySwapRestore.get();
    }

    public void setInventorySwapRestore(boolean restore) {
        inventorySwapRestore.set(restore);
        saveConfig();
    }

    public boolean isInventorySwapPreferHotbar() {
        return inventorySwapPreferHotbar.get();
    }

    public void setInventorySwapPreferHotbar(boolean preferHotbar) {
        inventorySwapPreferHotbar.set(preferHotbar);
        saveConfig();
    }

    public int getInventorySwapLegitWaitTicks() {
        return inventorySwapLegitWaitTicks.get();
    }

    public void setInventorySwapLegitWaitTicks(int ticks) {
        inventorySwapLegitWaitTicks.fromJson(ticks);
        saveConfig();
    }

    public int getInventorySwapStrictInventoryWaitTicks() {
        return inventorySwapStrictInventoryWaitTicks.get();
    }

    public void setInventorySwapStrictInventoryWaitTicks(int ticks) {
        inventorySwapStrictInventoryWaitTicks.fromJson(ticks);
        saveConfig();
    }

    public int getInventorySwapStrictMovementLockTicks() {
        return inventorySwapStrictMovementLockTicks.get();
    }

    public void setInventorySwapStrictMovementLockTicks(int ticks) {
        inventorySwapStrictMovementLockTicks.fromJson(ticks);
        saveConfig();
    }

    public EnumValue<InventorySwapPolicy> inventorySwapPolicyValue() {
        return inventorySwapPolicy;
    }

    public EnumValue<InventorySearchScope> inventorySwapScopeValue() {
        return inventorySwapScope;
    }

    public EnumValue<InventorySwapVisibility> inventorySwapVisibilityValue() {
        return inventorySwapVisibility;
    }

    public BooleanValue inventorySwapRestoreValue() {
        return inventorySwapRestore;
    }

    public BooleanValue inventorySwapPreferHotbarValue() {
        return inventorySwapPreferHotbar;
    }

    public NumberValue<Integer> inventorySwapLegitWaitTicksValue() {
        return inventorySwapLegitWaitTicks;
    }

    public NumberValue<Integer> inventorySwapStrictInventoryWaitTicksValue() {
        return inventorySwapStrictInventoryWaitTicks;
    }

    public NumberValue<Integer> inventorySwapStrictMovementLockTicksValue() {
        return inventorySwapStrictMovementLockTicks;
    }

    @Override
    public List<SettingDef> getSettingDefs() {
        return settings(
                SettingDef.mode(inventorySwapPolicy).common("inventory.swap_policy"),
                SettingDef.mode(inventorySwapScope).common("inventory.search_scope"),
                SettingDef.mode(inventorySwapVisibility).common("inventory.swap_visibility"),
                SettingDef.bool(inventorySwapRestore).common("inventory.restore_item"),
                SettingDef.bool(inventorySwapPreferHotbar).common("inventory.prefer_hotbar"),
                SettingDef.number(inventorySwapLegitWaitTicks).common("inventory.legit_wait_ticks"),
                SettingDef.number(inventorySwapStrictInventoryWaitTicks).common("inventory.strict_inventory_wait_ticks"),
                SettingDef.number(inventorySwapStrictMovementLockTicks).common("inventory.strict_movement_lock_ticks")
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
        InventorySwap swap = InventorySwap.INSTANCE;
        swap.setDefaultPolicy(inventorySwapPolicy.get());
        swap.setDefaultScope(inventorySwapScope.get());
        swap.setDefaultVisibility(inventorySwapVisibility.get());
        swap.setRestoreByDefault(inventorySwapRestore.get());
        swap.setPreferHotbar(inventorySwapPreferHotbar.get());
        swap.setLegitWaitTicks(inventorySwapLegitWaitTicks.get());
        swap.setStrictInventoryWaitTicks(inventorySwapStrictInventoryWaitTicks.get());
        swap.setStrictMovementLockTicks(inventorySwapStrictMovementLockTicks.get());
    }
}
