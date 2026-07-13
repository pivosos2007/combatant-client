/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.scaffold;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScaffoldPlacementGateTest {
    private static final long START = 10_000_000_000L;

    @Test
    void eagleIsExactlyOneTickInsteadOfAContinuousHold() {
        ScaffoldPlacementGate gate = new ScaffoldPlacementGate();

        assertTrue(consumeEligiblePulse(gate));
        assertFalse(consumeEligiblePulse(gate));
        assertFalse(consumeEligiblePulse(gate));
    }

    @Test
    void leavingTheEdgeDoesNotConsumeTheArmedPulse() {
        ScaffoldPlacementGate gate = new ScaffoldPlacementGate();

        assertFalse(gate.consumeEaglePulse(true, false, false, true, false, false));
        assertTrue(consumeEligiblePulse(gate));
        assertFalse(consumeEligiblePulse(gate));
    }

    @Test
    void blocksToEagleZeroRearmsOnlyAfterAPlacedBlock() {
        ScaffoldPlacementGate gate = new ScaffoldPlacementGate();

        assertTrue(consumeEligiblePulse(gate));
        assertFalse(consumeEligiblePulse(gate));

        gate.recordSuccessfulPlacement(START, 0);

        assertTrue(consumeEligiblePulse(gate));
        assertFalse(consumeEligiblePulse(gate));
    }

    @Test
    void blocksToEagleTwoPulsesOnceEveryThreeSuccessfulBlocks() {
        ScaffoldPlacementGate gate = new ScaffoldPlacementGate();
        assertTrue(consumeEligiblePulse(gate));

        gate.recordSuccessfulPlacement(START, 2);
        assertFalse(consumeEligiblePulse(gate));
        gate.recordSuccessfulPlacement(START + 1, 2);
        assertFalse(consumeEligiblePulse(gate));
        gate.recordSuccessfulPlacement(START + 2, 2);
        assertTrue(consumeEligiblePulse(gate));
        assertFalse(consumeEligiblePulse(gate));
    }

    @Test
    void sixBlocksPerSecondUsesAnExactNanosecondGate() {
        ScaffoldPlacementGate gate = new ScaffoldPlacementGate();
        gate.recordSuccessfulPlacement(START, 0);

        assertFalse(gate.isPlacementReady(START + 166_666_666L, 6.0, 0));
        assertTrue(gate.isPlacementReady(START + 166_666_667L, 6.0, 0));
    }

    @Test
    void extraTickDelayAndSpeedGateMustBothBeReady() {
        ScaffoldPlacementGate gate = new ScaffoldPlacementGate();
        gate.recordSuccessfulPlacement(START, 0);

        assertFalse(gate.isPlacementReady(START + 1_000_000_000L, 6.0, 1));
        assertTrue(gate.isPlacementReady(START + 1_000_000_000L, 6.0, 0));
    }

    @Test
    void cadenceNeverExtendsOrRearmsEagle() {
        ScaffoldPlacementGate gate = new ScaffoldPlacementGate();
        assertTrue(consumeEligiblePulse(gate));

        assertTrue(gate.isPlacementReady(START, 20.0, 0));
        assertTrue(gate.isPlacementReady(START + 60_000_000_000L, 1.0, 0));
        assertFalse(consumeEligiblePulse(gate));
    }

    @ParameterizedTest(name = "failed placement at {0} blocks/s")
    @ValueSource(doubles = {1.0, 3.8, 6.0, 10.0, 20.0})
    void failedPlacementsAtAnySpeedCannotRearmEagle(double blocksPerSecond) {
        ScaffoldPlacementGate gate = new ScaffoldPlacementGate();
        assertTrue(consumeEligiblePulse(gate));

        // A failed placement does not call recordSuccessfulPlacement. Neither elapsed time nor
        // another placement attempt is allowed to turn the one-tick pulse into a hold.
        assertTrue(gate.isPlacementReady(START, blocksPerSecond, 0));
        assertFalse(consumeEligiblePulse(gate));
        assertTrue(gate.isPlacementReady(START + 60_000_000_000L, blocksPerSecond, 0));
        assertFalse(consumeEligiblePulse(gate));
    }

    @Test
    void resetClearsCadenceAndArmsOneFreshEaglePulse() {
        ScaffoldPlacementGate gate = new ScaffoldPlacementGate();
        consumeEligiblePulse(gate);
        gate.recordSuccessfulPlacement(START, 20);

        gate.reset();

        assertTrue(gate.isPlacementReady(START, 1.0, 0));
        assertTrue(consumeEligiblePulse(gate));
        assertFalse(consumeEligiblePulse(gate));
    }

    @ParameterizedTest(name = "pulse eligibility mask {0}")
    @MethodSource("allPulseEligibilityCombinations")
    void everyEagleEligibilityCombinationIsHandled(
            int mask,
            boolean eagleEnabled,
            boolean closeToEdge,
            boolean onlyOnGround,
            boolean onGround,
            boolean flying,
            boolean goingDown
    ) {
        ScaffoldPlacementGate gate = new ScaffoldPlacementGate();
        boolean expected = eagleEnabled
                && closeToEdge
                && (!onlyOnGround || onGround)
                && !flying
                && !goingDown;

        assertTrue(
                gate.consumeEaglePulse(
                        eagleEnabled,
                        closeToEdge,
                        onlyOnGround,
                        onGround,
                        flying,
                        goingDown
                ) == expected,
                "unexpected first-tick decision for mask " + mask
        );

        if (expected) {
            assertFalse(consumeEligiblePulse(gate), "eligible pulse must last exactly one tick");
        } else {
            assertTrue(consumeEligiblePulse(gate), "an ineligible tick must not consume the armed pulse");
            assertFalse(consumeEligiblePulse(gate));
        }
    }

    @ParameterizedTest(name = "safe-walk eligibility mask {0}")
    @MethodSource("allSafeWalkCombinations")
    void everySafeWalkCombinationIsHandled(
            int mask,
            boolean scaffoldEnabled,
            boolean eagleEnabled,
            boolean canOperate,
            boolean onGround,
            boolean flying,
            boolean goingDown,
            boolean hasBlocks
    ) {
        boolean expected = scaffoldEnabled
                && eagleEnabled
                && canOperate
                && onGround
                && !flying
                && !goingDown
                && hasBlocks;

        assertTrue(
                ScaffoldPlacementGate.shouldSafeWalk(
                        scaffoldEnabled,
                        eagleEnabled,
                        canOperate,
                        onGround,
                        flying,
                        goingDown,
                        hasBlocks
                ) == expected,
                "unexpected edge-clipping decision for mask " + mask
        );
    }

    @ParameterizedTest(name = "blocks_to_eagle={0}")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20})
    void everyBlocksToEagleSettingRearmsAtItsExactBoundary(int blocksToEagle) {
        ScaffoldPlacementGate gate = new ScaffoldPlacementGate();
        assertTrue(consumeEligiblePulse(gate));

        for (int placement = 1; placement <= blocksToEagle; placement++) {
            gate.recordSuccessfulPlacement(START + placement, blocksToEagle);
            assertFalse(consumeEligiblePulse(gate), "rearmed too early at placement " + placement);
        }

        gate.recordSuccessfulPlacement(START + blocksToEagle + 1L, blocksToEagle);
        assertTrue(consumeEligiblePulse(gate), "did not rearm at the configured boundary");
        assertFalse(consumeEligiblePulse(gate), "boundary pulse lasted more than one tick");
    }

    @Test
    void everyShortInterleavingOfEdgeTicksFailuresAndSuccessfulPlacementsMatchesTheModel() {
        int sequenceLength = 7;
        int actionCount = 4;
        int sequenceCount = (int) Math.pow(actionCount, sequenceLength);

        for (int blocksToEagle = 0; blocksToEagle <= 10; blocksToEagle++) {
            for (int encodedSequence = 0; encodedSequence < sequenceCount; encodedSequence++) {
                ScaffoldPlacementGate gate = new ScaffoldPlacementGate();
                boolean expectedArmed = true;
                int expectedPlacements = 0;
                int actions = encodedSequence;

                for (int step = 0; step < sequenceLength; step++) {
                    int action = actions % actionCount;
                    actions /= actionCount;

                    switch (action) {
                        case 0 -> { // Eligible edge tick.
                            boolean actual = consumeEligiblePulse(gate);
                            assertTrue(
                                    actual == expectedArmed,
                                    sequenceMessage(blocksToEagle, encodedSequence, step, action)
                            );
                            expectedArmed = false;
                        }
                        case 1 -> { // Away from the edge: must not consume the pulse.
                            assertFalse(
                                    gate.consumeEaglePulse(true, false, false, true, false, false),
                                    sequenceMessage(blocksToEagle, encodedSequence, step, action)
                            );
                        }
                        case 2 -> { // A real accepted placement is the only re-arm source.
                            gate.recordSuccessfulPlacement(START + step + 1L, blocksToEagle);
                            expectedPlacements++;
                            if (expectedPlacements > blocksToEagle) {
                                expectedPlacements = 0;
                                expectedArmed = true;
                            }
                        }
                        case 3 -> { // Eagle disabled: another non-consuming failed tick.
                            assertFalse(
                                    gate.consumeEaglePulse(false, true, false, true, false, false),
                                    sequenceMessage(blocksToEagle, encodedSequence, step, action)
                            );
                        }
                        default -> throw new AssertionError("unknown action " + action);
                    }
                }
            }
        }
    }

    private static boolean consumeEligiblePulse(ScaffoldPlacementGate gate) {
        return gate.consumeEaglePulse(true, true, false, true, false, false);
    }

    private static Stream<Arguments> allPulseEligibilityCombinations() {
        return IntStream.range(0, 64).mapToObj(mask -> Arguments.of(
                mask,
                bit(mask, 0),
                bit(mask, 1),
                bit(mask, 2),
                bit(mask, 3),
                bit(mask, 4),
                bit(mask, 5)
        ));
    }

    private static Stream<Arguments> allSafeWalkCombinations() {
        return IntStream.range(0, 128).mapToObj(mask -> Arguments.of(
                mask,
                bit(mask, 0),
                bit(mask, 1),
                bit(mask, 2),
                bit(mask, 3),
                bit(mask, 4),
                bit(mask, 5),
                bit(mask, 6)
        ));
    }

    private static boolean bit(int value, int index) {
        return (value & (1 << index)) != 0;
    }

    private static String sequenceMessage(int blocksToEagle, int sequence, int step, int action) {
        return "blocksToEagle=" + blocksToEagle
                + ", sequence=" + sequence
                + ", step=" + step
                + ", action=" + action;
    }
}
