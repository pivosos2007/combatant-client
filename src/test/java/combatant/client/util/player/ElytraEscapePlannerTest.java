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
    private static final Rotation EAST = new Rotation(-90, 0, true);
    private static final AABB START = new AABB(0, 2, 0, 0.6, 3.8, 0.6);

    @Test
    void highSpeedWallImpactIsDetectedBeyondFirstMovement() {
        FlightModel physics = coast();
        var path = ElytraEscapePlanner.forecast(new Planes(4.0, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY), START, new Vec3(2.4, 0, 0), EAST, 4, physics);
        assertTrue(path.damage() > 0.0f);
        assertFalse(path.safe());
        assertTrue(path.firstCollisionTick() > 0);
    }

    @Test
    void landingCannotBeClassifiedAsClearEscape() {
        var path = ElytraEscapePlanner.forecast(new Planes(Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY, 0.0), new AABB(0, 0.3, 0, 0.6, 2.1, 0.6),
                new Vec3(0, -1, 0), EAST, 3, coast());
        assertFalse(path.safe());
        assertTrue(path.blockedSteering());
        assertEquals(1, path.firstCollisionTick());
    }

    @Test
    void uncertainRocketTickOrderMustIncludeImmediateAcceleration() {
        FlightModel boosted = new FlightModel(0.0, false, 0, 3,
                (velocity, heading) -> new Vec3(4, 0, 0));
        var path = ElytraEscapePlanner.forecast(new Planes(3.0, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY), START, new Vec3(0.2, 0, 0), EAST, 3, boosted);
        assertTrue(path.damage() > 0.0f);
        assertEquals(1, path.firstCollisionTick());
    }

    @Test
    void highSpeedNearMissDoesNotCountAsClearCorridor() {
        var path = ElytraEscapePlanner.forecast(new Planes(2.7, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY), START, new Vec3(2, 0, 0), EAST, 1, coast());
        assertEquals(0, (int) path.damage());
        assertTrue(path.blockedSteering());
        assertFalse(path.safe());
    }

    @Test
    void unknownClearanceSweepCannotBeCalledSafe() {
        ElytraEscapePlanner.CollisionWorld world = new ElytraEscapePlanner.CollisionWorld() {
            @Override
            public boolean loaded(AABB bounds, Vec3 movement) {
                return bounds.minX >= -0.1;
            }

            @Override
            public Vec3 move(AABB bounds, Vec3 movement) {
                return movement;
            }
        };
        var path = ElytraEscapePlanner.forecast(world, START, new Vec3(2, 0, 0), EAST, 1, coast());
        assertTrue(path.unknown());
        assertFalse(path.safe());
    }

    @Test
    void escapeMustImproveWholePathRatherThanOnlyFirstTick() {
        var world = new Planes(7.5, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        Vec3 velocity = new Vec3(1.3, 0, 0);
        FlightModel physics = coast();
        var baseline = ElytraEscapePlanner.forecast(world, START, velocity, EAST, 8, physics);
        assertFalse(baseline.safe());
        var escape = ElytraEscapePlanner.findSaferEscape(world, START, velocity, EAST,
                null, 8, physics, baseline, Rotation::normalize);
        assertNotNull(escape);
        assertTrue(escape.trajectory().safe());
    }

    @Test
    void repeatedPoweredImpactsAccumulateDamage() {
        var world = new Planes(2.0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        FlightModel boosted = new FlightModel(0.0, false, 3, 3,
                (velocity, heading) -> new Vec3(4, 0, 0));
        float singleImpact = ElytraEscapePlanner.forecast(world, START, new Vec3(4, 0, 0),
                EAST, 1, coast()).damage();
        float repeated = ElytraEscapePlanner.forecast(world, START, new Vec3(4, 0, 0),
                EAST, 3, boosted).damage();
        assertTrue(singleImpact > 0.0f);
        assertTrue(repeated > singleImpact);
    }

    @Test
    void groundSkimmingDoesNotRedirectFromAnApproach() {
        var world = new Planes(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0);
        var bounds = new AABB(0, 0.08, 0, 0.6, 1.88, 0.6);
        var path = ElytraEscapePlanner.forecast(world, bounds, new Vec3(0.9, -0.02, 0), EAST, 4, coast());
        assertTrue(path.safe());
        assertFalse(path.blockedSteering());
    }

    @Test
    void gentleTouchdownIsNotAWallCrash() {
        var world = new Planes(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0);
        var bounds = new AABB(0, 0.09, 0, 0.6, 1.89, 0.6);
        var path = ElytraEscapePlanner.forecast(world, bounds, new Vec3(1.1, -0.16, 0), EAST, 5, coast());
        assertTrue(path.safe());
        assertEquals(0, path.firstCollisionTick());
    }

    @Test
    void highSpeedWallContactStillRequiresEscapeNearGround() {
        var world = new Planes(2.8, Double.POSITIVE_INFINITY, 0.0);
        var bounds = new AABB(0, 0.10, 0, 0.6, 1.90, 0.6);
        var path = ElytraEscapePlanner.forecast(world, bounds, new Vec3(2.0, -0.03, 0), EAST, 3, coast());
        assertFalse(path.safe());
        assertTrue(path.firstCollisionTick() > 0);
    }

    @Test
    void fastDescentAtBoostSpeedRequiresAnEscape() {
        var world = new Planes(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0);
        var bounds = new AABB(0, 0.25, 0, 0.6, 2.05, 0.6);
        var path = ElytraEscapePlanner.forecast(world, bounds, new Vec3(2.4, -0.28, 0), EAST, 4, coast());
        assertFalse(path.safe());
        assertTrue(path.blockedSteering());
        assertTrue(path.firstCollisionTick() > 0);
    }

    @Test
    void descendingAtSpeedTriggersGroundClearanceBeforeCollision() {
        var world = new Planes(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0);
        var bounds = new AABB(0, 0.20, 0, 0.6, 2.0, 0.6);
        var path = ElytraEscapePlanner.forecast(world, bounds, new Vec3(2.5, -0.20, 0), EAST, 1, coast());
        assertTrue(path.blockedSteering());
        assertEquals(1, path.firstCollisionTick());
        assertEquals(0, (int) path.damage());
    }

    @Test
    void shallowDescentBecomesUnsafeWithVeryHighHorizontalSpeed() {
        var world = new Planes(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0);
        var bounds = new AABB(0, 0.20, 0, 0.6, 2.0, 0.6);
        var path = ElytraEscapePlanner.forecast(world, bounds, new Vec3(4.2, -0.12, 0), EAST, 1, coast());
        assertTrue(path.blockedSteering());
        assertEquals(1, path.firstCollisionTick());
    }

    @Test
    void slowGroundSkimStillPermitsTargetApproach() {
        var world = new Planes(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0);
        var bounds = new AABB(0, 0.03, 0, 0.6, 1.83, 0.6);
        var path = ElytraEscapePlanner.forecast(world, bounds, new Vec3(1.0, -0.02, 0), EAST, 3, coast());
        assertTrue(path.safe());
    }

    @Test
    void steepLandingWithoutHorizontalSpeedRemainsUnsafe() {
        var world = new Planes(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0);
        var bounds = new AABB(0, 0.2, 0, 0.6, 2.0, 0.6);
        var path = ElytraEscapePlanner.forecast(world, bounds, new Vec3(0.1, -1.0, 0), EAST, 2, coast());
        assertFalse(path.safe());
        assertTrue(path.blockedSteering());
    }

    @Test
    void fastFlatFlightJustAboveGroundIsNotAutomaticallyUnsafe() {
        var world = new Planes(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0);
        var bounds = new AABB(0, 0.035, 0, 0.6, 1.835, 0.6);
        var path = ElytraEscapePlanner.forecast(world, bounds, new Vec3(3.0, -0.008, 0), EAST, 2, coast());
        assertTrue(path.safe());
    }

    @Test
    void rapidClimbDetectsCeilingWithinVerticalClearance() {
        ElytraEscapePlanner.CollisionWorld ceiling = new ElytraEscapePlanner.CollisionWorld() {
            @Override
            public boolean loaded(AABB bounds, Vec3 movement) {
                return true;
            }

            @Override
            public Vec3 move(AABB bounds, Vec3 movement) {
                return new Vec3(movement.x,
                        movement.y > 0 ? Math.min(movement.y, Math.max(0.0, 2.0 - bounds.maxY)) : movement.y,
                        movement.z);
            }
        };
        var bounds = new AABB(0, 0, 0, 0.6, 1.6, 0.6);
        var path = ElytraEscapePlanner.forecast(ceiling, bounds, new Vec3(1.0, 0.36, 0), EAST, 1, coast());
        assertTrue(path.blockedSteering());
        assertEquals(1, path.firstCollisionTick());
    }

    @Test
    void waterHazardIsUnsafeEvenWithoutBlockCollision() {
        ElytraEscapePlanner.CollisionWorld water = new ElytraEscapePlanner.CollisionWorld() {
            @Override
            public boolean loaded(AABB bounds, Vec3 movement) {
                return true;
            }

            @Override
            public Vec3 move(AABB bounds, Vec3 movement) {
                return movement;
            }

            @Override
            public double waterRisk(AABB bounds, Vec3 movement) {
                return Math.max(0.0, 4.0 - bounds.move(movement).minY);
            }
        };
        var low = ElytraEscapePlanner.forecast(water, START, new Vec3(1.0, -0.1, 0), EAST, 2, coast());
        assertFalse(low.safe());
        assertEquals(0, low.firstCollisionTick());
        assertTrue(low.waterHazardTick() > 0);
        assertTrue(low.waterRisk() > 0.0);

        AABB high = START.move(0.0, 15.0, 0.0);
        var highFlight = ElytraEscapePlanner.forecast(water, high, new Vec3(1.0, -0.1, 0), EAST, 2, coast());
        assertTrue(highFlight.safe());
        assertEquals(0, highFlight.waterHazardTick());
    }

    @Test
    void lavaHazardIsUnsafeWithoutABlockCollision() {
        ElytraEscapePlanner.CollisionWorld lava = new ElytraEscapePlanner.CollisionWorld() {
            @Override
            public boolean loaded(AABB bounds, Vec3 movement) {
                return true;
            }

            @Override
            public Vec3 move(AABB bounds, Vec3 movement) {
                return movement;
            }

            @Override
            public double lavaRisk(AABB bounds, Vec3 movement) {
                return Math.max(0.0, 5.0 - bounds.move(movement).minY);
            }
        };
        var low = ElytraEscapePlanner.forecast(lava, START, new Vec3(1.0, -0.1, 0), EAST, 2, coast());
        assertFalse(low.safe());
        assertEquals(0, low.firstCollisionTick());
        assertEquals(0, low.waterHazardTick());
        assertTrue(low.lavaHazardTick() > 0);
        assertTrue(low.lavaRisk() > 0.0);

        var high = ElytraEscapePlanner.forecast(lava, START.move(0, 15, 0),
                new Vec3(1.0, -0.1, 0), EAST, 2, coast());
        assertTrue(high.safe());
        assertEquals(0, high.lavaHazardTick());
    }

    @Test
    void fluidFloorClearanceDependsOnDepthNotFireResistance() {
        assertEquals(4.75, ElytraEscapePlanner.fluidFloorClearance(1.0), 1.0E-6);
        assertEquals(4.75, ElytraEscapePlanner.fluidFloorClearance(4.0), 1.0E-6);
        assertEquals(3.0, ElytraEscapePlanner.fluidFloorClearance(5.0), 1.0E-6);
        assertEquals(3.0, ElytraEscapePlanner.fluidFloorClearance(20.0), 1.0E-6);
    }

    @Test
    void fireResistanceRemovesIgnitionRiskButNotLiquidFloorRisk() {
        double noFloor = Double.NEGATIVE_INFINITY;
        assertEquals(0.0, ElytraEscapePlanner.lavaRiskAtFeet(65.0, noFloor, 68.0, false), 1.0E-6);
        assertEquals(3.0, ElytraEscapePlanner.lavaRiskAtFeet(65.0, noFloor, 68.0, true), 1.0E-6);
        assertEquals(2.0, ElytraEscapePlanner.lavaRiskAtFeet(65.0, 67.0, 68.0, false), 1.0E-6);
        assertEquals(3.0, ElytraEscapePlanner.lavaRiskAtFeet(65.0, 67.0, 68.0, true), 1.0E-6);
    }

    private static FlightModel coast() {
        return new FlightModel(0.0, false, 0, 0, ElytraFlightPhysics::rocket);
    }

    private record Planes(double maxX, double maxZ, double groundY)
            implements ElytraEscapePlanner.CollisionWorld {
        @Override
        public boolean loaded(AABB bounds, Vec3 movement) {
            return true;
        }

        @Override
        public Vec3 move(AABB bounds, Vec3 movement) {
            double x = movement.x > 0.0 ? Math.min(movement.x, Math.max(0.0, maxX - bounds.maxX)) : movement.x;
            double z = movement.z > 0.0 ? Math.min(movement.z, Math.max(0.0, maxZ - bounds.maxZ)) : movement.z;
            double y = movement.y < 0.0 ? Math.max(movement.y, Math.min(0.0, groundY - bounds.minY)) : movement.y;
            return new Vec3(x, y, z);
        }
    }
}
