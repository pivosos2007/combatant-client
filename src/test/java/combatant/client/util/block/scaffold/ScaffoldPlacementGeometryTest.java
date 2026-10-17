/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.scaffold;

import combatant.client.util.aiming.data.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scene is the one the Grim harness plays: a stone platform whose last block (0,-51,1) ends at z=2, the player
 * walking +z at 0.22 blocks per tick with the eye 1.62 above his feet, which stand on the platform's top at y=-50.
 */
final class ScaffoldPlacementGeometryTest {
    private static final BlockPos SUPPORT = new BlockPos(0, -51, 1);
    private static final AABB SHAPE = new AABB(SUPPORT);
    private static final double EYE_HEIGHT = 1.62;
    private static final double STEP = 0.22;

    private static Vec3 eyeAt(double z) {
        return new Vec3(0.5, -50.0 + EYE_HEIGHT, z);
    }

    @Test
    void outwardDistanceIsPositiveOnlyOnTheOuterSideOfTheFace() {
        assertEquals(0.3, ScaffoldPlacementGeometry.outwardDistance(eyeAt(2.3), Direction.SOUTH, SHAPE), 1.0E-9);
        assertEquals(-0.5, ScaffoldPlacementGeometry.outwardDistance(eyeAt(1.5), Direction.SOUTH, SHAPE), 1.0E-9);
        assertEquals(1.62, ScaffoldPlacementGeometry.outwardDistance(eyeAt(1.5), Direction.UP, SHAPE), 1.0E-9);
        assertEquals(0.5, ScaffoldPlacementGeometry.outwardDistance(new Vec3(-0.5, -48.0, 1.5), Direction.WEST, SHAPE), 1.0E-9);
    }

    @Test
    void aFaceTheEyeCannotSeeIsNeverSafeWhateverTheRotation() {
        Rotation lookingBack = new Rotation(180.0f, 70.0f, false);

        assertFalse(ScaffoldPlacementGeometry.isSafe(eyeAt(1.9), eyeAt(2.1), lookingBack, SUPPORT, Direction.SOUTH, SHAPE));
        assertFalse(ScaffoldPlacementGeometry.isSafe(eyeAt(2.0), eyeAt(2.2), lookingBack, SUPPORT, Direction.SOUTH, SHAPE));
        assertTrue(ScaffoldPlacementGeometry.isSafe(eyeAt(2.01), eyeAt(2.23), lookingBack, SUPPORT, Direction.SOUTH, SHAPE));
    }

    @Test
    void aimingAtTheFaceCentreFromTheCurrentEyeMissesTheBlockOneTickLater() {
        // This is what Scaffold sent before: the face centre seen from an eye 0.1 beyond the edge is almost straight
        // down. Grim ray-casts the same rotation from the position after this tick's movement and finds nothing.
        Vec3 now = eyeAt(2.1);
        Vec3 next = eyeAt(2.1 + STEP);
        Rotation atFaceCentre = Rotation.lookingAt(new Vec3(0.5, -50.5, 2.0), now);

        assertTrue(atFaceCentre.pitch() > 85.0f);
        assertTrue(ScaffoldPlacementGeometry.rayHitsBox(now, atFaceCentre.directionVector(), new AABB(SUPPORT)));
        assertFalse(ScaffoldPlacementGeometry.rayHitsBox(next, atFaceCentre.directionVector(), new AABB(SUPPORT)));
        assertFalse(ScaffoldPlacementGeometry.isSafe(now, next, atFaceCentre, SUPPORT, Direction.SOUTH, SHAPE));
    }

