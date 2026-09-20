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
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBColorValue;

import java.util.List;

@ConfigSubsystem(value = "map/ui", settingOwner = "map_ui")
public final class MapUiConfig extends SubsystemConfig {
    public static final MapUiConfig INSTANCE = new MapUiConfig();

    private final ModeValue arrowColorMode = mode("arrowColorMode", "Theme", "Theme", "Custom");
    private final RGBColorValue arrowCustomColor = value(new RGBColorValue("arrowCustomColor", "#F5F8FC"));
    private final BooleanValue advancedPlayerMarkers = bool("advancedPlayerMarkers", false);
    private final BooleanValue hudEnabled = bool("hudEnabled", true);
    private final BooleanValue hudWaypointMarkers = bool("hudWaypointMarkers", true);
    private final BooleanValue hudPlayerMarkers = bool("hudPlayerMarkers", true);
    private final NumberValue<Integer> hudPlayerMaxDistance = number("hudPlayerMaxDistance", 20_000, 32, 1_000_000);
    private final BooleanValue showInvisibleRadar = bool("showInvisibleRadar", false);
    private final BooleanValue allowServerPlayerData = bool("allowServerPlayerData", false);
    private final BooleanValue allowServerConfiguration = bool("allowServerConfiguration", false);

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

    public boolean advancedPlayerMarkers() {
        return advancedPlayerMarkers.get();
    }

    public BooleanValue advancedPlayerMarkersValue() {
        return advancedPlayerMarkers;
    }

    public boolean hudEnabled() {
        return hudEnabled.get();
    }

    public BooleanValue hudEnabledValue() {
        return hudEnabled;
    }

    public boolean hudWaypointMarkers() {
        return hudWaypointMarkers.get();
    }

    public BooleanValue hudWaypointMarkersValue() {
        return hudWaypointMarkers;
    }

    public boolean hudPlayerMarkers() {
        return hudPlayerMarkers.get();
    }

    public BooleanValue hudPlayerMarkersValue() {
        return hudPlayerMarkers;
    }

    public int hudPlayerMaxDistance() {
        return hudPlayerMaxDistance.get();
    }

    public NumberValue<Integer> hudPlayerMaxDistanceValue() {
        return hudPlayerMaxDistance;
    }

    public boolean showInvisibleRadar() {
        return showInvisibleRadar.get();
    }

    public BooleanValue showInvisibleRadarValue() {
        return showInvisibleRadar;
    }

    public boolean allowServerPlayerData() {
        return allowServerPlayerData.get();
    }

    public BooleanValue allowServerPlayerDataValue() {
        return allowServerPlayerData;
    }

    public boolean allowServerConfiguration() {
        return allowServerConfiguration.get();
    }

    public BooleanValue allowServerConfigurationValue() {
        return allowServerConfiguration;
    }

    @Override
    public List<SettingDef> getSettingDefs() {
        return List.of(
                SettingDef.mode("arrowColorMode", arrowColorMode),
                SettingDef.colorNoAlpha("arrowCustomColor", arrowCustomColor)
                        .visibleWhen(this::isCustomArrowColor),
                SettingDef.bool("advancedPlayerMarkers", advancedPlayerMarkers),
                SettingDef.bool("hudEnabled", hudEnabled),
                SettingDef.bool("hudWaypointMarkers", hudWaypointMarkers)
                        .visibleWhen(this::hudEnabled),
                SettingDef.bool("hudPlayerMarkers", hudPlayerMarkers)
                        .visibleWhen(this::hudEnabled),
                SettingDef.number("hudPlayerMaxDistance", hudPlayerMaxDistance)
                        .visibleWhen(() -> hudEnabled() && hudPlayerMarkers()),
                SettingDef.bool("showInvisibleRadar", showInvisibleRadar),
                SettingDef.bool("allowServerPlayerData", allowServerPlayerData),
                SettingDef.bool("allowServerConfiguration", allowServerConfiguration)
        );
    }
}
