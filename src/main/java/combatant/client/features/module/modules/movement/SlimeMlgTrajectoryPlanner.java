/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.movement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure, block-grid-aware trajectory planner. The caller must still validate real block states,
 * visibility, range and rotation before interacting. Nothing is placed based on this prediction alone.
 */
final class SlimeMlgTrajectoryPlanner {
    private static final double HEIGHT_EPSILON = 0.035;
    // Require a meaningful part of the feet hitbox on the block at impact. Tiny
    // corner intersections are valid AABB math but unreliable placement targets.
    private static final double MIN_CONTACT_FRACTION = 0.09;
    private static final double MIN_FOLLOWTHROUGH_FRACTION = 0.02;
    private static final double FOLLOWTHROUGH_TICKS = 0.5;

    private record ColumnKey(int x, int supportY, int z) { }
    record Frame(double x, double feetY, double z, boolean onGround) { }
    /** coverage is a stability score, not a swept/union area; contactArea is the
     * actual hitbox overlap at the predicted instant the slime top is crossed. */
    record Candidate(int x, int supportY, int z, int contactTick, double coverage,
                     double peakY, double contactArea) { }

    private SlimeMlgTrajectoryPlanner() { }

    /**
     * Scan descending intersections with the top of a hypothetical full slime block.
     * The actual supporting block and placement reach are verified separately by NoFall.
     * Requiring a simulated air-to-ground transition loses jumps from ledges, long drops
     * and frames where Minecraft has already snapped the player to an adjacent column.
     */
    static List<Candidate> candidates(List<Frame> frames, double playerWidth, double minHeight) {
        return candidates(frames, playerWidth, minHeight, Double.NaN);
    }

