/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static combatant.client.config.common.impl.TargetFilters.*;
import static org.junit.jupiter.api.Assertions.*;

class TargetFilterValueTest {
    @Test
    void oldProfilesRetainExclusionsAndGainEnabledSections() {
        TargetFilterValue value = new TargetFilterValue("killaura_toggles", Map.of(PLAYERS_ONLY, false), false);
        value.fromJson(Map.of(IGNORE_FRIENDS, false, IGNORE_STAFF, true, IGNORE_MARKED, true));
        assertEquals("killaura_toggles", value.getName());
        assertFalse(value.get(PLAYERS_ONLY));
        assertFalse(value.get(IGNORE_FRIENDS));
        assertTrue(value.get(IGNORE_STAFF));
        assertTrue(value.get(IGNORE_MARKED));
        assertTrue(value.get(IGNORE_TEAMMATES));
        assertTrue(value.get(PLAYERS_ENABLED));
        assertTrue(value.get(MOBS_ENABLED));
        assertTrue(value.get(MARKED_ENABLED));
        assertFalse(value.get(ONLY_STAFF));
    }

    @Test
    void disabledGroupsAndTheirSelectionsRoundTripTogether() {
        TargetFilterValue source = new TargetFilterValue("targets", Map.of(), false);
        source.set(PLAYERS_ENABLED, false);
        source.set(ONLY_FRIENDS, true);
        source.set(ONLY_ENEMIES, true);
        source.set(ONLY_HOSTILE, true);
        source.set(IGNORE_MARKED, true);
        TargetFilterValue loaded = new TargetFilterValue("targets", Map.of(), false);
        loaded.fromJson(source.toJson());
        assertEquals(source.toJson(), loaded.toJson());
    }

    @Test
    void playerOnlyModulesCannotBeSwitchedToMobsByImportedProfiles() {
        TargetFilterValue value = new TargetFilterValue("autoWebTargets", Map.of(), true);
        value.fromJson(Map.of(PLAYERS_ONLY, false, MOBS_ONLY, true));
        assertTrue(value.get(PLAYERS_ONLY));
        assertFalse(value.get(MOBS_ONLY));
        assertFalse(sectionVisible(Section.MOBS, value::get, true));
        assertFalse(optionVisible(Section.GENERAL, PLAYERS_ONLY, value::get, true));
        assertFalse(optionVisible(Section.GENERAL, MOBS_ONLY, value::get, true));
    }
}
