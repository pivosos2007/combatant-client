/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.subsystem;

import combatant.client.config.subsystem.VisualConfig;
import combatant.client.config.SettingDef;
import combatant.client.config.SettingOwner;

import java.util.List;

public final class VisualSettingsContributor implements MainSettingsContributor {
    @Override public String id() { return "visual"; }
    @Override public String titleKey() { return "clickgui.settings.main.visual"; }
    @Override public String fallbackTitle() { return "Visual"; }
    @Override public String icon() { return "brush"; }
    @Override public int order() { return 100; }
    @Override public SettingOwner owner() { return VisualConfig.get(); }
    @Override public List<SettingDef> settingDefs() { return VisualConfig.get().getSettingDefs(); }
}
