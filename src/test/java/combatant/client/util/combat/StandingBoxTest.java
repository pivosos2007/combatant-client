package combatant.client.util.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.entity.Pose;
import org.junit.jupiter.api.Test;

class StandingBoxTest {
    @Test
    void playerShrinkingPosesGetStandingBox() {
        for (Pose pose : new Pose[]{Pose.CROUCHING, Pose.SWIMMING, Pose.FALL_FLYING, Pose.SPIN_ATTACK}) {
            assertTrue(StandingBox.applies(true, pose), pose.name());
        }
    }

    @Test
    void playerOtherPosesAreLeftAlone() {
        assertFalse(StandingBox.applies(true, Pose.STANDING));
        assertFalse(StandingBox.applies(true, Pose.SLEEPING));
        assertFalse(StandingBox.applies(true, Pose.DYING));
    }

    @Test
    void nonPlayersOnlyWhenNotStanding() {
        assertFalse(StandingBox.applies(false, Pose.STANDING));
        assertTrue(StandingBox.applies(false, Pose.SITTING));
    }
}
