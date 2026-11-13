/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.common.impl;

import combatant.client.config.values.TargetFilterValue;
import combatant.client.features.relations.CategoryType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static combatant.client.config.common.impl.TargetFilters.*;
import static org.junit.jupiter.api.Assertions.*;

class TargetFiltersTest {
    private static TargetFilterValue filters() {
        return new TargetFilterValue("targets", Map.of(PLAYERS_ONLY, false), false);
    }

    private static boolean player(TargetFilterValue filters, CategoryType category, boolean naked) {
        return accepts(filters::get, true, category, naked, Disposition.FRIENDLY, false, false);
    }

    private static boolean mob(TargetFilterValue filters, Disposition disposition, boolean marked) {
        return accepts(filters::get, false, CategoryType.DEFAULT, false, disposition, marked, false);
    }

    @Test
    void everyPlayerSelectionCombinesAsUnionAndSupersedesHiddenExclusions() {
        List<String> only = List.of(ONLY_FRIENDS, ONLY_STAFF, ONLY_ENEMIES, ONLY_TEAMMATES, ONLY_OTHER_PLAYERS);
        List<String> ignore = List.of(IGNORE_FRIENDS, IGNORE_STAFF, IGNORE_ENEMIES, IGNORE_TEAMMATES, IGNORE_OTHER_PLAYERS);
        List<CategoryType> categories = List.of(CategoryType.FRIEND, CategoryType.STAFF, CategoryType.ENEMY,
                CategoryType.BEDWARS_SELF, CategoryType.DEFAULT);
        for (int selection = 0; selection < 32; selection++) {
            for (int exclusions = 0; exclusions < 32; exclusions++) {
                TargetFilterValue filters = filters();
                for (int i = 0; i < 5; i++) {
                    filters.set(only.get(i), (selection & (1 << i)) != 0);
                    filters.set(ignore.get(i), (exclusions & (1 << i)) != 0);
                }
                for (int i = 0; i < 5; i++) {
                    boolean expected = selection != 0 ? (selection & (1 << i)) != 0 : (exclusions & (1 << i)) == 0;
                    assertEquals(expected, player(filters, categories.get(i), false),
                            "selection=" + selection + ", exclusions=" + exclusions + ", category=" + categories.get(i));
                }
                assertEquals(player(filters, CategoryType.ENEMY, false), player(filters, CategoryType.BEDWARS_ENEMY, false));
                assertEquals(selection == 0, mob(filters, Disposition.HOSTILE, false));
                assertEquals(selection == 0, optionVisible(Section.PLAYERS, IGNORE_FRIENDS, filters::get, false));
            }
        }
    }

    @Test
    void mobSelectionsCombineAndMarkedExclusionsRemainIndependent() {
        List<String> only = List.of(ONLY_HOSTILE, ONLY_NEUTRAL, ONLY_FRIENDLY);
        List<String> ignore = List.of(IGNORE_HOSTILE, IGNORE_NEUTRAL, IGNORE_FRIENDLY);
        for (int selection = 0; selection < 8; selection++) {
            for (int exclusions = 0; exclusions < 8; exclusions++) {
                TargetFilterValue filters = filters();
                filters.set(IGNORE_MARKED, true);
                for (int i = 0; i < 3; i++) {
                    filters.set(only.get(i), (selection & (1 << i)) != 0);
                    filters.set(ignore.get(i), (exclusions & (1 << i)) != 0);
                }
                for (int i = 0; i < 3; i++) {
                    boolean expected = selection != 0 ? (selection & (1 << i)) != 0 : (exclusions & (1 << i)) == 0;
                    assertEquals(expected, mob(filters, Disposition.values()[i], false));
                    assertFalse(mob(filters, Disposition.values()[i], true));
                }
            }
        }
    }

