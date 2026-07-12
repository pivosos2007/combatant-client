/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.inventory;

import combatant.client.config.values.EnumValue;

public enum InventorySwapVisibility implements EnumValue.IdProvider {
    NORMAL("normal"),
    SILENT("silent");

    private final String id;

    InventorySwapVisibility(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}
