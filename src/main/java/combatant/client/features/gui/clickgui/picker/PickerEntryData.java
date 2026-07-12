/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.picker;

import net.minecraft.world.item.ItemStack;

public record PickerEntryData(String id, String label, ItemStack stack) {
    public String getId() {
        return id;
    }
}
