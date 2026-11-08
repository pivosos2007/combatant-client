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
import combatant.client.features.theme.Theme;

import java.util.List;

@ConfigSubsystem(value = "map/ui", settingOwner = "map_ui")
public final class MapUiConfig extends SubsystemConfig {
    public static final MapUiConfig INSTANCE = new MapUiConfig();

    private final ModeValue arrowColorMode = mode("arrowColorMode", "Theme", "Theme", "Custom");
    private final RGBColorValue arrowCustomColor = value(new RGBColorValue("arrowCustomColor", "#F5F8FC"));
    private final BooleanValue advancedPlayerMarkers = bool("advancedPlayerMarkers", false);
    // Map information panels are presentation-only. Each ESP controls its own source visibility.
    private final BooleanValue mapInfoEnabled = bool("mapInfoEnabled", true);
    private final BooleanValue mapInfoIcons = bool("mapInfoIcons", true);
    private final BooleanValue mapInfoSources = bool("mapInfoSources", true);
    private final BooleanValue mapInfoChunkHighlights = bool("mapInfoChunkHighlights", true);
    private final NumberValue<Integer> mapInfoHighlightIntensity = number("mapInfoHighlightIntensity", 65, 0, 100);
    private final NumberValue<Integer> mapInfoBackground = number("mapInfoBackground", 100, 30, 100);
    private final NumberValue<Integer> mapInfoCardWidth = number("mapInfoCardWidth", 176, 128, 320);
    private final NumberValue<Integer> mapInfoRows = number("mapInfoRows", 5, 1, 16);
    private final NumberValue<Integer> mapInfoClusterPixels = number("mapInfoClusterPixels", 176, 72, 360);
    private final NumberValue<Integer> mapInfoMaxCards = number("mapInfoMaxCards", 60, 8, 180);

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

    /**
     * Canonical player-arrow color used by both Combatant's world-map arrow and Xaero's native minimap arrow.
     * RGB is controlled by Theme/Custom here; renderer-specific opacity remains owned by the renderer.
     */
    public int resolvedArrowColorArgb() {
        int color = isCustomArrowColor() ? arrowCustomColor.getArgb() : Theme.theme().accent();
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    public boolean mapInfoEnabled() { return mapInfoEnabled.get(); }
    public boolean mapInfoIcons() { return mapInfoIcons.get(); }
    public boolean mapInfoSources() { return mapInfoSources.get(); }
    public boolean mapInfoChunkHighlights() { return mapInfoChunkHighlights.get(); }
    public int mapInfoHighlightIntensity() { return mapInfoHighlightIntensity.get(); }
    public int mapInfoBackground() { return mapInfoBackground.get(); }
    public int mapInfoCardWidth() { return mapInfoCardWidth.get(); }
    public int mapInfoRows() { return mapInfoRows.get(); }
    public int mapInfoClusterPixels() { return mapInfoClusterPixels.get(); }
    public int mapInfoMaxCards() { return mapInfoMaxCards.get(); }

    public BooleanValue mapInfoEnabledValue() { return mapInfoEnabled; }
    public BooleanValue mapInfoIconsValue() { return mapInfoIcons; }
    public BooleanValue mapInfoSourcesValue() { return mapInfoSources; }
    public BooleanValue mapInfoChunkHighlightsValue() { return mapInfoChunkHighlights; }
    public NumberValue<Integer> mapInfoHighlightIntensityValue() { return mapInfoHighlightIntensity; }
    public NumberValue<Integer> mapInfoBackgroundValue() { return mapInfoBackground; }
    public NumberValue<Integer> mapInfoCardWidthValue() { return mapInfoCardWidth; }
    public NumberValue<Integer> mapInfoRowsValue() { return mapInfoRows; }
    public NumberValue<Integer> mapInfoClusterPixelsValue() { return mapInfoClusterPixels; }
    public NumberValue<Integer> mapInfoMaxCardsValue() { return mapInfoMaxCards; }

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
                SettingDef.bool("mapInfoEnabled", mapInfoEnabled),
                SettingDef.bool("mapInfoIcons", mapInfoIcons).visibleWhen(this::mapInfoEnabled),
                SettingDef.bool("mapInfoSources", mapInfoSources).visibleWhen(this::mapInfoEnabled),
                SettingDef.bool("mapInfoChunkHighlights", mapInfoChunkHighlights).visibleWhen(this::mapInfoEnabled),
                SettingDef.number("mapInfoHighlightIntensity", mapInfoHighlightIntensity)
                        .visibleWhen(() -> mapInfoEnabled() && mapInfoChunkHighlights()),
                SettingDef.number("mapInfoBackground", mapInfoBackground).visibleWhen(this::mapInfoEnabled),
                SettingDef.number("mapInfoCardWidth", mapInfoCardWidth).visibleWhen(this::mapInfoEnabled),
                SettingDef.number("mapInfoRows", mapInfoRows).visibleWhen(this::mapInfoEnabled),
                SettingDef.number("mapInfoClusterPixels", mapInfoClusterPixels).visibleWhen(this::mapInfoEnabled),
                SettingDef.number("mapInfoMaxCards", mapInfoMaxCards).visibleWhen(this::mapInfoEnabled),

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
