package combatant.client.automation.actions;

import combatant.client.automation.actions.RemovalConfirmation.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemovalConfirmationTest {

    @Test
    void confirmsAfterConsecutiveGoneTicks() {
        RemovalConfirmation c = new RemovalConfirmation(3, 2);
        assertEquals(State.PENDING, c.update(false));
        assertEquals(State.PENDING, c.update(true));
        assertEquals(State.PENDING, c.update(true));
        assertEquals(State.CONFIRMED, c.update(true));
    }

    @Test
    void serverRevertRestartsTheWindow() {
        RemovalConfirmation c = new RemovalConfirmation(2, 3);
        c.update(true);
        assertEquals(State.PENDING, c.update(false));
        assertEquals(1, c.reappearances());
        assertEquals(State.PENDING, c.update(true));
        assertEquals(State.CONFIRMED, c.update(true));
    }

    @Test
    void blockThatKeepsComingBackIsRejected() {
        RemovalConfirmation c = new RemovalConfirmation(3, 1);
        c.update(true);
        c.update(false);
        c.update(true);
        assertEquals(State.REJECTED, c.update(false));
    }

    @Test
    void blockThatNeverWentAwayIsNotARejection() {
        RemovalConfirmation c = new RemovalConfirmation(2, 0);
        for (int i = 0; i < 50; i++) assertEquals(State.PENDING, c.update(false));
    }
}
