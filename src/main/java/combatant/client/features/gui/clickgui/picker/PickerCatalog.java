/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.picker;

import combatant.client.features.gui.clickgui.settings.TextListSetting;

import java.util.List;

@FunctionalInterface
public interface PickerCatalog {
    List<PickerEntryData> entries(TextListSetting owner);
}
