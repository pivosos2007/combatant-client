/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.inventory;

import combatant.client.config.values.EnumValue;

public enum InventorySwapPolicy implements EnumValue.IdProvider {
    NONE("none"),
    GRIM_STRICT("grim_strict"),
    LEGIT("legit");

    private final String id;

    InventorySwapPolicy(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}