    @Test
    void disablingSectionsRestoresTheirCandidatesWithoutResettingOptions() {
        TargetFilterValue filters = filters();
        filters.set(ONLY_STAFF, true);
        assertFalse(player(filters, CategoryType.FRIEND, false));
        filters.set(PLAYERS_ENABLED, false);
        assertTrue(player(filters, CategoryType.FRIEND, false));
        assertTrue(mob(filters, Disposition.NEUTRAL, false));
        assertFalse(optionVisible(Section.PLAYERS, ONLY_STAFF, filters::get, false));
        assertTrue(optionVisible(Section.PLAYERS, PLAYERS_ENABLED, filters::get, false));
        filters.set(PLAYERS_ENABLED, true);
        assertFalse(player(filters, CategoryType.FRIEND, false));
        assertTrue(player(filters, CategoryType.STAFF, false));

        filters.set(PLAYERS_ENABLED, false);
        filters.set(IGNORE_FRIENDLY, true);
        assertFalse(mob(filters, Disposition.FRIENDLY, false));
        filters.set(MOBS_ENABLED, false);
        assertTrue(mob(filters, Disposition.FRIENDLY, false));
        filters.set(IGNORE_MARKED, true);
        assertFalse(mob(filters, Disposition.FRIENDLY, true));
        filters.set(MARKED_ENABLED, false);
        assertTrue(mob(filters, Disposition.FRIENDLY, true));
    }

    @Test
    void armorMarkedAndInvisibleRulesIntersectWithCategorySelections() {
        TargetFilterValue filters = filters();
        filters.set(ONLY_FRIENDS, true);
        filters.set(IGNORE_NAKED, true);
        assertFalse(player(filters, CategoryType.FRIEND, true));
        filters.set(ONLY_NAKED, true);
        assertTrue(player(filters, CategoryType.FRIEND, true));
        assertFalse(player(filters, CategoryType.FRIEND, false));
        assertFalse(optionVisible(Section.PLAYERS, IGNORE_NAKED, filters::get, false));
        filters.set(IGNORE_MARKED, true);
        filters.set(ONLY_MARKED, true);
        assertFalse(player(filters, CategoryType.FRIEND, true));
        assertTrue(accepts(filters::get, true, CategoryType.FRIEND, true, Disposition.FRIENDLY, true, true));
        filters.set(IGNORE_INVISIBLE, true);
        assertFalse(accepts(filters::get, true, CategoryType.FRIEND, true, Disposition.FRIENDLY, true, true));
        assertFalse(optionVisible(Section.MARKED, IGNORE_MARKED, filters::get, false));
    }

    @Test
    void contradictoryLoadedScopesHaveDeterministicVisiblePrecedence() {
        TargetFilterValue filters = filters();
        filters.set(MOBS_ONLY, true);
        assertFalse(player(filters, CategoryType.DEFAULT, false));
        assertFalse(sectionVisible(Section.PLAYERS, filters::get, false));
        filters.set(ONLY_STAFF, true);
        assertFalse(player(filters, CategoryType.STAFF, false));
        assertTrue(mob(filters, Disposition.HOSTILE, false));
        filters.set(PLAYERS_ONLY, true);
        assertTrue(player(filters, CategoryType.STAFF, false));
        assertFalse(mob(filters, Disposition.HOSTILE, false));
        assertTrue(sectionVisible(Section.PLAYERS, filters::get, false));
        assertFalse(sectionVisible(Section.MOBS, filters::get, false));
        assertFalse(optionVisible(Section.GENERAL, MOBS_ONLY, filters::get, false));
        assertTrue(optionVisible(Section.GENERAL, PLAYERS_ONLY, filters::get, false));
    }

    @Test
    void playerAndMobCategoriesCanBeSelectedTogether() {
        TargetFilterValue filters = filters();
        filters.set(ONLY_ENEMIES, true);
        filters.set(ONLY_HOSTILE, true);
        filters.set(IGNORE_MARKED, true);
        assertTrue(player(filters, CategoryType.ENEMY, false));
        assertFalse(player(filters, CategoryType.DEFAULT, false));
        assertTrue(mob(filters, Disposition.HOSTILE, false));
        assertFalse(mob(filters, Disposition.HOSTILE, true));
        assertFalse(mob(filters, Disposition.FRIENDLY, false));
        assertTrue(sectionVisible(Section.PLAYERS, filters::get, false));
        assertTrue(sectionVisible(Section.MOBS, filters::get, false));
        filters.set(ONLY_ENEMIES, false);
        assertFalse(player(filters, CategoryType.ENEMY, false));
        assertTrue(mob(filters, Disposition.HOSTILE, false));
    }
}
