/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config;

import combatant.client.config.subsystem.InventoryConfig;
import combatant.client.config.subsystem.RuntimeConfig;
import combatant.client.config.subsystem.SecurityConfig;
import combatant.client.config.subsystem.VisualConfig;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.util.logging.DebugMode;
import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import combatant.client.util.player.inventory.InventorySwapVisibility;

import java.util.List;
import java.util.Set;

/**
 * Compatibility facade for the former monolithic global config.
 *
 * <p>The actual values are owned and persisted by independent subsystem configs.
 * This aggregate deliberately has no file of its own.</p>
 */
public final class MainConfig implements ConfigAggregate, ConfigNameProvider, SettingOwner {
    public static final MainConfig INSTANCE = new MainConfig();

    private final VisualConfig visual = VisualConfig.get();
    private final SecurityConfig security = SecurityConfig.get();
    private final InventoryConfig inventory = InventoryConfig.get();
    private final RuntimeConfig runtime = RuntimeConfig.get();
    private final List<? extends ConfigObject> children = List.of(visual, security, inventory, runtime);

    private MainConfig() {
    }

    public static MainConfig get() {
        return INSTANCE;
    }

    @Override
    public String getConfigName() {
        return "mainconfig";
    }

    @Override
    public String name() {
        return "main_config";
    }

    @Override
    public List<? extends ConfigObject> configChildren() {
        return children;
    }

    @Override
    public void saveConfig() {
        ConfigSerializer.requestSave(this);
    }

    public DebugMode getDebugMode() {
        return runtime.getDebugMode();
    }

    public void setDebugMode(DebugMode mode) {
        runtime.setDebugMode(mode);
    }

    public boolean isForcePvp() {
        return runtime.isForcePvp();
    }

    public boolean isCombatantMainMenuEnabled() {
        return visual.isCombatantMainMenuEnabled();
    }

    public String getMenuBackgroundMode() {
        return visual.getMenuBackgroundMode();
    }

    public boolean isMenuClockShowSeconds() {
        return visual.isMenuClockShowSeconds();
    }

    public int getMsaa3dSamples() {
        return visual.getMsaa3dSamples();
    }

    public boolean isClickGuiModulesHintsEnabled() {
        return visual.isClickGuiModulesHintsEnabled();
    }

    public boolean isClickGuiHintsEnabled() {
        return visual.isClickGuiHintsEnabled();
    }

    public void setClickGuiModulesHintsEnabled(boolean enabled) {
        visual.setClickGuiModulesHintsEnabled(enabled);
    }

    public void setClickGuiHintsEnabled(boolean enabled) {
        visual.setClickGuiHintsEnabled(enabled);
    }

    public boolean isClickGuiHudEditorHintsEnabled() {
        return visual.isClickGuiHudEditorHintsEnabled();
    }

    public void setClickGuiHudEditorHintsEnabled(boolean enabled) {
        visual.setClickGuiHudEditorHintsEnabled(enabled);
    }

    public InventorySwapPolicy getInventorySwapPolicy() {
        return inventory.getInventorySwapPolicy();
    }

    public void setInventorySwapPolicy(InventorySwapPolicy policy) {
        inventory.setInventorySwapPolicy(policy);
    }

    public InventorySearchScope getInventorySwapScope() {
        return inventory.getInventorySwapScope();
    }

    public void setInventorySwapScope(InventorySearchScope scope) {
        inventory.setInventorySwapScope(scope);
    }

    public InventorySwapVisibility getInventorySwapVisibility() {
        return inventory.getInventorySwapVisibility();
    }

    public void setInventorySwapVisibility(InventorySwapVisibility visibility) {
        inventory.setInventorySwapVisibility(visibility);
    }

    public boolean isInventorySwapRestore() {
        return inventory.isInventorySwapRestore();
    }

    public void setInventorySwapRestore(boolean restore) {
        inventory.setInventorySwapRestore(restore);
    }

    public boolean isInventorySwapPreferHotbar() {
        return inventory.isInventorySwapPreferHotbar();
    }

