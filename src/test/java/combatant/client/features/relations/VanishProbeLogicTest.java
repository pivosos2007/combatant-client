/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.relations;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanishProbeLogicTest {

    @Test
    void namesMissingFromTabListAreHidden() {
        Set<String> hidden = VanishProbeLogic.hiddenNames(
                List.of("Alice", "Bob_1", "Ghost"), List.of("alice", "BOB_1"), "Me");
        assertEquals(Set.of("Ghost"), hidden);
    }

    @Test
    void selfSelectorsAndNoiseAreIgnored() {
        Set<String> hidden = VanishProbeLogic.hiddenNames(
                List.of("Me", "@a", "ab", " ", "has space", "WayTooLongUsername123"), List.of(), "me");
        assertTrue(hidden.isEmpty());
    }

    @Test
    void aNameIsConfirmedOnlyAfterConsecutiveProbesAndResetsWhenItReappears() {
        Map<String, Integer> streaks = new HashMap<>();
        assertTrue(VanishProbeLogic.confirmed(Set.of("Ghost"), streaks, 2).isEmpty());
        assertEquals(Set.of("Ghost"), VanishProbeLogic.confirmed(Set.of("Ghost"), streaks, 2));
        // Back in the tab list for one probe: the streak starts over.
        assertTrue(VanishProbeLogic.confirmed(Set.of(), streaks, 2).isEmpty());
        assertTrue(VanishProbeLogic.confirmed(Set.of("Ghost"), streaks, 2).isEmpty());
    }
}
