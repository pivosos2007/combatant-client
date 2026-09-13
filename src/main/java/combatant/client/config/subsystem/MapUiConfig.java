/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.subsystem;

import combatant.client.config.SettingDef;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.RGBColorValue;

import java.util.List;

@ConfigSubsystem(value = "map/ui", settingOwner = "map_ui")
public final class MapUiConfig extends SubsystemConfig {
    public static final MapUiConfig INSTANCE = new MapUiConfig();

    private final ModeValue arrowColorMode = mode("arrowColorMode", "Theme", "Theme", "Custom");
    private final RGBColorValue arrowCustomColor = value(new RGBColorValue("arrowCustomColor", "#F5F8FC"));

    private MapUiConfig() {
        loadConfig();
    }

    public static MapUiConfig get() {
        return INSTANCE;
    }

    public ModeValue arrowColorModeValue() {
        return arrowColorMode;
    }

    public RGBColorValue arrowCustomColorValue() {
        return arrowCustomColor;
    }

    public boolean isCustomArrowColor() {
        return "Custom".equalsIgnoreCase(arrowColorMode.get());
    }

    public int customArrowColorArgb() {
        return arrowCustomColor.getArgb();
    }

    @Override
    public List<SettingDef> getSettingDefs() {
        return List.of();
    }
}
