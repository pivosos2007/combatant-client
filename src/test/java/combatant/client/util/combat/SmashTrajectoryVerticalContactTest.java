/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmashTrajectoryVerticalContactTest {
    @Test
    void stationaryVerticalDescentCanReachTargetWithoutHorizontalOverlap() {
        assertTrue(SmashTrajectory.withinVerticalContactRange(0.8, 4.5, 3.0));
        assertTrue(SmashTrajectory.withinVerticalContactRange(2.6, 4.5, 3.0));
    }

    @Test
    void verticalDescentCannotHitTargetOutsideReach() {
        assertFalse(SmashTrajectory.withinVerticalContactRange(3.2, 4.5, 3.0));
        assertFalse(SmashTrajectory.withinVerticalContactRange(2.8, 4.5, 3.0));
        assertFalse(SmashTrajectory.withinVerticalContactRange(2.0, 1.5, 3.0));
    }
}
