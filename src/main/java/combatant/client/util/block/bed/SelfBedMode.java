/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.bed;

import combatant.client.config.values.EnumValue;

public enum SelfBedMode implements EnumValue.IdProvider {
    NONE("none"),
    COLOR("color"),
    SPAWN_LOCATION("spawn_location"),
    MANUAL("manual");

    private final String id;

    SelfBedMode(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}