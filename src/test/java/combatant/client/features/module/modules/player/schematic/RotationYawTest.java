package combatant.client.features.module.modules.player.schematic;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

class RotationYawTest {
    @Test
    void yawAndRotationRoundTrip() {
        for (int r = 0; r < 16; r++) {
            float yaw = BuildPlanner.yawForRotation(r);
            assertEquals(r, BuildPlanner.rotationForYaw(yaw), "centre of " + r);
            assertEquals(r, BuildPlanner.rotationForYaw(yaw + 11.0f), "upper edge of " + r);
            assertEquals(r, BuildPlanner.rotationForYaw(yaw - 11.0f), "lower edge of " + r);
        }
        assertEquals(22.5f, BuildPlanner.yawForRotation(1));
    }

    @Test
    void plannerPicksYawGivingTheRotation() {
        for (int wanted = 0; wanted < 16; wanted++) {
            final int r = wanted;
            Float yaw = BuildPlanner.chooseRotationYaw(0.0f, y -> BuildPlanner.rotationForYaw(y) == r);
            assertNotNull(yaw);
            assertEquals(r, BuildPlanner.rotationForYaw(yaw));
        }
    }

    @Test
    void plannerPrefersNearestYawAndReportsNone() {
        // Two rotations qualify: the one nearer the current look wins.
        Float yaw = BuildPlanner.chooseRotationYaw(170.0f,
                y -> BuildPlanner.rotationForYaw(y) == 0 || BuildPlanner.rotationForYaw(y) == 8);
        assertEquals(BuildPlanner.yawForRotation(8), yaw);
        assertNull(BuildPlanner.chooseRotationYaw(0.0f, y -> false));
    }

    @Test
    void walkerSpotMustFaceTheCellAtTheWantedYaw() {
        // Yaw 0 looks along +Z, yaw 90 along -X.
        assertTrue(BuildPlanner.yawAllows(0.0, 4.0, 0.0f));
        assertFalse(BuildPlanner.yawAllows(0.0, 4.0, 90.0f));
        assertTrue(BuildPlanner.yawAllows(-4.0, 0.0, 90.0f));
        assertTrue(BuildPlanner.yawAllows(-4.0, 0.0, -270.0f));
        // A spot 22.5 degrees off is a different rotation.
        double rad = Math.toRadians(22.5);
        assertFalse(BuildPlanner.yawAllows(-4.0 * Math.sin(rad), 4.0 * Math.cos(rad), 0.0f));
        assertTrue(BuildPlanner.yawAllows(-4.0 * Math.sin(rad), 4.0 * Math.cos(rad), 22.5f));
        // Standing on the cell gives no usable yaw.
        assertFalse(BuildPlanner.yawAllows(0.0, 0.2, 0.0f));
        // Whatever the walker allows really places that rotation.
        for (int r = 0; r < 16; r++) {
            float wantedYaw = BuildPlanner.yawForRotation(r);
            for (double deg = -180; deg < 180; deg += 3) {
                double a = Math.toRadians(deg);
                if (BuildPlanner.yawAllows(-4.0 * Math.sin(a), 4.0 * Math.cos(a), wantedYaw)) {
                    assertEquals(r, BuildPlanner.rotationForYaw((float) Mth.wrapDegrees(deg)));
                }
            }
        }
    }
}
