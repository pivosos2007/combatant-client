package combatant.client.util.block.interaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class MiningSurfaceSamplesTest {
    @Test void eachFaceKeepsCorrectCoordinateConstant() {
        for (var face : MiningSurfaceSamples.Face.values()) {
            var p = MiningSurfaceSamples.onFace(0, 0, 0, 1, 1, 1, face, .2, .8);
            assertTrue(p.x() > 0 && p.x() < 1);
            assertTrue(p.y() > 0 && p.y() < 1);
            assertTrue(p.z() > 0 && p.z() < 1);
            switch (face) {
                case WEST -> assertEquals(.0008, p.x(), 1e-8);
                case EAST -> assertEquals(.9992, p.x(), 1e-8);
                case DOWN -> assertEquals(.0008, p.y(), 1e-8);
                case UP -> assertEquals(.9992, p.y(), 1e-8);
                case NORTH -> assertEquals(.0008, p.z(), 1e-8);
                case SOUTH -> assertEquals(.9992, p.z(), 1e-8);
            }
        }
    }

    @Test void twoCoordinatesVaryIndependentlyOnEveryFace() {
        for (var face : MiningSurfaceSamples.Face.values()) {
            var p = MiningSurfaceSamples.onFace(0,0,0,1,1,1,face,.2,.8);
            var q = MiningSurfaceSamples.onFace(0,0,0,1,1,1,face,.8,.2);
            assertFalse(p.equals(q), "Multipoints must cover a surface grid, not one diagonal");
            var center = MiningSurfaceSamples.onFace(0,0,0,1,1,1,face,.5,.5);
            assertFalse(center.equals(p));
        }
    }

    @Test void insetNeverLeavesThinVoxelShape() {
        var point = MiningSurfaceSamples.onFace(.1,.1,.1,.10001,.11,.15,
                MiningSurfaceSamples.Face.EAST,.2,.8);
        assertTrue(point.x() >= .1 && point.x() <= .10001);
        assertTrue(point.y() >= .1 && point.y() <= .11);
        assertTrue(point.z() >= .1 && point.z() <= .15);
    }
}
