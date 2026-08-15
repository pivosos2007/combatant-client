/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.subsystem;

import combatant.client.config.MainConfig;
import combatant.client.config.SettingDef;
import combatant.client.config.SettingOwner;

import java.util.List;

public final class SecuritySettingsContributor implements MainSettingsContributor {
    @Override public String id() { return "security"; }
    @Override public String titleKey() { return "clickgui.settings.main.security"; }
    @Override public String fallbackTitle() { return "Security"; }
    @Override public String icon() { return "shield-user"; }
    @Override public int order() { return 200; }
    @Override public SettingOwner owner() { return MainConfig.get(); }
    @Override public List<SettingDef> settingDefs() { return MainConfig.get().getSecuritySettingDefs(); }
}
