/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.subsystem;

import combatant.client.config.SettingDef;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.SetValue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ConfigSubsystem(value = "security", legacyNames = "mainconfig", settingOwner = "main_config")
public final class SecurityConfig extends SubsystemConfig {
    public static final SecurityConfig INSTANCE = new SecurityConfig();

    private final BooleanValue backdoorProtection = bool("backdoorProtection", true);
    private final BooleanValue backdoorTranslationFilter = bool("backdoorTranslationFilter", true);
    private final BooleanValue backdoorKeybindFilter = bool("backdoorKeybindFilter", true);
    private final BooleanValue backdoorLocalHttpGuard = bool("backdoorLocalHttpGuard", true);
    private final BooleanValue backdoorPackCacheIsolation = bool("backdoorPackCacheIsolation", true);
    private final BooleanValue backdoorAllowLocalHttpWhenServerLocal = bool("backdoorAllowLocalHttpWhenServerLocal", true);
    private final SetValue backdoorAllowedTranslationPacks = stringSet("backdoorAllowedTranslationPacks", defaultAllowedTranslationPacks());
    private final SetValue backdoorAllowedKeybinds = stringSet("backdoorAllowedKeybinds");

    private SecurityConfig() {
        loadConfig();
    }

    public static SecurityConfig get() {
        return INSTANCE;
    }

    private static Set<String> defaultAllowedTranslationPacks() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add("vanilla");
        return out;
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

    public Set<String> getBackdoorAllowedKeybinds() {
        return new LinkedHashSet<>(backdoorAllowedKeybinds.get());
    }

    @Override
    public List<SettingDef> getSettingDefs() {
        return settings(
                SettingDef.bool(backdoorProtection),
                SettingDef.bool(backdoorTranslationFilter).visibleWhen(backdoorProtection::get),
                SettingDef.bool(backdoorKeybindFilter).visibleWhen(backdoorProtection::get),
                SettingDef.bool(backdoorLocalHttpGuard).visibleWhen(backdoorProtection::get),
                SettingDef.bool(backdoorPackCacheIsolation).visibleWhen(backdoorProtection::get),
                SettingDef.bool(backdoorAllowLocalHttpWhenServerLocal).visibleWhen(backdoorProtection::get),
                SettingDef.textList(backdoorAllowedTranslationPacks)
                        .visibleWhen(() -> backdoorProtection.get() && backdoorTranslationFilter.get()),
                SettingDef.textList(backdoorAllowedKeybinds)
                        .visibleWhen(() -> backdoorProtection.get() && backdoorKeybindFilter.get())
        );
    }
}
