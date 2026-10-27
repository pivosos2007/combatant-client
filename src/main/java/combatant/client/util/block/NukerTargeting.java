/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Pure filtering and ordering rules for the Nuker's candidate blocks; no world access. */
public final class NukerTargeting {

    /** Which vertical slice of the radius may be broken, relative to the block the player's feet are in. */
    public enum Shape {
        /** Every block in range. */
        ALL,
        /** Level the terrain: the feet layer and everything above, never the floor. */
        FLATTEN,
        /** Only layers strictly above the feet block. */
        ABOVE_FEET
    }

    public enum Sort {
        CLOSEST,
        FARTHEST,
        TOP_DOWN,
        BOTTOM_UP
    }

    public enum FilterMode {
        OFF,
        WHITELIST,
        BLACKLIST
    }

    /** A block the scan found; {@code distanceSq} is eye-to-block-centre squared. */
    public record Candidate(int x, int y, int z, double distanceSq, String blockId) {
    }

    private NukerTargeting() {
    }

    public static boolean allowedByShape(Shape shape, int feetY, int y) {
        return switch (shape) {
            case ALL -> true;
            case FLATTEN -> y >= feetY;
            case ABOVE_FEET -> y > feetY;
        };
    }

    /** {@code ids} holds namespaced ids; an empty list never restricts a whitelist-less configuration. */
    public static boolean allowedByFilter(FilterMode mode, Set<String> ids, String blockId) {
        return switch (mode) {
            case OFF -> true;
            case WHITELIST -> ids.contains(blockId);
            case BLACKLIST -> !ids.contains(blockId);
        };
    }

    /** Ordering with a fully deterministic tiebreak so a stable scan never reshuffles targets. */
    public static Comparator<Candidate> comparator(Sort sort) {
        Comparator<Candidate> tie = Comparator.comparingInt(Candidate::y)
                .thenComparingInt(Candidate::x)
                .thenComparingInt(Candidate::z);
        return switch (sort) {
            case CLOSEST -> Comparator.comparingDouble(Candidate::distanceSq).thenComparing(tie);
            case FARTHEST -> Comparator.comparingDouble(Candidate::distanceSq).reversed().thenComparing(tie);
            case TOP_DOWN -> Comparator.comparingInt(Candidate::y).reversed()
                    .thenComparingDouble(Candidate::distanceSq).thenComparing(tie);
            case BOTTOM_UP -> Comparator.comparingInt(Candidate::y)
                    .thenComparingDouble(Candidate::distanceSq).thenComparing(tie);
        };
    }

    /**
     * Applies range, shape and block filters, orders what is left and keeps at most {@code limit}.
     *
     * @param maxDistanceSq candidates farther than this (eye to block centre) are dropped
     */
    public static List<Candidate> select(Collection<Candidate> candidates, double maxDistanceSq, Shape shape,
                                         int feetY, FilterMode filterMode, Set<String> filterIds, Sort sort,
                                         int limit) {
        List<Candidate> kept = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.distanceSq() > maxDistanceSq) continue;
            if (!allowedByShape(shape, feetY, candidate.y())) continue;
            if (!allowedByFilter(filterMode, filterIds, candidate.blockId())) continue;
            kept.add(candidate);
        }
        kept.sort(comparator(sort));
        int cap = Math.max(0, limit);
        return kept.size() > cap ? new ArrayList<>(kept.subList(0, cap)) : kept;
    }
}
