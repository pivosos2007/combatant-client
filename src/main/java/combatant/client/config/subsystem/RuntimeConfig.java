/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.subsystem;

import combatant.client.config.SettingDef;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.logging.DebugMode;

import java.util.List;

@ConfigSubsystem(value = "runtime", legacyNames = "mainconfig", settingOwner = "main_config")
public final class RuntimeConfig extends SubsystemConfig {
    public static final RuntimeConfig INSTANCE = new RuntimeConfig();

    private final ModeValue debug = mode(
            "debug", "off", "off", "error_only", "error_and_warnings", "info", "config", "render_thread", "stencil", "serverdebug", "all"
    );
    private final BooleanValue forcePvp = bool("forcePvp", false);
    private final BooleanValue disableNarrator = bool("disableNarrator", true);

    private RuntimeConfig() {
        loadConfig();
    }

    public static RuntimeConfig get() {
        return INSTANCE;
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
        saveConfig();
    }

    public boolean isForcePvp() {
        return forcePvp.get();
    }

    public boolean isNarratorDisabled() {
        return disableNarrator.get();
    }

    @Override
    public List<SettingDef> getSettingDefs() {
        return settings(
                SettingDef.mode(debug),
                SettingDef.bool(forcePvp),
                SettingDef.bool(disableNarrator)
        );
    }

    @Override
    protected void afterLoad() {
        applyDebugMode();
    }

    @Override
    protected void beforeSave() {
        applyDebugMode();
    }

    private void applyDebugMode() {
        DebugLog.setMode(getDebugMode());
    }
}
