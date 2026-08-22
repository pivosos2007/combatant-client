/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RenderPassCompatibilityTest {
    private final Object color = new Object();
    private final Object depth = new Object();

    @Test
    void keepsPassOpenForStateAndResourceChanges() {
        assertTrue(RenderPassCompatibility.canContinue(color, depth, color, depth, false, false));
    }

    @Test
    void targetAndDepthTransitionsAreHardBarriers() {
        assertFalse(RenderPassCompatibility.canContinue(color, depth, new Object(), depth, false, false));
        assertFalse(RenderPassCompatibility.canContinue(color, depth, color, new Object(), false, false));
    }

    @Test
    void clearsAreHardBarriers() {
        assertFalse(RenderPassCompatibility.canContinue(color, depth, color, depth, true, false));
        assertFalse(RenderPassCompatibility.canContinue(color, depth, color, depth, false, true));
    }
}
