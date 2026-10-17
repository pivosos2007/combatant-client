package combatant.client.features.module.modules.combat.autocrystal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoCrystalRayFaceTest {
    private static final BlockPos BLOCK = new BlockPos(0, -61, 4);
    private static final Vec3 EYE = new Vec3(0.5, -58.38, 0.5);

    private static Vec3 toward(Vec3 point) {
        return point.subtract(EYE).normalize();
    }

    @Test
    void rayHittingTopFaceMatchesUp() {
        Vec3 dir = toward(new Vec3(0.5, -60.0, 4.5));
        assertTrue(AutoCrystalInteractionUtil.rayEntersFace(EYE, dir, BLOCK, Direction.UP, 6.0));
    }

    @Test
    void rayHittingTopFaceDoesNotMatchOtherFace() {
        Vec3 dir = toward(new Vec3(0.5, -60.0, 4.5));
        assertFalse(AutoCrystalInteractionUtil.rayEntersFace(EYE, dir, BLOCK, Direction.NORTH, 6.0));
    }

    @Test
    void rayHittingNearSideFaceMatchesNorth() {
        Vec3 dir = toward(new Vec3(0.5, -60.5, 4.0));
        assertTrue(AutoCrystalInteractionUtil.rayEntersFace(EYE, dir, BLOCK, Direction.NORTH, 6.0));
    }

    @Test
    void rayMissingBlockOrOutOfRangeFails() {
        assertFalse(AutoCrystalInteractionUtil.rayEntersFace(EYE, new Vec3(0, 1, 0), BLOCK, Direction.UP, 6.0));
        Vec3 dir = toward(new Vec3(0.5, -60.0, 4.5));
        assertFalse(AutoCrystalInteractionUtil.rayEntersFace(EYE, dir, BLOCK, Direction.UP, 1.0));
    }
}
