/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiVanishLogicTest {

    @Test
    void namesMissingFromTabListAreHidden() {
        Set<String> hidden = AntiVanishLogic.hiddenNames(
                List.of("Alice", "Bob_1", "Ghost"), List.of("alice", "BOB_1"), "Me");
        assertEquals(Set.of("Ghost"), hidden);
    }

    @Test
    void selfSelectorsAndNoiseAreIgnored() {
        Set<String> hidden = AntiVanishLogic.hiddenNames(
                List.of("Me", "@a", "ab", " ", "has space", "WayTooLongUsername123"), List.of(), "me");
        assertTrue(hidden.isEmpty());
    }

    @Test
    void removalIsDesyncOnlyWhileEntityLingersAndTabEntryIsGone() {
        assertTrue(AntiVanishLogic.desyncAfterRemoval(true, false));
        assertFalse(AntiVanishLogic.desyncAfterRemoval(false, false));
        assertFalse(AntiVanishLogic.desyncAfterRemoval(true, true));
    }
}
