/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GrimAC PositionPlace: a click may only name a face whose plane the eyes are not behind. */
class FaceVisibilityTest {

    private static final double EYE_HEIGHT = 1.62;
    private static final BlockPos FLOOR = new BlockPos(0, 64, 0);

    private static Vec3 eyesStandingAt(double x, double z) {
        return new Vec3(x, FLOOR.getY() + 1 + EYE_HEIGHT, z);
    }

    @Test
    void standingOnABlockOnlyItsTopIsVisible() {
        Vec3 eyes = eyesStandingAt(0.5, 0.5);
        assertTrue(FaceVisibility.isVisibleFrom(eyes, FLOOR, Direction.UP));
        for (Direction face : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST,
                Direction.EAST}) {
            assertFalse(FaceVisibility.isVisibleFrom(eyes, FLOOR, face), face.toString());
        }
    }

    @Test
    void aSideOfTheFloorBlockShowsOnceTheEyesOverhangIt() {
        assertFalse(FaceVisibility.isVisibleFrom(eyesStandingAt(0.99, 0.5), FLOOR, Direction.EAST));
        assertTrue(FaceVisibility.isVisibleFrom(eyesStandingAt(1.2, 0.5), FLOOR, Direction.EAST));
        assertTrue(FaceVisibility.isVisibleFrom(eyesStandingAt(-0.2, 0.5), FLOOR, Direction.WEST));
    }

    @Test
    void eyesOnTheFacePlaneSeeTheFace() {
        assertTrue(FaceVisibility.isVisibleFrom(eyesStandingAt(1.0, 0.5), FLOOR, Direction.EAST));
        assertTrue(FaceVisibility.isVisibleFrom(eyesStandingAt(0.5, 0.0), FLOOR, Direction.NORTH));
    }

    /** A wall cell's ceiling block shows its underside only to eyes below it, never to standing eyes above it. */
    @Test
    void theUndersideOfABlockAboveIsHiddenFromEyesAboveItsBottom() {
        BlockPos aboveWallCell = FLOOR.above(2).east();
        Vec3 eyes = eyesStandingAt(0.5, 0.5);
        assertFalse(FaceVisibility.isVisibleFrom(eyes, aboveWallCell, Direction.DOWN));
        assertTrue(FaceVisibility.isVisibleFrom(eyes, aboveWallCell, Direction.WEST));
        assertTrue(FaceVisibility.isVisibleFrom(eyes.add(0.0, -1.0, 0.0), aboveWallCell, Direction.DOWN));
    }
}
