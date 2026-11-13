package combatant.client.features.module.modules.combat;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AttributeSwapCycleTimingTest {
    @Test
    void attackPrecedesNextMovementAndRequiresPreviouslyReportedFall() {
        var window = new AttributeSwap.CycleAttackWindow();
        List<String> packets = new ArrayList<>();
        window.beforeMovement(2, () -> packets.add("attack"));
        assertTrue(packets.isEmpty());
        window.notePosition(2.1, false, false);
        packets.add("mace-slot");
        window.beforeMovement(2, () -> packets.add("attack"));
        packets.add("position");
        assertEquals(List.of("mace-slot", "attack", "position"), packets);
    }

    @Test
    void resetCannotReuseFallFromPreviousCycle() {
        var window = new AttributeSwap.CycleAttackWindow();
        window.notePosition(5, false, false);
        window.reset();
        assertFalse(window.ready(2));
    }

    @Test
    void glidingGroundedAndInsufficientFallDoNotAuthorizeAttack() {
        var window = new AttributeSwap.CycleAttackWindow();
        window.notePosition(5, true, false);
        assertFalse(window.ready(2));
        window.notePosition(5, false, true);
        assertFalse(window.ready(2));
        window.notePosition(1.9, false, false);
        assertFalse(window.ready(2));
    }
}
