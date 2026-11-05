/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.subsystem;

import combatant.client.config.SettingDef;
import combatant.client.config.SettingOwner;
import combatant.client.config.subsystem.SeedCrackerConfig;
import java.util.List;

public final class SeedCrackerSettingsContributor implements MainSettingsContributor {
    @Override public String id() { return "seedcracker"; }
    @Override public String titleKey() { return "clickgui.settings.main.seedcracker"; }
    @Override public String fallbackTitle() { return "Seed Cracker"; }
    @Override public String icon() { return "scan-search"; }
    @Override public int order() { return 350; }
    @Override public SettingOwner owner() { return SeedCrackerConfig.get(); }
    @Override public List<SettingDef> settingDefs() { return SeedCrackerConfig.get().getSettingDefs(); }
}
