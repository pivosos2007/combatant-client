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
import combatant.client.render.iris.IrisRuntime;
import net.minecraft.client.resources.language.I18n;

import java.util.List;

@ConfigSubsystem(value = "visual", legacyNames = "mainconfig", settingOwner = "main_config")
public final class VisualConfig extends SubsystemConfig {
    public static final VisualConfig INSTANCE = new VisualConfig();

    private static final String IRIS_MSAA_REASON_KEY = "setting.main_config.msaa3d.iris_blocked";
    private static final String IRIS_MSAA_REASON_FALLBACK = "Iris shaderpack pipeline is active.";

    private final BooleanValue combatantMainMenu = bool("combatantMainMenu", true);
    private final ModeValue menuBackground = mode("menuBackground", "png", "png", "aurora", "waves");
    private final BooleanValue menuClockShowSeconds = bool("menuClockShowSeconds", false);
    private final ModeValue msaa3d = mode("msaa3d", "off", "off", "2x", "4x");
    private final BooleanValue clickGuiModulesHints = bool("clickGuiModulesHints", true);
    private final BooleanValue clickGuiHudEditorHints = bool("clickGuiHudEditorHints", true);

    private VisualConfig() {
        loadConfig();
    }

    public static VisualConfig get() {
        return INSTANCE;
    }

    public boolean isCombatantMainMenuEnabled() {
        return combatantMainMenu.get();
    }

    public String getMenuBackgroundMode() {
        String mode = menuBackground.get();
        return mode != null ? mode : "png";
    }

    public boolean isMenuClockShowSeconds() {
        return menuClockShowSeconds.get();
    }

    public int getMsaa3dSamples() {
        if (IrisRuntime.isShaderpackRendererActive()) return 0;
        String value = msaa3d.get();
        if (value == null) return 0;
        if (value.equalsIgnoreCase("2x")) return 2;
        if (value.equalsIgnoreCase("4x")) return 4;
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
        saveConfig();
    }

    public void setClickGuiHintsEnabled(boolean enabled) {
        setClickGuiModulesHintsEnabled(enabled);
    }

    public boolean isClickGuiHudEditorHintsEnabled() {
        return clickGuiHudEditorHints.get();
    }

    public void setClickGuiHudEditorHintsEnabled(boolean enabled) {
        clickGuiHudEditorHints.set(enabled);
        saveConfig();
    }

    @Override
    public List<SettingDef> getSettingDefs() {
        return settings(
                SettingDef.mode(msaa3d)
                        .unavailableWhen(IrisRuntime::isShaderpackRendererActive, VisualConfig::irisMsaaReason),
                SettingDef.bool(combatantMainMenu),
                SettingDef.mode(menuBackground).visibleWhen(combatantMainMenu::get),
                SettingDef.bool(menuClockShowSeconds)
        );
    }

    private static String irisMsaaReason() {
        String translated = I18n.get(IRIS_MSAA_REASON_KEY);
        return IRIS_MSAA_REASON_KEY.equals(translated) ? IRIS_MSAA_REASON_FALLBACK : translated;
    }
}
