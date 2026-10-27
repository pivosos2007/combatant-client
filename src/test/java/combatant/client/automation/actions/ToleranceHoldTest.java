package combatant.client.automation.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToleranceHoldTest {

    @Test
    void zeroHoldSucceedsOnFirstAlignedTick() {
        ToleranceHold hold = new ToleranceHold(0);
        assertFalse(hold.update(false));
        assertTrue(hold.update(true));
    }

    @Test
    void holdNeedsConsecutiveAlignedTicks() {
        ToleranceHold hold = new ToleranceHold(2);
        assertFalse(hold.update(true));
        assertFalse(hold.update(true));
        assertTrue(hold.update(true));
    }

    @Test
    void leavingToleranceRestartsTheCount() {
        ToleranceHold hold = new ToleranceHold(2);
        hold.update(true);
        hold.update(true);
        assertFalse(hold.update(false));
        assertEquals(0, hold.satisfiedTicks());
        assertFalse(hold.update(true));
        assertFalse(hold.update(true));
        assertTrue(hold.update(true));
    }

    @Test
    void angularErrorWrapsYawAcrossTheSeam() {
        assertEquals(20.0f, ToleranceHold.angularError(170f, 0f, -170f, 0f), 1.0e-4f);
        assertEquals(20.0f, ToleranceHold.angularError(-170f, 0f, 170f, 0f), 1.0e-4f);
        assertEquals(0.0f, ToleranceHold.angularError(10f, 5f, 370f, 5f), 1.0e-4f);
    }

    @Test
    void angularErrorCombinesYawAndPitchAndCapsAtHalfTurn() {
        assertEquals(5.0f, ToleranceHold.angularError(0f, 0f, 3f, 4f), 1.0e-4f);
        assertEquals(180.0f, ToleranceHold.angularError(0f, -90f, 180f, 90f), 1.0e-4f);
    }
}
