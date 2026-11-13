package combatant.client.util.combat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmashTrajectoryTest {
    private static final AABB TARGET = new AABB(-0.3, 0, -0.3, 0.3, 1.8, 0.3);

    private boolean reaches(Vec3 feet, Vec3 velocity, Vec3 targetVelocity, int ticks, double fall) {
        return SmashTrajectory.canReach(feet, 1.62, 0.3, velocity, TARGET, targetVelocity,
                0, fall, 0.08, 0.25, ticks, 4.5, 3, 0.08);
    }

    @Test
    void recordedChaseCanPrepareBeforeEnteringAuraReach() {
        // combatant-elytra-dive.log, tick 2387: CHASE, reach 6, lead 20; the old phase gate skipped this forecast.
        Vec3 feet = new Vec3(1120.3586, 66.4754, -283.7575);
        Vec3 velocity = new Vec3(0.1597, -1.1517, 1.2468);
        AABB target = new AABB(1122.042734238472, 59, -279.7399345378253,
                1123.2427342861556, 59.400000005960464, -278.5399344901416);
        Vec3 targetVelocity = new Vec3(0, -0.0768, 0.0054);
        assertTrue(feet.add(0, 0.4, 0).distanceToSqr(
                1122.0427, 59.4, -279.7399) > 6 * 6);
        assertTrue(SmashTrajectory.canReach(feet, 1.62, 0.3, velocity, target, targetVelocity,
                0, 1.6, 0.01, 0.25, 8, 4.5, 6, 0.08));
        assertFalse(SmashTrajectory.canReach(feet, 1.62, 0.3, velocity, target, targetVelocity,
                0, 1.6, 0.01, 0.25, 2, 4.5, 6, 0.08));
        assertTrue(SmashTrajectory.canReach(feet, 1.62, 0.3, velocity, target, targetVelocity,
                0, 1.6, 0.01, 0.25, 4, 4.5, 6, 0.08));
        assertFalse(SmashTrajectory.canReach(feet, 1.62, 0.3, velocity, target, targetVelocity,
                0, 1.6, 0.01, 0.25, 4, 4.5, 3, 0.08));
    }

    @Test
    void preparationLeadFindsContactBeyondTheBaseHorizon() {
        Vec3 feet = new Vec3(0, 12, 0);
        Vec3 velocity = new Vec3(0, -0.4, 0);
        assertFalse(reaches(feet, velocity, Vec3.ZERO, 8, 2));
        assertTrue(reaches(feet, velocity, Vec3.ZERO, 16, 2));
        assertFalse(reaches(feet, velocity, Vec3.ZERO, 16, 30));
    }

    @Test
    void verticalDiveAndTinyHorizontalDriftDoNotNeedApproachSpeed() {
        assertTrue(reaches(new Vec3(0, 6, 0), new Vec3(0, -0.8, 0), Vec3.ZERO, 8, 2));
        assertTrue(reaches(new Vec3(0, 6, 0), new Vec3(0.01, -0.8, 0), Vec3.ZERO, 8, 2));
    }

    @Test
    void horizontalClosestPointAloneDoesNotMakeAReachableSmash() {
        assertFalse(reaches(new Vec3(0, 30, 0), new Vec3(0, -0.1, 0), Vec3.ZERO, 8, 2));
        assertFalse(reaches(new Vec3(8, 6, 0), new Vec3(0, -0.8, 0), Vec3.ZERO, 8, 2));
    }

    @Test
    void targetHorizontalEscapeAndVerticalDescentChangeContactTime() {
        assertFalse(reaches(new Vec3(0, 6, 0), new Vec3(0, -0.8, 0), new Vec3(2, 0, 0), 8, 2));
        assertFalse(reaches(new Vec3(0, 6, 0), new Vec3(0, -0.8, 0), new Vec3(0, -2, 0), 8, 2));
        assertTrue(reaches(new Vec3(0, 6, 0), new Vec3(0, -0.8, 0), new Vec3(0, 0.3, 0), 8, 2));
    }

    @Test
    void configuredFallAndPredictionHorizonRemainConstraints() {
        assertFalse(reaches(new Vec3(0, 6, 0), new Vec3(0, -0.8, 0), Vec3.ZERO, 1, 2));
        assertFalse(reaches(new Vec3(0, 6, 0), new Vec3(0, -0.8, 0), Vec3.ZERO, 8, 20));
        assertFalse(reaches(new Vec3(0, 6, 0), new Vec3(0, -0.01, 0), Vec3.ZERO, 8, 2));
    }

    @Test
    void offsetApproachStillRequiresTheConfiguredClosingSpeed() {
        assertTrue(reaches(new Vec3(4, 6, 0), new Vec3(-0.6, -0.8, 0), Vec3.ZERO, 8, 2));
        assertFalse(reaches(new Vec3(4, 6, 0), new Vec3(-0.1, -0.8, 0), Vec3.ZERO, 8, 2));
        assertFalse(SmashTrajectory.canReach(new Vec3(4, 6, 0), 1.62, 0.3,
                new Vec3(-0.3, -0.8, 0), TARGET, Vec3.ZERO, 0, 2, 0.08, 0.25, 8, 0.1, 3, 0.08));
    }
}
