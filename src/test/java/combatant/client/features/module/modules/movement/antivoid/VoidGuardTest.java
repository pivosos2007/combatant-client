/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement.antivoid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoidGuardTest {

    private static final int FLOOR = -64;

    @Test
    void bandStartsStrictlyBelowFloorPlusDistance() {
        assertTrue(VoidGuard.inTriggerBand(-61.01, FLOOR, 3));
        assertFalse(VoidGuard.inTriggerBand(-61.0, FLOOR, 3), "exactly on the line is still safe");
        assertFalse(VoidGuard.inTriggerBand(-30.0, FLOOR, 3));
    }

    @Test
    void fallingInsideTheBandOverTheVoidTriggers() {
        assertTrue(VoidGuard.shouldAct(-62.0, -0.8, FLOOR, 4, false, true, true, 0));
    }

    @Test
    void risingOrHoveringNeverTriggers() {
        assertFalse(VoidGuard.shouldAct(-62.0, 0.0, FLOOR, 4, false, true, true, 0));
        assertFalse(VoidGuard.shouldAct(-62.0, 0.4, FLOOR, 4, false, true, true, 0));
    }

    @Test
    void aboveTheBandNeverTriggers() {
        assertFalse(VoidGuard.shouldAct(-50.0, -1.5, FLOOR, 4, false, true, true, 0));
    }

    @Test
    void cooldownBlocksRepeatRescues() {
        assertFalse(VoidGuard.shouldAct(-62.0, -0.8, FLOOR, 4, false, true, true, 1));
    }

    @Test
    void elytraFlightIsIgnoredOnlyWhenTheOptionSaysSo() {
        assertFalse(VoidGuard.shouldAct(-62.0, -0.8, FLOOR, 4, true, true, true, 0));
        assertTrue(VoidGuard.shouldAct(-62.0, -0.8, FLOOR, 4, false, true, true, 0));
    }

    @Test
    void solidGroundBelowOnlyBlocksTheRescueWhenOverVoidIsRequired() {
        // Mining at bedrock level: a short drop inside the band, with floor underneath.
        assertFalse(VoidGuard.shouldAct(-62.0, -0.3, FLOOR, 4, false, false, true, 0));
        assertTrue(VoidGuard.shouldAct(-62.0, -0.3, FLOOR, 4, false, false, false, 0));
    }
}
