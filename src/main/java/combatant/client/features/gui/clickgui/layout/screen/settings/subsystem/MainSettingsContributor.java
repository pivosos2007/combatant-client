/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.subsystem;

import combatant.client.config.SettingDef;
import combatant.client.config.SettingOwner;

import java.util.List;

/**
 * A self-contained group shown in Main Settings.
 *
 * <p>Subsystems publish this SPI instead of being wired into the screen. The
 * owner remains responsible for persistence and setting translation keys.</p>
 */
public interface MainSettingsContributor {
    String id();

    String titleKey();

    String fallbackTitle();

    String icon();

    int order();

    SettingOwner owner();

    List<SettingDef> settingDefs();

    default boolean available() {
        return true;
    }
}
