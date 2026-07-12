/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block;

import combatant.client.config.values.EnumValue;

public enum BlockOutlineShapeMode implements EnumValue.IdProvider {
    FULL_BLOCK("full_block"),
    VOXEL_SHAPE("voxel_shape");

    private final String id;

    BlockOutlineShapeMode(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}
