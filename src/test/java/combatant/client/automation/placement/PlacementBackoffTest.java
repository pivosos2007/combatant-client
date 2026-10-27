package combatant.client.automation.placement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementBackoffTest {

    private final PlacementBackoff<String> backoff = new PlacementBackoff<>(3, 20, 100);

    @Test
    void thirdFailureStartsBackoffForTheConfiguredTicks() {
        assertEquals(1, backoff.recordFailure("a", 0));
        assertEquals(2, backoff.recordFailure("a", 1));
        assertFalse(backoff.isBackedOff("a", 2));

        assertEquals(0, backoff.recordFailure("a", 2));

        assertTrue(backoff.isBackedOff("a", 3));
        assertTrue(backoff.isBackedOff("a", 21));
        assertFalse(backoff.isBackedOff("a", 22));
    }

    @Test
    void afterBackoffTheKeyStartsFromZeroFailures() {
        backoff.recordFailure("a", 0);
        backoff.recordFailure("a", 0);
        backoff.recordFailure("a", 0);
        assertFalse(backoff.isBackedOff("a", 20));

        assertEquals(1, backoff.recordFailure("a", 20));
        assertFalse(backoff.isBackedOff("a", 21));
    }

    @Test
    void successForgetsFailures() {
        backoff.recordFailure("a", 0);
        backoff.recordFailure("a", 1);
        backoff.recordSuccess("a");

        assertEquals(1, backoff.recordFailure("a", 2));
        assertFalse(backoff.isBackedOff("a", 3));
    }

    @Test
    void keysAreIndependent() {
        backoff.recordFailure("a", 0);
        backoff.recordFailure("a", 0);
        backoff.recordFailure("a", 0);

        assertTrue(backoff.isBackedOff("a", 1));
        assertFalse(backoff.isBackedOff("b", 1));
        assertEquals(1, backoff.recordFailure("b", 1));
    }

    @Test
    void staleFailuresDoNotCountTowardBackoff() {
        backoff.recordFailure("a", 0);
        backoff.recordFailure("a", 1);

        // Long after the earlier failures: counting starts over.
        assertEquals(1, backoff.recordFailure("a", 500));
        assertEquals(2, backoff.recordFailure("a", 501));
        assertFalse(backoff.isBackedOff("a", 502));
    }

    @Test
    void failureCountReportsOnlyFreshFailures() {
        backoff.recordFailure("a", 0);
        backoff.recordFailure("a", 5);

        assertEquals(2, backoff.failures("a", 6));
        assertEquals(0, backoff.failures("a", 500));
        assertEquals(0, backoff.failures("missing", 6));
    }

    @Test
    void clearDropsBackoffAndCounts() {
        backoff.recordFailure("a", 0);
        backoff.recordFailure("a", 0);
        backoff.recordFailure("a", 0);

        backoff.clear();

        assertFalse(backoff.isBackedOff("a", 1));
        assertEquals(1, backoff.recordFailure("a", 1));
    }
}
