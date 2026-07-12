/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.addon;

import combatant.client.api.v0.module.ModuleExtensionContext;
import combatant.client.config.SettingDef;
import combatant.client.config.values.ConfigValue;
import combatant.client.config.values.ModeValue;
import combatant.client.features.module.Module;

final class ModuleExtensionContextImpl implements ModuleExtensionContext {
    private final String addonId;
    private final Module module;

    ModuleExtensionContextImpl(String addonId, Module module) {
        this.addonId = addonId;
        this.module = module;
    }

    @Override
    public String addonId() {
        return addonId;
    }

    @Override
    public String moduleId() {
        return module.name();
    }

    @Override
    public Module module() {
        return module;
    }

    @Override
    public void addSetting(SettingDef setting) {
        module.addExtensionSetting(setting);
    }

    @Override
    public ConfigValue<?> getConfigValue(String configName) {
        return module.getConfigValue(configName);
    }

    @Override
    public boolean addModeOption(String configName, String option) {
        ConfigValue<?> value = getConfigValue(configName);
        return value instanceof ModeValue mode && mode.addOption(option);
    }
}
