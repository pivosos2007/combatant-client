/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.subsystem;

import combatant.client.config.subsystem.InventoryConfig;
import combatant.client.config.SettingDef;
import combatant.client.config.SettingOwner;

import java.util.List;

public final class InventorySettingsContributor implements MainSettingsContributor {
    @Override public String id() { return "inventory"; }
    @Override public String titleKey() { return "clickgui.settings.main.inventory"; }
    @Override public String fallbackTitle() { return "Inventory"; }
    @Override public String icon() { return "package"; }
    @Override public int order() { return 300; }
    @Override public SettingOwner owner() { return InventoryConfig.get(); }
    @Override public List<SettingDef> settingDefs() { return InventoryConfig.get().getSettingDefs(); }
}
