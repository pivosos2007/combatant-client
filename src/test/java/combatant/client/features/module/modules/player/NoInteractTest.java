/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class NoInteractTest {
    @ParameterizedTest(name = "state mask {0}")
    @MethodSource("allStateCombinations")
    void blockInteractionDecisionCoversEveryStateCombination(
            int mask,
            boolean noInteractEnabled,
            boolean onlyKillAura,
            boolean killAuraEnabled
    ) {
        boolean expected = noInteractEnabled && (!onlyKillAura || killAuraEnabled);

        assertEquals(
                expected,
                NoInteract.shouldBlockBlockInteraction(
                        noInteractEnabled,
                        onlyKillAura,
                        killAuraEnabled
                ),
                "unexpected decision for mask " + mask
        );
    }

    private static Stream<Arguments> allStateCombinations() {
        return IntStream.range(0, 8).mapToObj(mask -> Arguments.of(
                mask,
                bit(mask, 0),
                bit(mask, 1),
                bit(mask, 2)
        ));
    }

    private static boolean bit(int value, int index) {
        return (value & (1 << index)) != 0;
    }
}
