/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class RenderWarpTest {
    @Test
    void mapBoundsContainsTheWarpedBackdropCorners() {
        RenderWarp warp = RenderWarp.corners(
                10.0f, 20.0f, 100.0f, 40.0f,
                -5.0f, 2.0f,
                7.0f, -3.0f,
                9.0f, 4.0f,
                -4.0f, 6.0f
        );
        double[] bounds = new double[4];

        warp.mapBounds(10.0, 20.0, 100.0, 40.0, bounds);

        assertArrayEquals(new double[]{5.0, 17.0, 114.0, 49.0}, bounds, 0.0001);
    }

    @Test
    void identityKeepsBoundsUnchanged() {
        double[] bounds = new double[4];

        RenderWarp.IDENTITY.mapBounds(3.0, 4.0, 50.0, 20.0, bounds);

        assertArrayEquals(new double[]{3.0, 4.0, 50.0, 20.0}, bounds, 0.0);
    }
}
