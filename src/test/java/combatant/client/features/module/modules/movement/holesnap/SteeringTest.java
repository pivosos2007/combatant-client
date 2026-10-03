/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement.holesnap;

import org.junit.jupiter.api.Test;

import static combatant.client.features.module.modules.movement.holesnap.Steering.BACKWARD;
import static combatant.client.features.module.modules.movement.holesnap.Steering.FORWARD;
import static combatant.client.features.module.modules.movement.holesnap.Steering.LEFT;
import static combatant.client.features.module.modules.movement.holesnap.Steering.RIGHT;
import static combatant.client.features.module.modules.movement.holesnap.Steering.keysToward;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SteeringTest {

    @Test
    void facingSouthWalksTowardTheTargetOnEachAxis() {
        // Yaw 0 faces +Z; the player's left is +X.
        assertEquals(FORWARD, keysToward(0f, 0, 5));
        assertEquals(BACKWARD, keysToward(0f, 0, -5));
        assertEquals(LEFT, keysToward(0f, 5, 0));
        assertEquals(RIGHT, keysToward(0f, -5, 0));
    }

    @Test
    void facingWestRotatesTheKeys() {
        // Yaw 90 faces -X; the player's left is +Z.
        assertEquals(FORWARD, keysToward(90f, -5, 0));
        assertEquals(LEFT, keysToward(90f, 0, 5));
        assertEquals(RIGHT, keysToward(90f, 0, -5));
        assertEquals(BACKWARD, keysToward(90f, 5, 0));
    }

    @Test
    void diagonalTargetsPressTwoKeys() {
        assertEquals(FORWARD | LEFT, keysToward(0f, 3, 3));
        assertEquals(FORWARD | RIGHT, keysToward(0f, -3, 3));
        assertEquals(BACKWARD | LEFT, keysToward(0f, 3, -3));
        assertEquals(BACKWARD | RIGHT, keysToward(0f, -3, -3));
    }

    @Test
    void smallAnglesOffAnAxisStayOnOneKey() {
        // 20 degrees off straight ahead is inside the 22.5 degree cone.
        double dx = Math.sin(Math.toRadians(20)) * 10;
        double dz = Math.cos(Math.toRadians(20)) * 10;
        assertEquals(FORWARD, keysToward(0f, dx, dz));
    }

    @Test
    void largerAnglesAddTheSideKey() {
        double dx = Math.sin(Math.toRadians(25)) * 10;
        double dz = Math.cos(Math.toRadians(25)) * 10;
        assertEquals(FORWARD | LEFT, keysToward(0f, dx, dz));
    }

    @Test
    void yawWrapsAroundLikeMinecraftYaw() {
        assertEquals(keysToward(0f, 4, 1), keysToward(360f, 4, 1));
        assertEquals(keysToward(90f, 4, 1), keysToward(-270f, 4, 1));
        assertEquals(keysToward(180f, -2, 5), keysToward(-180f, -2, 5));
    }

    @Test
    void standingOnTheTargetPressesNothing() {
        assertEquals(0, keysToward(37f, 0, 0));
        assertEquals(0, keysToward(37f, 1.0e-9, -1.0e-9));
    }
}
