/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.player;

import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.player.ElytraFlightPhysics.FlightModel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElytraEscapePlannerTest {
    private static final Rotation EAST = Rotation.fromRotationVec(new Vec3(1, 0, 0));
    private static final Rotation WEST = Rotation.fromRotationVec(new Vec3(-1, 0, 0));
    private static final FlightModel GLIDE = new FlightModel(0, false, 0, 0, (velocity, rotation) -> velocity);
    private static final AABB PLAYER = new AABB(0, 65, 0, 0.6, 66.8, 0.6);

    @Test
    void actualCollisionIsNotSafeWhenLookPointsAwayFromWall() {
        ElytraEscapePlanner.CollisionWorld wall = new ElytraEscapePlanner.CollisionWorld() {
            @Override
            public boolean loaded(AABB bounds, Vec3 move) { return true; }

            @Override
            public Vec3 move(AABB bounds, Vec3 move) {
                return new Vec3(Math.min(0.0, move.x), move.y, move.z);
            }
        };
        var result = ElytraEscapePlanner.forecast(wall, PLAYER, new Vec3(0.25, 0, 0), WEST, 1, GLIDE);
        assertTrue(result.blockedSteering());
        assertFalse(result.safe());
        assertEquals(0.0f, result.damage());
    }

    @Test
    void escapeCanAvoidSlowCollisionWithZeroProjectedDamage() {
        ElytraEscapePlanner.CollisionWorld wall = new ElytraEscapePlanner.CollisionWorld() {
            @Override
            public boolean loaded(AABB bounds, Vec3 move) { return true; }

            @Override
            public Vec3 move(AABB bounds, Vec3 move) {
                return new Vec3(Math.min(move.x, 3.0 - bounds.maxX), move.y, move.z);
            }
        };
        Vec3 speed = new Vec3(0.25, 0, 0);
        var incoming = ElytraEscapePlanner.forecast(wall, PLAYER, speed, EAST, 32, GLIDE);
        assertFalse(incoming.safe());
        assertTrue(incoming.blockedSteering());
        assertEquals(0.0f, incoming.damage());

        var alternative = ElytraEscapePlanner.findSaferEscape(
                wall, PLAYER, speed, EAST, null, 32, GLIDE, incoming, rotation -> rotation);
        assertNotNull(alternative);
        assertTrue(alternative.trajectory().safe());
    }

    @Test
    void unblockedGlideDoesNotRequestEmergencyTurn() {
        ElytraEscapePlanner.CollisionWorld empty = new ElytraEscapePlanner.CollisionWorld() {
            @Override
            public boolean loaded(AABB bounds, Vec3 move) { return true; }

            @Override
            public Vec3 move(AABB bounds, Vec3 move) { return move; }
        };
        Vec3 speed = new Vec3(0.25, 0, 0);
        var path = ElytraEscapePlanner.forecast(empty, PLAYER, speed, EAST, 32, GLIDE);
        assertTrue(path.safe());
        assertNull(ElytraEscapePlanner.findSaferEscape(
                empty, PLAYER, speed, EAST, null, 32, GLIDE, path, rotation -> rotation));
    }
}
