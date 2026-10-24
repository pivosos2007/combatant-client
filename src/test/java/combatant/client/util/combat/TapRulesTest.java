package combatant.client.util.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TapRulesTest {
    @Test
    void needsSprintAndNoActiveTap() {
        assertFalse(TapRules.shouldTap(false, false, 100, Long.MIN_VALUE, 0, 100, 0.0));
        assertFalse(TapRules.shouldTap(true, true, 100, Long.MIN_VALUE, 0, 100, 0.0));
        assertTrue(TapRules.shouldTap(true, false, 100, Long.MIN_VALUE, 0, 100, 0.99));
    }

    @Test
    void cooldownBlocksUntilElapsed() {
        assertFalse(TapRules.shouldTap(true, false, 105, 100, 6, 100, 0.0));
        assertTrue(TapRules.shouldTap(true, false, 106, 100, 6, 100, 0.0));
    }

    @Test
    void chanceUsesRoll() {
        assertTrue(TapRules.shouldTap(true, false, 10, Long.MIN_VALUE, 0, 50, 0.49));
        assertFalse(TapRules.shouldTap(true, false, 10, Long.MIN_VALUE, 0, 50, 0.50));
        assertFalse(TapRules.shouldTap(true, false, 10, Long.MIN_VALUE, 0, 0, 0.0));
    }

    @Test
    void pickTicksCoversRangeInclusively() {
        assertEquals(2, TapRules.pickTicks(2, 5, 0.0));
        assertEquals(5, TapRules.pickTicks(2, 5, 0.999999));
        assertEquals(5, TapRules.pickTicks(5, 2, 0.999999));
        assertEquals(3, TapRules.pickTicks(3, 3, 0.7));
    }
}
