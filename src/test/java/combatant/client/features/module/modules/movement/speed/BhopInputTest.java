/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement.speed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BhopInputTest {

    @Test
    void walkingForwardOnTheGroundHopsAndSprints() {
        assertEquals(new BhopInput(true, true), BhopInput.decide(true, true, true, false, false, false));
    }

    @Test
    void strafingAloneHopsWithoutForcingSprint() {
        assertEquals(new BhopInput(true, false), BhopInput.decide(true, true, false, false, true, false));
    }

    @Test
    void backwardsHopsButNeverSprints() {
        assertEquals(new BhopInput(true, false), BhopInput.decide(true, true, false, true, false, false));
    }

    @Test
    void oppositeKeysCancelOut() {
        assertEquals(BhopInput.NONE, BhopInput.decide(true, true, true, true, false, false));
        assertEquals(BhopInput.NONE, BhopInput.decide(true, true, false, false, true, true));
        assertEquals(new BhopInput(true, false), BhopInput.decide(true, true, true, true, true, false),
                "forward and back cancel, the strafe key still moves the player");
    }

    @Test
    void standingStillAsksForNothing() {
        assertEquals(BhopInput.NONE, BhopInput.decide(true, true, false, false, false, false));
    }

    @Test
    void neverHopsInTheAir() {
        assertEquals(BhopInput.NONE, BhopInput.decide(true, false, true, false, false, false));
    }

    @Test
    void neverHopsWhereTheModuleIsNotAllowed() {
        assertEquals(BhopInput.NONE, BhopInput.decide(false, true, true, false, false, false));
    }
}
