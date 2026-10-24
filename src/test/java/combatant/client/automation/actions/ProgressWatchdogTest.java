package combatant.client.automation.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressWatchdogTest {

    @Test
    void steadyApproachNeverStalls() {
        ProgressWatchdog dog = new ProgressWatchdog(3, 0.05);
        for (double d = 10; d > 1; d -= 0.2) assertFalse(dog.update(d));
    }

    @Test
    void stallsAfterTheConfiguredNumberOfTicksWithoutProgress() {
        ProgressWatchdog dog = new ProgressWatchdog(3, 0.05);
        assertFalse(dog.update(5.0));
        assertFalse(dog.update(5.0));
        assertFalse(dog.update(5.01));
        assertTrue(dog.update(5.0));
    }

    @Test
    void progressBelowTheMinimumDoesNotCount() {
        ProgressWatchdog dog = new ProgressWatchdog(2, 0.1);
        dog.update(5.0);
        assertFalse(dog.update(4.95));
        assertTrue(dog.update(4.92));
    }

    @Test
    void recoveringResetsTheStallCount() {
        ProgressWatchdog dog = new ProgressWatchdog(3, 0.05);
        dog.update(5.0);
        dog.update(5.0);
        dog.update(5.0);
        dog.update(5.0);
        assertEquals(3, dog.stalledTicks());
        assertFalse(dog.update(4.0));
        assertEquals(0, dog.stalledTicks());
    }

    @Test
    void backingAwayCountsAsNoProgressAgainstTheBestDistance() {
        ProgressWatchdog dog = new ProgressWatchdog(2, 0.05);
        dog.update(3.0);
        assertFalse(dog.update(4.0));
        assertTrue(dog.update(3.5));
    }

    @Test
    void resetForgetsTheBestDistance() {
        ProgressWatchdog dog = new ProgressWatchdog(2, 0.05);
        dog.update(1.0);
        dog.reset();
        assertFalse(dog.update(9.0));
        assertFalse(dog.update(8.0));
    }
}
