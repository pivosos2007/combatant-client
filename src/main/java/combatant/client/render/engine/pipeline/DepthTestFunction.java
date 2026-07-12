/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.pipeline;

import com.mojang.blaze3d.platform.CompareOp;

public enum DepthTestFunction {
    NO_DEPTH_TEST(null),
    LEQUAL_DEPTH_TEST(CompareOp.LESS_THAN_OR_EQUAL),
    GEQUAL_DEPTH_TEST(CompareOp.GREATER_THAN_OR_EQUAL);

    private final CompareOp compareOp;

    DepthTestFunction(CompareOp compareOp) {
        this.compareOp = compareOp;
    }

    public CompareOp compareOp() {
        return compareOp;
    }
}
