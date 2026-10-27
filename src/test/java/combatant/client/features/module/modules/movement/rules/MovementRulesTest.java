/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementRulesTest {

    @Test
    void boostStartsAtPeakAndEndsAtOne() {
        assertEquals(2.0, MovementRules.boostFactor(0, 10, 2.0, 1.0), 1e-9);
        assertEquals(1.0, MovementRules.boostFactor(10, 10, 2.0, 1.0), 1e-9);
        assertEquals(1.0, MovementRules.boostFactor(99, 10, 2.0, 1.0), 1e-9);
    }

    @Test
    void linearBoostHalfwayIsHalfTheExtra() {
        assertEquals(1.5, MovementRules.boostFactor(5, 10, 2.0, 1.0), 1e-9);
    }

    @Test
    void boostNeverIncreasesAndHigherFalloffDropsFaster() {
        double prev = Double.MAX_VALUE;
        for (int t = 0; t <= 12; t++) {
            double f = MovementRules.boostFactor(t, 12, 1.8, 1.0);
            assertTrue(f <= prev);
            prev = f;
        }
        assertTrue(MovementRules.boostFactor(3, 12, 1.8, 2.5) < MovementRules.boostFactor(3, 12, 1.8, 1.0));
    }

    @Test
    void boostDegenerateInputsAreNeutral() {
        assertEquals(1.0, MovementRules.boostFactor(0, 0, 3.0, 1.0), 1e-9);
        assertEquals(1.0, MovementRules.boostFactor(0, 10, 0.5, 1.0), 1e-9, "peak below 1 never slows the player");
        assertEquals(2.0, MovementRules.boostFactor(-4, 10, 2.0, 1.0), 1e-9);
    }

    @Test
    void boostSpeedFloorsTheLaunchSpeed() {
        assertEquals(0.6, MovementRules.boostSpeed(0.0, 0.3, 0, 8, 2.0, 1.0), 1e-9);
        assertEquals(0.8, MovementRules.boostSpeed(0.4, 0.3, 0, 8, 2.0, 1.0), 1e-9);
        assertEquals(0.4, MovementRules.boostSpeed(0.4, 0.3, 8, 8, 2.0, 1.0), 1e-9);
    }

    @Test
    void edgeJumpOnlyAtALedgeWhileWalking() {
        assertTrue(MovementRules.edgeJump(true, false, false, true, false, 0.0));
        assertFalse(MovementRules.edgeJump(true, false, false, true, true, 0.0), "ground under the feet");
        assertFalse(MovementRules.edgeJump(false, false, false, true, false, 0.0), "airborne");
        assertFalse(MovementRules.edgeJump(true, true, false, true, false, 0.0), "sneaking stays on the edge");
        assertFalse(MovementRules.edgeJump(true, false, true, true, false, 0.0), "jump already held");
        assertFalse(MovementRules.edgeJump(true, false, false, false, false, 0.0), "standing still");
        assertFalse(MovementRules.edgeJump(true, false, false, true, false, 0.2), "already rising");
    }

    @Test
    void autoJumpNeedsGroundAndWalking() {
        assertTrue(MovementRules.autoJump(true, true, true, false));
        assertFalse(MovementRules.autoJump(false, true, true, false));
        assertFalse(MovementRules.autoJump(true, false, true, false));
        assertFalse(MovementRules.autoJump(true, true, false, false));
        assertFalse(MovementRules.autoJump(true, true, true, true));
    }

    @Test
    void spiderClimbsOnlyAgainstAWall() {
        assertEquals(0.2, MovementRules.spiderVelocity(true, true, false, 0.2, -0.5), 1e-9);
        assertEquals(-0.5, MovementRules.spiderVelocity(false, true, false, 0.2, -0.5), 1e-9);
        assertEquals(-0.5, MovementRules.spiderVelocity(true, false, false, 0.2, -0.5), 1e-9);
        assertEquals(-0.5, MovementRules.spiderVelocity(true, true, true, 0.2, -0.5), 1e-9);
        assertEquals(0.42, MovementRules.spiderVelocity(true, true, false, 0.2, 0.42), 1e-9,
                "an existing stronger upward velocity is kept");
    }

    @Test
    void spiderJumpOnlyFromTheGround() {
        assertTrue(MovementRules.spiderJump(true, true, false, true));
        assertFalse(MovementRules.spiderJump(true, true, false, false));
        assertFalse(MovementRules.spiderJump(false, true, false, true));
    }

    @Test
    void webReductionInterpolatesBetweenVanillaAndFree() {
        assertEquals(0.25, MovementRules.reducedStuckMultiplier(0.25, 0.0), 1e-9);
        assertEquals(1.0, MovementRules.reducedStuckMultiplier(0.25, 1.0), 1e-9);
        assertEquals(0.625, MovementRules.reducedStuckMultiplier(0.25, 0.5), 1e-9);
        assertEquals(1.0, MovementRules.reducedStuckMultiplier(0.25, 7.0), 1e-9);
        assertEquals(0.25, MovementRules.reducedStuckMultiplier(0.25, -1.0), 1e-9);
    }
}
