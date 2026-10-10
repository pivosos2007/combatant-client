package combatant.client.util.block.interaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class MiningPolicyTest {
    @Test void reachUsesSurfaceDistanceNotCenter() {
        assertTrue(MiningPolicy.withinReach(25.0, 5.0));
        assertFalse(MiningPolicy.withinReach(25.001, 5.0));
        assertFalse(MiningPolicy.withinReach(Double.NaN, 5.0));
        assertFalse(MiningPolicy.withinReach(1.0, 0));
        assertFalse(MiningPolicy.withinReach(Double.POSITIVE_INFINITY, 6));
    }

    @Test void estimatedDigTimeHandlesLimitsAndInvalidSpeeds() {
        assertEquals(1, MiningPolicy.estimatedTicks(1.0f));
        assertEquals(1, MiningPolicy.estimatedTicks(10.0f));
        assertEquals(5, MiningPolicy.estimatedTicks(0.2f));
        assertEquals(10, MiningPolicy.estimatedTicks(0.1f));
        assertEquals(Integer.MAX_VALUE, MiningPolicy.estimatedTicks(0f));
        assertEquals(Integer.MAX_VALUE, MiningPolicy.estimatedTicks(Float.NaN));
        assertEquals(Integer.MAX_VALUE, MiningPolicy.estimatedTicks(Float.NEGATIVE_INFINITY));
    }

    @Test void filteringBeforeLockSkipsExpensiveAndWrongToolBlocks() {
        assertFalse(MiningPolicy.admissible(0.001f, 100, true, false, false));
        assertTrue(MiningPolicy.admissible(0.02f, 100, true, false, false));
        assertFalse(MiningPolicy.admissible(0.05f, 100, false, true, false));
        assertTrue(MiningPolicy.admissible(0.05f, 100, false, true, true));
        assertFalse(MiningPolicy.admissible(0, 100, false, false, false));
        assertTrue(MiningPolicy.admissible(0.001f, 100, false, false, false));
    }

    @Test void configurableSuitableToolPreventsBareHandMining() {
        assertFalse(MiningPolicy.admissible(0.02f, 100, true, false, true, true, false));
        assertTrue(MiningPolicy.admissible(0.02f, 100, true, false, true, true, true));
        assertTrue(MiningPolicy.admissible(0.02f, 100, true, false, true, false, false));
        assertFalse(MiningPolicy.admissible(Float.POSITIVE_INFINITY, 100, false, false, true));
    }

    @Test void stalledMiningBacksOffInsteadOfWaitingForever() {
        assertFalse(MiningPolicy.miningTimedOut(220, 100));
        assertTrue(MiningPolicy.miningTimedOut(221, 100));
        assertFalse(MiningPolicy.miningTimedOut(20, 0));
        assertTrue(MiningPolicy.miningTimedOut(21, 0));
        assertFalse(MiningPolicy.miningTimedOut(-1, 100));
    }

    @Test void tunnelNeverChoosesSupportingFloorOrThirdLevel() {
        assertFalse(MiningPolicy.tunnelHeight(64, 63));
        assertTrue(MiningPolicy.tunnelHeight(64, 64));
        assertTrue(MiningPolicy.tunnelHeight(64, 65));
        assertFalse(MiningPolicy.tunnelHeight(64, 66));
    }
}
