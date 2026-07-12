/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config;

import combatant.client.config.values.*;
import combatant.client.config.values.*;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.logging.DebugMode;
import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import combatant.client.util.player.inventory.InventorySwapVisibility;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MainConfig implements ConfigObject, ConfigNameProvider, SettingOwner {

    public static final MainConfig INSTANCE = new MainConfig();

    //@CFGComment("Debug logging mode: off / error_only / error_and_warnings / info / config / render_thread / stencil / serverdebug / all")
    private final ModeValue debug =
            new ModeValue("debug", "off", "off", "error_only", "error_and_warnings", "info", "config", "render_thread", "stencil", "serverdebug", "all");

    //@CFGComment("Force PvP mode ON everywhere (dev/testing only)")
    private final BooleanValue forcePvp =
            new BooleanValue("forcePvp", false);

    //@CFGComment("Menu background shader: off / waves / aurora")
    private final ModeValue menuBg =
            new ModeValue("menuBg", "off", "off", "waves", "aurora");

    //@CFGComment("Use ClickGUI theme colors for menu background shader")
    private final BooleanValue menuBgUseTheme =
            new BooleanValue("menuBgUseTheme", false);

    //@CFGComment("Show seconds in the custom main menu clock")
    private final BooleanValue menuClockShowSeconds =
            new BooleanValue("menuClockShowSeconds", false);

    //@CFGComment("MSAA for world render: off / 2x / 4x")
    private final ModeValue msaa3d =
            new ModeValue("msaa3d", "off", "off", "2x", "4x");

    //@CFGComment("Show ClickGUI modules hotkey hints")
    private final BooleanValue clickGuiModulesHints =
            new BooleanValue("clickGuiModulesHints", true);

    //@CFGComment("Show ClickGUI HUD editor hotkey hints")
    private final BooleanValue clickGuiHudEditorHints =
            new BooleanValue("clickGuiHudEditorHints", true);

    //@CFGComment("Default inventory swap safety policy: none / grim_strict / legit")
    private final EnumValue<InventorySwapPolicy> inventorySwapPolicy =
            new EnumValue<>("inventorySwapPolicy", InventorySwapPolicy.NONE, InventorySwapPolicy.class);

    //@CFGComment("Default inventory swap search scope: hotbar / inventory / full")
    private final EnumValue<InventorySearchScope> inventorySwapScope =
            new EnumValue<>("inventorySwapScope", InventorySearchScope.FULL, InventorySearchScope.class);

    //@CFGComment("Default inventory swap visibility: normal / silent")
    private final EnumValue<InventorySwapVisibility> inventorySwapVisibility =
            new EnumValue<>("inventorySwapVisibility", InventorySwapVisibility.SILENT, InventorySwapVisibility.class);

    //@CFGComment("Restore the original hotbar slot after default swap requests")
    private final BooleanValue inventorySwapRestore =
            new BooleanValue("inventorySwapRestore", true);

    //@CFGComment("Prefer hotbar matches before inventory matches for full-scope swap requests")
    private final BooleanValue inventorySwapPreferHotbar =
            new BooleanValue("inventorySwapPreferHotbar", true);

    //@CFGComment("Extra delay before queued legit inventory swap actions")
    private final NumberValue<Integer> inventorySwapLegitWaitTicks =
            new NumberValue<>("inventorySwapLegitWaitTicks", 2, 0, 20);

    // @CFGComment("Extra delay before queued Grim-strict inventory click/close actions")
    private final NumberValue<Integer> inventorySwapStrictInventoryWaitTicks =
            new NumberValue<>("inventorySwapStrictInventoryWaitTicks", 1, 0, 20);

    //@CFGComment("Movement input lock duration while waiting for Grim-strict inventory actions")
    private final NumberValue<Integer> inventorySwapStrictMovementLockTicks =
            new NumberValue<>("inventorySwapStrictMovementLockTicks", 2, 0, 20);

    //@CFGComment("Hard-disable narrator and narrator hotkey unless Panic mode is active")
    private final BooleanValue disableNarrator =
            new BooleanValue("disableNarrator", true);

    //@CFGComment("Always-on backdoor protection master switch")
    private final BooleanValue backdoorProtection =
            new BooleanValue("backdoorProtection", true);

    //@CFGComment("Filter non-whitelisted translation keys in server-controlled sign/anvil text")
    private final BooleanValue backdoorTranslationFilter =
            new BooleanValue("backdoorTranslationFilter", true);

    //@CFGComment("Filter non-vanilla keybind text keys in server-controlled text")
    private final BooleanValue backdoorKeybindFilter =
            new BooleanValue("backdoorKeybindFilter", true);

    //@CFGComment("Block remote servers from redirecting resource-pack downloads into local/private network addresses")
    private final BooleanValue backdoorLocalHttpGuard =
            new BooleanValue("backdoorLocalHttpGuard", true);

    //@CFGComment("Isolate downloaded server-pack cache by account UUID to reduce pack-cache fingerprinting")
    private final BooleanValue backdoorPackCacheIsolation =
            new BooleanValue("backdoorPackCacheIsolation", true);

    //@CFGComment("Allow local/private HTTP requests when connected to local/integrated server")
    private final BooleanValue backdoorAllowLocalHttpWhenServerLocal =
            new BooleanValue("backdoorAllowLocalHttpWhenServerLocal", true);

    //@CFGComment("Extra allowed translation resource-pack ids for server-controlled text")
    private final SetValue backdoorAllowedTranslationPacks =
            new SetValue("backdoorAllowedTranslationPacks", defaultAllowedTranslationPacks());

    //@CFGComment("Extra allowed keybind ids for server-controlled text")
    private final SetValue backdoorAllowedKeybinds =
            new SetValue("backdoorAllowedKeybinds");

    private MainConfig() {
        ConfigSerializer.load(this);
        applyDebugMode();
        applyInventorySwapSettings();
    }

    public static MainConfig get() {
        return INSTANCE;
    }

    private static Set<String> defaultAllowedTranslationPacks() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add("vanilla");
        return out;
    }

    @Override
    public String getConfigName() {
        return "mainconfig";
    }

    @Override
    public String name() {
        return "main_config";
    }

    // ================= DEBUG =================

    @Override
    public void saveConfig() {
        applyDebugMode();
        applyInventorySwapSettings();
        ConfigSerializer.requestSave(this);
    }

    public DebugMode getDebugMode() {
        try {
            return DebugMode.valueOf(debug.get().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DebugMode.OFF;
        }
    }

    public void setDebugMode(DebugMode mode) {
        if (mode == null) mode = DebugMode.OFF;
        debug.set(mode.name().toLowerCase());
        applyDebugMode();
        ConfigSerializer.requestSave(this);
    }

    private void applyDebugMode() {
        DebugLog.setMode(getDebugMode());
    }

    public boolean isForcePvp() {
        return forcePvp.get();
    }

    public String getMenuBackgroundMode() {
        return menuBg.get();
    }

    public boolean isMenuBackgroundUseTheme() {
        return menuBgUseTheme.get();
    }

    public boolean isMenuClockShowSeconds() {
        return menuClockShowSeconds.get();
    }

    public int getMsaa3dSamples() {
        String v = msaa3d.get();
        if (v == null) return 0;
        if (v.equalsIgnoreCase("2x")) return 2;
        if (v.equalsIgnoreCase("4x")) return 4;
        return 0;
    }

    public boolean isClickGuiModulesHintsEnabled() {
        return clickGuiModulesHints.get();
    }

    public boolean isClickGuiHintsEnabled() {
        return clickGuiModulesHints.get();
    }

    public void setClickGuiModulesHintsEnabled(boolean enabled) {
        clickGuiModulesHints.set(enabled);
        ConfigSerializer.requestSave(this);
    }

    public void setClickGuiHintsEnabled(boolean enabled) {
        setClickGuiModulesHintsEnabled(enabled);
    }

    public boolean isClickGuiHudEditorHintsEnabled() {
        return clickGuiHudEditorHints.get();
    }

    public void setClickGuiHudEditorHintsEnabled(boolean enabled) {
        clickGuiHudEditorHints.set(enabled);
        ConfigSerializer.requestSave(this);
    }

    public InventorySwapPolicy getInventorySwapPolicy() {
        return inventorySwapPolicy.get();
    }

    public void setInventorySwapPolicy(InventorySwapPolicy policy) {
        inventorySwapPolicy.set(policy != null ? policy : InventorySwapPolicy.NONE);
        saveAndApplyInventorySwapSettings();
    }

    public InventorySearchScope getInventorySwapScope() {
        return inventorySwapScope.get();
    }

    public void setInventorySwapScope(InventorySearchScope scope) {
        inventorySwapScope.set(scope != null ? scope : InventorySearchScope.FULL);
        saveAndApplyInventorySwapSettings();
    }

    public InventorySwapVisibility getInventorySwapVisibility() {
        return inventorySwapVisibility.get();
    }

    public void setInventorySwapVisibility(InventorySwapVisibility visibility) {
        inventorySwapVisibility.set(visibility != null ? visibility : InventorySwapVisibility.SILENT);
        saveAndApplyInventorySwapSettings();
    }

    public boolean isInventorySwapRestore() {
        return inventorySwapRestore.get();
    }

    public void setInventorySwapRestore(boolean restore) {
        inventorySwapRestore.set(restore);
        saveAndApplyInventorySwapSettings();
    }

    public boolean isInventorySwapPreferHotbar() {
        return inventorySwapPreferHotbar.get();
    }

    public void setInventorySwapPreferHotbar(boolean preferHotbar) {
        inventorySwapPreferHotbar.set(preferHotbar);
        saveAndApplyInventorySwapSettings();
    }

    public int getInventorySwapLegitWaitTicks() {
        return inventorySwapLegitWaitTicks.get();
    }

    public void setInventorySwapLegitWaitTicks(int ticks) {
        inventorySwapLegitWaitTicks.fromJson(ticks);
        saveAndApplyInventorySwapSettings();
    }

    public int getInventorySwapStrictInventoryWaitTicks() {
        return inventorySwapStrictInventoryWaitTicks.get();
    }

    public void setInventorySwapStrictInventoryWaitTicks(int ticks) {
        inventorySwapStrictInventoryWaitTicks.fromJson(ticks);
        saveAndApplyInventorySwapSettings();
    }

    public int getInventorySwapStrictMovementLockTicks() {
        return inventorySwapStrictMovementLockTicks.get();
    }

    public void setInventorySwapStrictMovementLockTicks(int ticks) {
        inventorySwapStrictMovementLockTicks.fromJson(ticks);
        saveAndApplyInventorySwapSettings();
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

    public List<SettingDef> getImageSettingDefs() {
        List<SettingDef> list = new ArrayList<>();
        list.add(SettingDef.mode(msaa3d));
        list.add(SettingDef.mode(menuBg));
        list.add(SettingDef.bool(menuClockShowSeconds));
        list.add(SettingDef.bool(menuBgUseTheme));
        return list;
    }

    public List<SettingDef> getMiscellaneousSettingDefs() {
        List<SettingDef> list = new ArrayList<>();
        list.add(SettingDef.mode(debug));
        list.add(SettingDef.bool(forcePvp));
        list.add(SettingDef.bool(disableNarrator));
        return list;
    }

    public List<SettingDef> getSecuritySettingDefs() {
        List<SettingDef> list = new ArrayList<>();
        list.add(SettingDef.bool(backdoorProtection));
        list.add(SettingDef.bool(backdoorTranslationFilter).visibleWhen(backdoorProtection::get));
        list.add(SettingDef.bool(backdoorKeybindFilter).visibleWhen(backdoorProtection::get));
        list.add(SettingDef.bool(backdoorLocalHttpGuard).visibleWhen(backdoorProtection::get));
        list.add(SettingDef.bool(backdoorPackCacheIsolation).visibleWhen(backdoorProtection::get));
        list.add(SettingDef.bool(backdoorAllowLocalHttpWhenServerLocal).visibleWhen(backdoorProtection::get));
        list.add(SettingDef.textList(backdoorAllowedTranslationPacks)
                .visibleWhen(() -> backdoorProtection.get() && backdoorTranslationFilter.get()));
        list.add(SettingDef.textList(backdoorAllowedKeybinds)
                .visibleWhen(() -> backdoorProtection.get() && backdoorKeybindFilter.get()));
        return list;
    }

    public List<SettingDef> getUtilitySettingDefs() {
        List<SettingDef> list = new ArrayList<>();
        list.add(SettingDef.mode(inventorySwapPolicy).common("inventory.swap_policy"));
        list.add(SettingDef.mode(inventorySwapScope).common("inventory.search_scope"));
        list.add(SettingDef.mode(inventorySwapVisibility).common("inventory.swap_visibility"));
        list.add(SettingDef.bool(inventorySwapRestore).common("inventory.restore_item"));
        list.add(SettingDef.bool(inventorySwapPreferHotbar).common("inventory.prefer_hotbar"));
        list.add(SettingDef.number(inventorySwapLegitWaitTicks).common("inventory.legit_wait_ticks"));
        list.add(SettingDef.number(inventorySwapStrictInventoryWaitTicks).common("inventory.strict_inventory_wait_ticks"));
        list.add(SettingDef.number(inventorySwapStrictMovementLockTicks).common("inventory.strict_movement_lock_ticks"));
        return list;
    }

    public boolean isNarratorDisabled() {
        return disableNarrator.get();
    }

    public boolean isBackdoorProtectionEnabled() {
        return backdoorProtection.get();
    }

    public boolean isBackdoorTranslationFilterEnabled() {
        return backdoorProtection.get() && backdoorTranslationFilter.get();
    }

    public boolean isBackdoorKeybindFilterEnabled() {
        return backdoorProtection.get() && backdoorKeybindFilter.get();
    }

    public boolean isBackdoorLocalHttpGuardEnabled() {
        return backdoorProtection.get() && backdoorLocalHttpGuard.get();
    }

    public boolean isBackdoorPackCacheIsolationEnabled() {
        return backdoorProtection.get() && backdoorPackCacheIsolation.get();
    }

    public boolean isBackdoorAllowLocalHttpWhenServerLocal() {
        return backdoorAllowLocalHttpWhenServerLocal.get();
    }

    public Set<String> getBackdoorAllowedTranslationPacks() {
        return new LinkedHashSet<>(backdoorAllowedTranslationPacks.get());
    }

    // ================= CONFIG =================

    public Set<String> getBackdoorAllowedKeybinds() {
        return new LinkedHashSet<>(backdoorAllowedKeybinds.get());
    }

    @Override
    public List<ConfigValue<?>> getConfigValues() {
        List<ConfigValue<?>> list = new ArrayList<>();
        list.add(debug);
        list.add(forcePvp);
        list.add(menuBg);
        list.add(menuClockShowSeconds);
        list.add(menuBgUseTheme);
        list.add(msaa3d);
        list.add(clickGuiModulesHints);
        list.add(clickGuiHudEditorHints);
        list.add(inventorySwapPolicy);
        list.add(inventorySwapScope);
        list.add(inventorySwapVisibility);
        list.add(inventorySwapRestore);
        list.add(inventorySwapPreferHotbar);
        list.add(inventorySwapLegitWaitTicks);
        list.add(inventorySwapStrictInventoryWaitTicks);
        list.add(inventorySwapStrictMovementLockTicks);
        list.add(disableNarrator);
        list.add(backdoorProtection);
        list.add(backdoorTranslationFilter);
        list.add(backdoorKeybindFilter);
        list.add(backdoorLocalHttpGuard);
        list.add(backdoorPackCacheIsolation);
        list.add(backdoorAllowLocalHttpWhenServerLocal);
        list.add(backdoorAllowedTranslationPacks);
        list.add(backdoorAllowedKeybinds);
        return list;
    }

    private void saveAndApplyInventorySwapSettings() {
        applyInventorySwapSettings();
        ConfigSerializer.requestSave(this);
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