    /** Keep the previously observed apex when the future-only simulation starts in descent. */
    static List<Candidate> candidates(List<Frame> frames, double playerWidth, double minHeight,
                                       double observedPeakFeetY) {
        if (frames == null || frames.size() < 2 || !Double.isFinite(playerWidth)
                || playerWidth <= 0 || !Double.isFinite(minHeight) || minHeight < 0) return List.of();
        double peakY = Double.NEGATIVE_INFINITY;
        for (Frame frame : frames) {
            if (!finite(frame)) return List.of();
            peakY = Math.max(peakY, frame.feetY);
        }
        if (Double.isFinite(observedPeakFeetY)) peakY = Math.max(peakY, observedPeakFeetY);
        double half = playerWidth * 0.5;
        double fullArea = playerWidth * playerWidth;
        int lowestSupport = Integer.MIN_VALUE;
        for (int tick = 1; tick < frames.size(); tick++) {
            if (frames.get(tick).onGround && !frames.get(tick - 1).onGround) {
                lowestSupport = (int) Math.floor(frames.get(tick).feetY - 0.025);
                break;
            }
        }
        List<Candidate> found = new ArrayList<>();
        Set<ColumnKey> seen = new HashSet<>();
        for (int tick = 1; tick < frames.size(); tick++) {
            Frame previous = frames.get(tick - 1), next = frames.get(tick);
            double descent = previous.feetY - next.feetY;
            if (descent <= 1.0E-5) continue;

            int firstSupport = (int) Math.ceil(next.feetY - 2.0 - 1.0E-6);
            int lastSupport = (int) Math.floor(previous.feetY - 2.0 + 1.0E-6);
            for (int supportY = firstSupport; supportY <= lastSupport; supportY++) {
                if (supportY < lowestSupport) continue;
                double slimeTop = supportY + 2.0;
                if (previous.feetY < slimeTop - HEIGHT_EPSILON || next.feetY > slimeTop + 1.0E-6
                        || !SlimeMlgPlanningRules.heightEligible(peakY, supportY + 1.0, minHeight)) continue;
                double fraction = Math.max(0.0, Math.min(1.0,
                        (previous.feetY - slimeTop) / descent));
                double x = previous.x + (next.x - previous.x) * fraction;
                double z = previous.z + (next.z - previous.z) * fraction;
                int minX = (int) Math.floor(x - half), maxX = (int) Math.floor(x + half);
                int minZ = (int) Math.floor(z - half), maxZ = (int) Math.floor(z + half);
                for (int blockX = minX; blockX <= maxX; blockX++) {
                    for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                        double contact = overlapArea(x, z, half, blockX, blockZ);
                        if (contact < fullArea * MIN_CONTACT_FRACTION) continue;
                        // Only the direction at touchdown matters. The OLD scorer
                        // gave weight to the position BEFORE impact, which could
                        // select a block already sliding behind the player.
                        double stepX = next.x - previous.x;
                        double stepZ = next.z - previous.z;
                        if (tick + 1 < frames.size()) {
                            Frame after = frames.get(tick + 1);
                            stepX = (stepX + after.x - next.x) * 0.5;
                            stepZ = (stepZ + after.z - next.z) * 0.5;
                        }
                        double followthrough = overlapArea(
                                x + stepX * FOLLOWTHROUGH_TICKS,
                                z + stepZ * FOLLOWTHROUGH_TICKS,
                                half, blockX, blockZ);
                        if (followthrough < fullArea * MIN_FOLLOWTHROUGH_FRACTION) continue;
                        // The worst phase matters more than large overlap before the
                        // feet reach the pad. Scores stay within [0, fullArea].
                        double coverage = contact * 0.50 + followthrough * 0.30
                                + Math.min(contact, followthrough) * 0.20;
                        // The first descending crossing is the only relevant one;
                        // a later crossing cannot rescue a missed earlier collision.
                        if (seen.add(new ColumnKey(blockX, supportY, blockZ))) {
                            found.add(new Candidate(blockX, supportY, blockZ, tick,
                                    coverage, peakY, contact));
                        }
                    }
                }
            }
        }
        // Quality first within the same imminent landing, never coordinate order.
        // A far-future support cannot outrank a comparable imminent contact.
        found.sort(Comparator
                .comparingInt((Candidate c) -> (c.contactTick() - 1) / 2)
                .thenComparing(Comparator.comparingDouble(Candidate::coverage).reversed())
                .thenComparingInt(Candidate::contactTick)
                .thenComparingInt(Candidate::x).thenComparingInt(Candidate::z));
        return List.copyOf(found);
    }

    /** A small corner contact is acceptable just before impact, but not as an
     * early speculative placement while horizontal input can still change. */
    static Candidate choose(List<Candidate> viable, Candidate previous, Candidate aligned) {
        if (viable == null || viable.isEmpty()) return null;
        int earliestGroup = viable.stream().mapToInt(c -> (c.contactTick() - 1) / 2)
                .min().orElse(Integer.MAX_VALUE);
        Candidate strongest = null;
        double highestCoverage = 0.0;
        for (Candidate candidate : viable) {
            if ((candidate.contactTick() - 1) / 2 != earliestGroup) continue;
            if (strongest == null || candidate.coverage() > highestCoverage) {
                strongest = candidate;
                highestCoverage = candidate.coverage();
            }
        }
        if (strongest == null) return null;
        if (aligned != null && viable.contains(aligned)
                && aligned.contactTick() <= 2
                && (aligned.contactTick() - 1) / 2 == earliestGroup
                && aligned.coverage() >= highestCoverage * 0.88) return aligned;
        if (previous != null && viable.contains(previous)
                && (previous.contactTick() - 1) / 2 == earliestGroup
                && previous.coverage() >= highestCoverage * 0.77) return previous;
        if (aligned != null && viable.contains(aligned)
                && (aligned.contactTick() - 1) / 2 == earliestGroup
                && aligned.coverage() >= highestCoverage * 0.88) return aligned;
        return strongest;
    }

    static boolean readyToCommit(Candidate candidate, double playerWidth) {
        if (candidate == null || !Double.isFinite(playerWidth) || playerWidth <= 0) return false;
        return candidate.contactTick() <= 2
                || candidate.contactArea() >= playerWidth * playerWidth * 0.16;
    }

    /** Only commit close to impact; at high horizontal velocity even 4 ticks is a poor prediction. */
    static int commitWindow(double velocityX, double velocityZ) {
        double horizontal = Math.hypot(velocityX, velocityZ);
        if (!Double.isFinite(horizontal)) return 0;
        return horizontal > 0.40 ? 3 : horizontal > 0.23 ? 4 : 6;
    }

    static boolean clearForPlacement(double actualFeetY, int supportY) {
        return Double.isFinite(actualFeetY) && actualFeetY >= supportY + 2.0 + 0.025;
    }

    static double overlapArea(double x, double z, double half, int blockX, int blockZ) {
        double overlapX = Math.max(0.0, Math.min(x + half, blockX + 1.0) - Math.max(x - half, blockX));
        double overlapZ = Math.max(0.0, Math.min(z + half, blockZ + 1.0) - Math.max(z - half, blockZ));
        return overlapX * overlapZ;
    }

    private static boolean finite(Frame f) {
        return Double.isFinite(f.x) && Double.isFinite(f.feetY) && Double.isFinite(f.z);
    }
}
