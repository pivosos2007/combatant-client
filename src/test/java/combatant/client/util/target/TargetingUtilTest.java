/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.target;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TargetingUtilTest {
    @Test
    void noTargetsAfterDeathDoesNotQueryImmutableListWithNull() {
        assertNull(TargetingUtil.selectPreferredTarget(List.of(), null, null, candidate -> {
            fail("An empty candidate list must not run reachability checks");
            return true;
        }));
    }

    @Test
    void missingPreferredTargetsFallsBackForAllCandidateListTypes() {
        List<String> targets = List.of("first", "second", "third");
        for (List<String> candidates : List.of(targets, List.copyOf(targets), new ArrayList<>(targets))) {
            assertEquals("first", TargetingUtil.selectPreferredTarget(candidates, null, null, candidate -> {
                assertNotNull(candidate);
                return true;
            }));
        }
        assertEquals("only", TargetingUtil.selectPreferredTarget(List.of("only"), null, null, candidate -> true));
    }

    @Test
    void keepsAuraTargetAheadOfPreviousAndCandidateOrder() {
        assertEquals("aura", TargetingUtil.selectPreferredTarget(
                List.of("first", "previous", "aura"), "aura", "previous", candidate -> true));
    }

    @Test
    void keepsPreviousTargetWhenAuraTargetIsMissingOrUnreachable() {
        List<String> candidates = List.of("first", "previous", "aura");
        assertEquals("previous", TargetingUtil.selectPreferredTarget(
                candidates, null, "previous", candidate -> true));
        assertEquals("previous", TargetingUtil.selectPreferredTarget(
                candidates, "aura", "previous", candidate -> !candidate.equals("aura")));
    }

    @Test
    void ignoresStalePreferencesAndChoosesFirstReachableCandidate() {
        assertEquals("reachable", TargetingUtil.selectPreferredTarget(
                List.of("blocked", "reachable", "later"), "stale-aura", "stale-previous", candidate -> {
                    assertFalse(candidate.startsWith("stale"));
                    return !candidate.equals("blocked");
                }));
    }

    @Test
    void noReachableCandidatesReturnsNoTarget() {
        assertNull(TargetingUtil.selectPreferredTarget(
                List.of("blocked"), null, null, candidate -> false));
        assertNull(TargetingUtil.selectPreferredTarget(
                List.of("blocked"), "blocked", null, candidate -> false));
        assertNull(TargetingUtil.selectPreferredTarget(
                List.of(), "stale-aura", "stale-previous", candidate -> true));
    }
}