    public void setInventorySwapPreferHotbar(boolean preferHotbar) {
        inventory.setInventorySwapPreferHotbar(preferHotbar);
    }

    public int getInventorySwapLegitWaitTicks() {
        return inventory.getInventorySwapLegitWaitTicks();
    }

    public void setInventorySwapLegitWaitTicks(int ticks) {
        inventory.setInventorySwapLegitWaitTicks(ticks);
    }

    public int getInventorySwapStrictInventoryWaitTicks() {
        return inventory.getInventorySwapStrictInventoryWaitTicks();
    }

    public void setInventorySwapStrictInventoryWaitTicks(int ticks) {
        inventory.setInventorySwapStrictInventoryWaitTicks(ticks);
    }

    public int getInventorySwapStrictMovementLockTicks() {
        return inventory.getInventorySwapStrictMovementLockTicks();
    }

    public void setInventorySwapStrictMovementLockTicks(int ticks) {
        inventory.setInventorySwapStrictMovementLockTicks(ticks);
    }

    public EnumValue<InventorySwapPolicy> inventorySwapPolicyValue() {
        return inventory.inventorySwapPolicyValue();
    }

    public EnumValue<InventorySearchScope> inventorySwapScopeValue() {
        return inventory.inventorySwapScopeValue();
    }

    public EnumValue<InventorySwapVisibility> inventorySwapVisibilityValue() {
        return inventory.inventorySwapVisibilityValue();
    }

    public BooleanValue inventorySwapRestoreValue() {
        return inventory.inventorySwapRestoreValue();
    }

    public BooleanValue inventorySwapPreferHotbarValue() {
        return inventory.inventorySwapPreferHotbarValue();
    }

    public NumberValue<Integer> inventorySwapLegitWaitTicksValue() {
        return inventory.inventorySwapLegitWaitTicksValue();
    }

    public NumberValue<Integer> inventorySwapStrictInventoryWaitTicksValue() {
        return inventory.inventorySwapStrictInventoryWaitTicksValue();
    }

    public NumberValue<Integer> inventorySwapStrictMovementLockTicksValue() {
        return inventory.inventorySwapStrictMovementLockTicksValue();
    }

    public List<SettingDef> getImageSettingDefs() {
        return visual.getSettingDefs();
    }

    public List<SettingDef> getMiscellaneousSettingDefs() {
        return runtime.getSettingDefs();
    }

    public List<SettingDef> getSecuritySettingDefs() {
        return security.getSettingDefs();
    }

    public List<SettingDef> getUtilitySettingDefs() {
        return inventory.getSettingDefs();
    }

    public boolean isNarratorDisabled() {
        return runtime.isNarratorDisabled();
    }

    public boolean isNativeGuardEnabled(String platformId) {
        return runtime.isNativeGuardEnabled(platformId);
    }

    public boolean isNativeGuardWindowsX8664Enabled() {
        return runtime.isNativeGuardWindowsX8664Enabled();
    }

    public void setNativeGuardWindowsX8664Enabled(boolean enabled) {
        runtime.setNativeGuardWindowsX8664Enabled(enabled);
    }

    public boolean isBackdoorProtectionEnabled() {
        return security.isBackdoorProtectionEnabled();
    }

    public boolean isBackdoorTranslationFilterEnabled() {
        return security.isBackdoorTranslationFilterEnabled();
    }

    public boolean isBackdoorKeybindFilterEnabled() {
        return security.isBackdoorKeybindFilterEnabled();
    }

    public boolean isBackdoorLocalHttpGuardEnabled() {
        return security.isBackdoorLocalHttpGuardEnabled();
    }

    public boolean isBackdoorPackCacheIsolationEnabled() {
        return security.isBackdoorPackCacheIsolationEnabled();
    }

    public boolean isBackdoorAllowLocalHttpWhenServerLocal() {
        return security.isBackdoorAllowLocalHttpWhenServerLocal();
    }

    public Set<String> getBackdoorAllowedTranslationPacks() {
        return security.getBackdoorAllowedTranslationPacks();
    }

    public Set<String> getBackdoorAllowedKeybinds() {
        return security.getBackdoorAllowedKeybinds();
    }
}
