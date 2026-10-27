package combatant.client.util.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArrowPlacementTest {
    private static final double EPS = 1e-9;

    @Test
    void targetAheadPointsUp() {
        // yaw 0 looks towards +Z
        assertEquals(-90.0, ArrowPlacement.screenAngleDeg(0, 10, 0), 1e-6);
    }

    @Test
    void targetBehindPointsDown() {
        assertEquals(90.0, ArrowPlacement.screenAngleDeg(0, -10, 0), 1e-6);
    }

    @Test
    void targetOnRightPointsRight() {
        // looking +Z, right-hand side is -X
        assertEquals(0.0, ArrowPlacement.screenAngleDeg(-10, 0, 0), 1e-6);
    }

    @Test
    void targetOnLeftPointsLeft() {
        assertEquals(180.0, Math.abs(ArrowPlacement.screenAngleDeg(10, 0, 0)), 1e-6);
    }

    @Test
    void angleFollowsCameraYaw() {
        // yaw 90 looks towards -X: a target at -X is straight ahead
        assertEquals(-90.0, ArrowPlacement.screenAngleDeg(-10, 0, 90), 1e-6);
        // yaw 180 looks towards -Z
        assertEquals(-90.0, ArrowPlacement.screenAngleDeg(0, -10, 180), 1e-6);
    }

    @Test
    void onCirclePlacesPointAroundCenter() {
        ArrowPlacement.Point up = ArrowPlacement.onCircle(-90, 50, 400, 300);
        assertEquals(400.0, up.x(), 1e-6);
        assertEquals(250.0, up.y(), 1e-6);
        ArrowPlacement.Point right = ArrowPlacement.onCircle(0, 50, 400, 300);
        assertEquals(450.0, right.x(), 1e-6);
        assertEquals(300.0, right.y(), 1e-6);
    }

    @Test
    void radiusFactorClampsAndInterpolates() {
        assertEquals(0.5f, ArrowPlacement.radiusFactor(1, 6, 60, 0.5f, 1.0f), EPS);
        assertEquals(1.0f, ArrowPlacement.radiusFactor(100, 6, 60, 0.5f, 1.0f), EPS);
        assertEquals(0.75f, ArrowPlacement.radiusFactor(33, 6, 60, 0.5f, 1.0f), 1e-6);
    }

    @Test
    void distanceColorBlendsNearToFar() {
        int near = 0xFFFF0000;
        int far = 0xFF00FF00;
        assertEquals(near, ArrowPlacement.distanceColor(0, 6, 60, near, far));
        assertEquals(far, ArrowPlacement.distanceColor(500, 6, 60, near, far));
        assertEquals(0xFF808000, ArrowPlacement.distanceColor(33, 6, 60, near, far));
    }

    @Test
    void distanceColorHandlesDegenerateRange() {
        assertEquals(0xFFFF0000, ArrowPlacement.distanceColor(10, 20, 20, 0xFFFF0000, 0xFF0000FF));
        assertEquals(0xFF0000FF, ArrowPlacement.distanceColor(30, 20, 20, 0xFFFF0000, 0xFF0000FF));
    }
}
