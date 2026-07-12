/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block;

import combatant.client.config.values.EnumValue;

public enum BlockScanMode implements EnumValue.IdProvider {
    LEGIT("legit"),
    LOS("los"),
    LOS_UNLOCKED("los_unlocked"),
    AGGRESSIVE("aggressive");

    private final String id;

    BlockScanMode(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}
