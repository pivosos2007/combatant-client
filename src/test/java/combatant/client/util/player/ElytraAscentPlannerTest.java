/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.player;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElytraAscentPlannerTest {
    private static final AABB PLAYER = new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3);
    private static final Vec3 UP = new Vec3(0, 6, 0);

    @Test
    void openColumnDoesNotDetour() {
        var plan = ElytraAscentPlanner.plan(new Roof(0.0, false), PLAYER,
                new Vec3(1, 0, 0), 6, UP);
        assertTrue(plan.verticalClear());
        assertNull(plan.departure());
    }

    @Test
    void lowRoofRequiresSidewaysExitBeforeClimbing() {
        Roof roof = new Roof(2.2, false);
        var plan = ElytraAscentPlanner.plan(roof, PLAYER, new Vec3(1, 0, 0), 6, UP);
        assertFalse(plan.verticalClear());
        assertNotNull(plan.departure());
        assertTrue(ElytraAscentPlanner.clear(roof, PLAYER, plan.departure()));
        assertTrue(ElytraAscentPlanner.clear(roof, PLAYER.move(plan.departure()), UP));
    }

    @Test
    void enclosedCeilingHasNoSafeDeparture() {
        var plan = ElytraAscentPlanner.plan(new Roof(2.2, true), PLAYER,
                new Vec3(1, 0, 0), 6, UP);
        assertFalse(plan.verticalClear());
        assertNull(plan.departure());
    }

    @Test
    void diagonalWallAlsoTriggersDetourWithOpenSky() {
        ElytraEscapePlanner.CollisionWorld wall = new ElytraEscapePlanner.CollisionWorld() {
            @Override public boolean loaded(AABB bounds, Vec3 movement) { return true; }
            @Override public Vec3 move(AABB bounds, Vec3 movement) {
                if (movement.x > 0.0 && bounds.maxX < 1.5 && bounds.maxX + movement.x > 1.5) {
                    return new Vec3(1.5 - bounds.maxX, movement.y, movement.z);
                }
                return movement;
            }
        };
        var plan = ElytraAscentPlanner.plan(wall, PLAYER, new Vec3(1, 0, 0),
                6, new Vec3(4, 5, 0));
        assertFalse(plan.verticalClear());
        assertNotNull(plan.departure());
    }

    private record Roof(double radius, boolean enclosed) implements ElytraEscapePlanner.CollisionWorld {
        @Override public boolean loaded(AABB bounds, Vec3 movement) { return true; }
        @Override public Vec3 move(AABB bounds, Vec3 movement) {
            if (radius <= 0 || movement.y <= 0.0) return movement;
            boolean underneath = enclosed || bounds.minX < radius && bounds.maxX > -radius
                    && bounds.minZ < radius && bounds.maxZ > -radius;
            if (!underneath) return movement;
            return new Vec3(movement.x,
                    Math.min(movement.y, Math.max(0.0, 3.0 - bounds.maxY)), movement.z);
        }
    }
}
