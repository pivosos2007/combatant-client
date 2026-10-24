/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block;

import combatant.client.util.block.NukerTargeting.Candidate;
import combatant.client.util.block.NukerTargeting.FilterMode;
import combatant.client.util.block.NukerTargeting.Shape;
import combatant.client.util.block.NukerTargeting.Sort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NukerTargetingTest {

    private static Candidate c(int x, int y, int z, double d, String id) {
        return new Candidate(x, y, z, d, id);
    }

    private static final String STONE = "minecraft:stone";
    private static final String DIRT = "minecraft:dirt";

    @Test
    void shapeRulesRelativeToFeet() {
        assertTrue(NukerTargeting.allowedByShape(Shape.ALL, 64, 10));
        assertFalse(NukerTargeting.allowedByShape(Shape.FLATTEN, 64, 63));
        assertTrue(NukerTargeting.allowedByShape(Shape.FLATTEN, 64, 64));
        assertTrue(NukerTargeting.allowedByShape(Shape.FLATTEN, 64, 70));
        assertFalse(NukerTargeting.allowedByShape(Shape.ABOVE_FEET, 64, 64));
        assertTrue(NukerTargeting.allowedByShape(Shape.ABOVE_FEET, 64, 65));
    }

    @Test
    void filterModes() {
        Set<String> ids = Set.of(STONE);
        assertTrue(NukerTargeting.allowedByFilter(FilterMode.OFF, ids, DIRT));
        assertTrue(NukerTargeting.allowedByFilter(FilterMode.WHITELIST, ids, STONE));
        assertFalse(NukerTargeting.allowedByFilter(FilterMode.WHITELIST, ids, DIRT));
        assertFalse(NukerTargeting.allowedByFilter(FilterMode.BLACKLIST, ids, STONE));
        assertTrue(NukerTargeting.allowedByFilter(FilterMode.BLACKLIST, ids, DIRT));
        assertFalse(NukerTargeting.allowedByFilter(FilterMode.WHITELIST, Set.of(), STONE));
        assertTrue(NukerTargeting.allowedByFilter(FilterMode.BLACKLIST, Set.of(), STONE));
    }

    @Test
    void closestOrdersByDistanceWithDeterministicTiebreak() {
        List<Candidate> out = NukerTargeting.select(
                List.of(c(2, 5, 0, 4.0, STONE), c(1, 5, 0, 1.0, STONE), c(0, 4, 2, 4.0, STONE), c(0, 5, 2, 4.0, STONE)),
                100.0, Shape.ALL, 0, FilterMode.OFF, Set.of(), Sort.CLOSEST, 10);
        assertEquals(List.of(
                c(1, 5, 0, 1.0, STONE), c(0, 4, 2, 4.0, STONE), c(0, 5, 2, 4.0, STONE), c(2, 5, 0, 4.0, STONE)), out);
    }

    @Test
    void farthestReversesDistance() {
        List<Candidate> out = NukerTargeting.select(
                List.of(c(0, 0, 0, 1.0, STONE), c(1, 0, 0, 9.0, STONE), c(2, 0, 0, 4.0, STONE)),
                100.0, Shape.ALL, 0, FilterMode.OFF, Set.of(), Sort.FARTHEST, 10);
        assertEquals(List.of(9.0, 4.0, 1.0), out.stream().map(Candidate::distanceSq).toList());
    }

    @Test
    void topDownAndBottomUpOrderByLayer() {
        List<Candidate> in = List.of(c(0, 3, 0, 9.0, STONE), c(0, 5, 0, 25.0, STONE), c(1, 5, 0, 1.0, STONE));
        List<Candidate> top = NukerTargeting.select(in, 100.0, Shape.ALL, 0, FilterMode.OFF, Set.of(),
                Sort.TOP_DOWN, 10);
        assertEquals(1, top.get(0).x());
        assertEquals(5, top.get(0).y());
        assertEquals(3, top.get(2).y());
        List<Candidate> bottom = NukerTargeting.select(in, 100.0, Shape.ALL, 0, FilterMode.OFF, Set.of(),
                Sort.BOTTOM_UP, 10);
        assertEquals(3, bottom.get(0).y());
        assertEquals(1.0, bottom.get(1).distanceSq());
    }

    @Test
    void rangeShapeFilterAndLimitCompose() {
        List<Candidate> in = List.of(
                c(0, 63, 0, 1.0, STONE),   // below feet: shape drops it
                c(0, 64, 0, 2.0, DIRT),    // filtered by whitelist
                c(1, 64, 0, 3.0, STONE),
                c(2, 65, 0, 5.0, STONE),
                c(3, 66, 0, 6.0, STONE),
                c(9, 66, 0, 200.0, STONE)); // out of range
        List<Candidate> out = NukerTargeting.select(in, 50.0, Shape.FLATTEN, 64, FilterMode.WHITELIST,
                Set.of(STONE), Sort.CLOSEST, 2);
        assertEquals(List.of(c(1, 64, 0, 3.0, STONE), c(2, 65, 0, 5.0, STONE)), out);
    }

    @Test
    void zeroOrNegativeLimitYieldsNothing() {
        List<Candidate> in = List.of(c(0, 0, 0, 1.0, STONE));
        assertTrue(NukerTargeting.select(in, 10.0, Shape.ALL, 0, FilterMode.OFF, Set.of(), Sort.CLOSEST, 0).isEmpty());
        assertTrue(NukerTargeting.select(in, 10.0, Shape.ALL, 0, FilterMode.OFF, Set.of(), Sort.CLOSEST, -3).isEmpty());
    }
}