    @Test
    void aTellyJumpsAimIsNotTheSameRayAsLookingAtItsClickPoint() {
        // Grim trace, sx_telly: past the block edge, falling at 0.3 blocks per tick while moving 0.245 forward. The safe
        // aim enters the block through its top, so its click point sits on the face's top edge and looking at that point
        // from the eye is steeper and passes in front of the block a tick later.
        BlockPos support = new BlockPos(0, -51, 5);
        AABB shape = new AABB(support);
        Vec3 now = new Vec3(0.5, -49.203 + EYE_HEIGHT, 6.161);
        Vec3 next = now.add(0.0, -0.302, 0.245);

        Rotation aim = ScaffoldPlacementGeometry.chooseRotation(now, next, support, Direction.SOUTH, null);

        assertNotNull(aim);
        assertTrue(ScaffoldPlacementGeometry.isSafe(now, next, aim, support, Direction.SOUTH, shape));
        Vec3 click = ScaffoldPlacementGeometry.clickPoint(now, aim, support, Direction.SOUTH, shape);
        Rotation atClick = Rotation.lookingAt(click, now);
        assertFalse(ScaffoldPlacementGeometry.isSafe(now, next, atClick, support, Direction.SOUTH, shape));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.05, 0.1, 0.2, 0.3, 0.45, 0.6, 0.8})
    void theChosenAimHitsTheBlockBeforeAndAfterTheTicksMovement(double beyondEdge) {
        Vec3 now = eyeAt(2.0 + beyondEdge + 0.01);
        Vec3 next = now.add(0.0, 0.0, STEP);

        Rotation chosen = ScaffoldPlacementGeometry.chooseRotation(now, next, SUPPORT, Direction.SOUTH, new Rotation(0.0f, 0.0f, false));

        assertNotNull(chosen);
        assertTrue(ScaffoldPlacementGeometry.isSafe(now, next, chosen, SUPPORT, Direction.SOUTH, SHAPE));
        // looking back at the block the player walks away from, not sideways and not up
        assertTrue(Math.abs(chosen.yaw()) > 150.0f, "yaw " + chosen.yaw());
        assertTrue(chosen.pitch() > 40.0f && chosen.pitch() < 90.0f);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.1, 0.3, 0.6})
    void anAnchoredAimOnlyUsesYawsAMultipleOf45FromThePlayersOwn(double beyondEdge) {
        Vec3 now = eyeAt(2.0 + beyondEdge + 0.01);
        Vec3 next = now.add(0.0, 0.0, STEP);

        Rotation chosen = ScaffoldPlacementGeometry.chooseRotation(now, next, SUPPORT, Direction.SOUTH, new Rotation(0.0f, 0.0f, false), 0.0f);

        assertNotNull(chosen);
        assertTrue(ScaffoldPlacementGeometry.isSafe(now, next, chosen, SUPPORT, Direction.SOUTH, SHAPE));
        assertEquals(0.0f, Math.abs(chosen.yaw()) % 45.0f, 1.0E-3f);
        assertEquals(180.0f, Math.abs(chosen.yaw()), 1.0E-3f);
    }

    @Test
    void aYawAtTheHalfTurnIsMovedInsideItOnTheSideTheServerAlreadyHas() {
        Rotation atTheBoundary = new Rotation(-180.0f, 65.0f, false);

        Rotation fromThePositiveSide = ScaffoldPlacementGeometry.avoidHalfTurn(atTheBoundary, 0.0f, new Rotation(179.9f, 60.0f, false));
        Rotation fromTheNegativeSide = ScaffoldPlacementGeometry.avoidHalfTurn(atTheBoundary, 0.0f, new Rotation(-179.9f, 60.0f, false));

        assertEquals(179.8f, fromThePositiveSide.yaw(), 1.0E-3f);
        assertEquals(-179.8f, fromTheNegativeSide.yaw(), 1.0E-3f);
        assertEquals(65.0f, fromThePositiveSide.pitch(), 1.0E-6f);
    }

    @Test
    void aYawPastTheHalfTurnAndOneWellInsideAreHandledAroundThePlayersOwnYaw() {
        // the player's yaw is 90: the half turn from it is at -90 / 270
        Rotation past = ScaffoldPlacementGeometry.avoidHalfTurn(new Rotation(-90.05f, 70.0f, false), 90.0f, null);
        Rotation inside = ScaffoldPlacementGeometry.avoidHalfTurn(new Rotation(-45.0f, 70.0f, false), 90.0f, null);

        assertEquals(Math.abs(net.minecraft.util.Mth.wrapDegrees(past.yaw() - 90.0f)), 179.8f, 1.0E-3f);
        assertEquals(-45.0f, inside.yaw(), 1.0E-6f);
    }

    @Test
    void aDiagonalBridgeFindsAnAimOnTheAnchoredYaws() {
        // walking (-x,+z) over the corner of the platform: the block to click is the west face of (-1,-51,2)
        BlockPos support = new BlockPos(-1, -51, 2);
        AABB shape = new AABB(support);
        Vec3 now = new Vec3(-1.36, -48.38, 2.22);
        Vec3 next = now.add(-0.155, 0.0, 0.155);

        Rotation chosen = ScaffoldPlacementGeometry.chooseRotation(now, next, support, Direction.WEST, new Rotation(-135.0f, 68.0f, false), 0.0f);

        assertNotNull(chosen);
        assertTrue(ScaffoldPlacementGeometry.isSafe(now, next, chosen, support, Direction.WEST, shape));
        assertEquals(0.0f, Math.abs(chosen.yaw()) % 45.0f, 1.0E-3f);
    }

    @Test
    void theChosenAimToleratesAMovementStepThatIsAThirdLongerThanPredicted() {
        Vec3 now = eyeAt(2.15);
        Vec3 predicted = now.add(0.0, 0.0, STEP);
        Vec3 actual = now.add(0.0, 0.0, STEP * 1.3);

        Rotation chosen = ScaffoldPlacementGeometry.chooseRotation(now, predicted, SUPPORT, Direction.SOUTH, new Rotation(180.0f, 75.0f, false));

        assertNotNull(chosen);
        assertTrue(ScaffoldPlacementGeometry.rayHitsBox(actual, chosen.directionVector(), new AABB(SUPPORT)));
    }

    @Test
    void aSteadyAimIsKeptInsteadOfBeingReplacedByAnEquallySafeOne() {
        Vec3 now = eyeAt(2.3);
        Vec3 next = now.add(0.0, 0.0, STEP);
        Rotation current = new Rotation(180.0f, 68.0f, false);

        Rotation chosen = ScaffoldPlacementGeometry.chooseRotation(now, next, SUPPORT, Direction.SOUTH, current);

        assertNotNull(chosen);
        assertTrue(current.angleTo(chosen) < 12.0f, "turn of " + current.angleTo(chosen));
    }

    @Test
    void theClickPointLiesOnTheClickedFaceInsideItsBounds() {
        Vec3 now = eyeAt(2.4);
        Rotation chosen = ScaffoldPlacementGeometry.chooseRotation(now, now.add(0.0, 0.0, STEP), SUPPORT, Direction.SOUTH, null);

        Vec3 point = ScaffoldPlacementGeometry.clickPoint(now, chosen, SUPPORT, Direction.SOUTH, SHAPE);

        assertEquals(2.0, point.z, 1.0E-9);
        assertTrue(point.x > 0.0 && point.x < 1.0);
        assertTrue(point.y > -51.0 && point.y < -50.0);
    }

    @Test
    void aPlacementOnTheTopFaceOfTheBlockBelowIsSafeLookingStraightDown() {
        Vec3 now = new Vec3(0.5, -48.4, 1.5);
        Vec3 next = now.add(0.0, -0.08, 0.0);

        Rotation chosen = ScaffoldPlacementGeometry.chooseRotation(now, next, SUPPORT, Direction.UP, new Rotation(33.0f, 20.0f, false));

        assertNotNull(chosen);
        assertTrue(ScaffoldPlacementGeometry.isSafe(now, next, chosen, SUPPORT, Direction.UP, SHAPE));
    }
}
