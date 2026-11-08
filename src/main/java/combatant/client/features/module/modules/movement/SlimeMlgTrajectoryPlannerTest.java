/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.movement;

import java.util.ArrayList;
import java.util.List;

/** Standalone regression suite; run with javac/java without Minecraft classes. */
public final class SlimeMlgTrajectoryPlannerTest {
    private static int assertions;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    // A jump whose feet start at Y=65.0, reach 66.2 and return to ground at 65.0.
    private static List<SlimeMlgTrajectoryPlanner.Frame> jump(double x, double z, double dx, double dz) {
        double[] heights = {65.0,65.42,65.75,65.99,66.15,66.21,66.18,66.07,65.89,65.60,65.22,65.0};
        List<SlimeMlgTrajectoryPlanner.Frame> frames = new ArrayList<>();
        for (int i = 0; i < heights.length; i++) {
            frames.add(new SlimeMlgTrajectoryPlanner.Frame(x + i * dx, heights[i], z + i * dz,
                    i == 0 || i == heights.length - 1));
        }
        return frames;
    }

    private static SlimeMlgTrajectoryPlanner.Candidate best(List<SlimeMlgTrajectoryPlanner.Frame> path, double height) {
        var candidates = SlimeMlgTrajectoryPlanner.candidates(path, .6, height);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static void stationaryJump() {
        var c = best(jump(4.5, 2.5, 0, 0), 1.0);
        check(c != null, "one-block jump must be reachable");
        check(c.x() == 4 && c.z() == 2 && c.supportY() == 64, "ground column wrong");
        check(c.contactTick() >= 6 && c.contactTick() <= 9, "slime contact not ground impact");
        check(best(jump(4.5, 2.5, 0, 0), 1.3) == null, "height threshold ignored");
    }

    private static void fourWayCorner() {
        var results = SlimeMlgTrajectoryPlanner.candidates(jump(5.0, 6.0, 0, 0), .6, 1.0);
        check(results.size() == 4, "must offer four separate supports at a corner");
        for (var c : results) check(c.coverage() > 0.02, "corner candidate not covered");
    }

    private static void crossingBoundary() {
        var result = SlimeMlgTrajectoryPlanner.candidates(jump(1.5, 3.5, .095, 0), .6, 1.0);
        check(!result.isEmpty(), "moving jump missing");
        check(result.get(0).x() == 2, "target is prior column instead of at slime contact");
    }

    private static void noFalsePlacement() {
        var stationary = List.of(new SlimeMlgTrajectoryPlanner.Frame(2.5, 65.0, 2.5, true),
                new SlimeMlgTrajectoryPlanner.Frame(2.5, 65.0, 2.5, true),
                new SlimeMlgTrajectoryPlanner.Frame(2.5, 65.0, 2.5, true));
        check(best(stationary, 0) == null, "standing still must not place");
        var shallow = new ArrayList<>(jump(4.5, 2.5, 0, 0));
        shallow.replaceAll(f -> new SlimeMlgTrajectoryPlanner.Frame(f.x(),
                65.0 + (f.feetY() - 65.0) * 0.65, f.z(), f.onGround()));
        check(best(shallow, 0) == null, "not enough clearance to fit slime");
        check(!SlimeMlgTrajectoryPlanner.clearForPlacement(65.5, 64), "player intersects block");
        check(SlimeMlgTrajectoryPlanner.clearForPlacement(66.15, 64), "cleared block is placeable");
        check(!SlimeMlgTrajectoryPlanner.clearForPlacement(66.0, 64), "edge contact is not clearance");
        check(SlimeMlgTrajectoryPlanner.candidates(null, .6, 1).isEmpty(), "null trace");
        check(SlimeMlgTrajectoryPlanner.candidates(jump(1,1,0,0), .6, Double.NaN).isEmpty(), "invalid threshold");
    }

    private static void fallingAndUnexpectedDrift() {
        List<SlimeMlgTrajectoryPlanner.Frame> fall = new ArrayList<>();
        double[] ys = {71.2,70.7,70.1,69.4,68.6,67.7,66.7,65.8,65.0};
        for (int i = 0; i < ys.length; i++) fall.add(new SlimeMlgTrajectoryPlanner.Frame(
                4.9 + .12 * i, ys[i], 3.99, i == ys.length - 1));
        var result = SlimeMlgTrajectoryPlanner.candidates(fall, .6, 2.0);
        check(!result.isEmpty(), "fall from height must work");
        check(result.get(0).contactTick() < ys.length - 1, "must collide with slime before ground");
        check(result.stream().allMatch(c -> c.coverage() > 0), "zero-coverage column");
        check(SlimeMlgTrajectoryPlanner.commitWindow(.02,0) == 6, "slow commit");
        check(SlimeMlgTrajectoryPlanner.commitWindow(.3,0) == 4, "medium commit");
        check(SlimeMlgTrajectoryPlanner.commitWindow(.5,0) == 3, "fast commit");
        check(SlimeMlgTrajectoryPlanner.commitWindow(Double.NaN,0) == 0, "invalid speed");
    }


    private static void lateDescentRemembersApex() {
        // A tall jump reached Y=67.21, but future prediction starts at Y=66.72.
        // At minHeight=2 the earlier apex is essential to decide whether to place.
        var late = List.of(
                new SlimeMlgTrajectoryPlanner.Frame(4.50, 66.72, 2.50, false),
                new SlimeMlgTrajectoryPlanner.Frame(4.52, 66.42, 2.51, false),
                new SlimeMlgTrajectoryPlanner.Frame(4.54, 66.02, 2.52, false),
                new SlimeMlgTrajectoryPlanner.Frame(4.56, 65.75, 2.53, false));
        var withoutHistory = SlimeMlgTrajectoryPlanner.candidates(late, .6, 2.0);
        var withHistory = SlimeMlgTrajectoryPlanner.candidates(late, .6, 2.0, 67.21);
        check(withoutHistory.stream().noneMatch(c -> c.supportY() == 64),
                "future-only projection should lose earlier apex");
        check(withHistory.stream().anyMatch(c -> c.supportY() == 64),
                "observed apex must preserve mid-descent MLG eligibility");
        check(SlimeMlgTrajectoryPlanner.candidates(late, .6, 2.3, 67.21).stream()
                .noneMatch(c -> c.supportY() == 64), "minimum height is enforced");
    }

    private static void randomizedFootprints() {
        java.util.Random random = new java.util.Random(297);
        for (int run = 0; run < 2000; run++) {
            double x = 2.4 + random.nextDouble() * 1.2;
            double z = 3.4 + random.nextDouble() * 1.2;
            double vx = (random.nextDouble() - 0.5) * .22;
            double vz = (random.nextDouble() - 0.5) * .22;
            List<SlimeMlgTrajectoryPlanner.Frame> frames = jump(x, z, vx, vz);
            var candidates = SlimeMlgTrajectoryPlanner.candidates(frames, .6, 1.0);
            for (var c : candidates) {
                check(c.contactTick() > 0 && c.contactTick() < frames.size() - 1,
                        "random: illegal contact tick");
                check(c.coverage() > 0.0, "random: candidate misses footprint");
                var a = frames.get(c.contactTick() - 1);
                var b = frames.get(c.contactTick());
                double factor = (a.feetY() - (c.supportY() + 2.0)) / (a.feetY() - b.feetY());
                double impactX = a.x() + factor * (b.x() - a.x());
                double impactZ = a.z() + factor * (b.z() - a.z());
                check(SlimeMlgTrajectoryPlanner.overlapArea(impactX, impactZ, .3, c.x(), c.z()) > .007,
                        "random: placed block misses actual predicted hitbox");
            }
        }
    }

    private static void incompleteLandingProjection() {
        var frames = List.of(
                new SlimeMlgTrajectoryPlanner.Frame(3.99, 67.10, 5.01, false),
                new SlimeMlgTrajectoryPlanner.Frame(3.99, 66.45, 5.01, false),
                new SlimeMlgTrajectoryPlanner.Frame(3.99, 65.83, 5.01, false));
        var found = SlimeMlgTrajectoryPlanner.candidates(frames, .6, 1.0);
        check(!found.isEmpty(), "no onGround in horizon must not suppress a future slime contact");
        check(found.stream().anyMatch(c -> c.supportY() == 64), "should inspect support below the contact level");
        check(found.stream().filter(c -> c.supportY() == 64).count() == 4,
                "an exactly four-way footprint must preserve all columns");
        var noDrop = List.of(
                new SlimeMlgTrajectoryPlanner.Frame(3.5, 65.3, 5.5, false),
                new SlimeMlgTrajectoryPlanner.Frame(3.5, 65.2, 5.5, false));
        check(SlimeMlgTrajectoryPlanner.candidates(noDrop, .6, 0).isEmpty(),
                "a block may not be placed where the player is already standing");
    }

    /** The legacy comparator fell back to increasing X/Z on equal contact area,
     * picking the block BEHIND a player crossing an exact four-way corner. */
    private static void movingCornerPrefersDirectionOfTravel() {
        var forward = List.of(
                new SlimeMlgTrajectoryPlanner.Frame(4.9, 66.4, 5.9, false),
                new SlimeMlgTrajectoryPlanner.Frame(5.1, 65.6, 6.1, false),
                new SlimeMlgTrajectoryPlanner.Frame(5.3, 65.1, 6.3, false));
        var forwardCandidates = SlimeMlgTrajectoryPlanner.candidates(forward, .6, 0, 67.0);
        check(!forwardCandidates.isEmpty(), "diagonal crossing lost candidates");
        check(forwardCandidates.get(0).x() == 5 && forwardCandidates.get(0).z() == 6,
                "moving forward should not place behind the four-way corner");
        var reverse = List.of(
                new SlimeMlgTrajectoryPlanner.Frame(5.1, 66.4, 6.1, false),
                new SlimeMlgTrajectoryPlanner.Frame(4.9, 65.6, 5.9, false),
                new SlimeMlgTrajectoryPlanner.Frame(4.7, 65.1, 5.7, false));
        var reverseCandidates = SlimeMlgTrajectoryPlanner.candidates(reverse, .6, 0, 67.0);
        check(!reverseCandidates.isEmpty(), "reverse diagonal crossing lost candidates");
        check(reverseCandidates.get(0).x() == 4 && reverseCandidates.get(0).z() == 5,
                "moving backward should choose the opposite quadrant");
    }

    private static void tinySideOverlapIsNotReliableLanding() {
        var trace = List.of(
                new SlimeMlgTrajectoryPlanner.Frame(5.27, 66.4, 6.5, false),
                new SlimeMlgTrajectoryPlanner.Frame(5.32, 65.6, 6.5, false),
                new SlimeMlgTrajectoryPlanner.Frame(5.37, 65.1, 6.5, false));
        var candidates = SlimeMlgTrajectoryPlanner.candidates(trace, .6, 0, 67.0);
        check(!candidates.isEmpty(), "reliable central support lost");
        check(candidates.stream().noneMatch(c -> c.x() == 4),
                "a few pixels of side-hitbox intersection are not a reliable MLG");
        for (var c : candidates) {
            check(c.contactArea() >= 0.6 * 0.6 * 0.09 - 1e-8,
                    "candidate violates minimum contact area");
        }
        check(!SlimeMlgTrajectoryPlanner.readyToCommit(null, .6), "null cannot commit");
        var thinEarly = new SlimeMlgTrajectoryPlanner.Candidate(4, 64, 6, 4,
                .042, 67.0, .04);
        var thinNow = new SlimeMlgTrajectoryPlanner.Candidate(4, 64, 6, 2,
                .042, 67.0, .04);
        check(!SlimeMlgTrajectoryPlanner.readyToCommit(thinEarly, .6),
                "small edge intersection must not trigger speculative early placement");
        check(SlimeMlgTrajectoryPlanner.readyToCommit(thinNow, .6),
                "imminent narrow landing should still be attempted when geometry allows");
    }

    private static void rankIsStableAtFourColumns() {
        java.util.Random rand = new java.util.Random(20261107);
        for (int i = 0; i < 1000; i++) {
            double dx = (rand.nextDouble() - .5) * .34;
            double dz = (rand.nextDouble() - .5) * .34;
            var frames = List.of(
                    new SlimeMlgTrajectoryPlanner.Frame(5 - dx * .5, 66.4, 6 - dz * .5, false),
                    new SlimeMlgTrajectoryPlanner.Frame(5 + dx * .5, 65.6, 6 + dz * .5, false),
                    new SlimeMlgTrajectoryPlanner.Frame(5 + dx * 1.5, 65.1, 6 + dz * 1.5, false));
            var candidates = SlimeMlgTrajectoryPlanner.candidates(frames, .6, 0, 67);
            check(!candidates.isEmpty(), "jitter should not suppress all four-way candidates");
            for (int k = 1; k < candidates.size(); k++) {
                var prev = candidates.get(k - 1);
                var next = candidates.get(k);
                int prevBucket = (prev.contactTick() - 1) / 2;
                int nextBucket = (next.contactTick() - 1) / 2;
                check(prevBucket < nextBucket || (prevBucket == nextBucket
                                && prev.coverage() + 1e-9 >= next.coverage()),
                        "rank comparator prefers weaker support in the same impact window");
            }
        }
    }

    private static void stableChoiceBetweenBlocks() {
        var four = SlimeMlgTrajectoryPlanner.candidates(jump(5.0, 6.0, 0, 0), .6, 1.0);
        check(four.size() == 4, "four-way landing must offer four candidates");
        for (var prior : four) {
            check(SlimeMlgTrajectoryPlanner.choose(four, prior, null).equals(prior),
                    "four-way selection lost its previous valid target");
            check(SlimeMlgTrajectoryPlanner.choose(four, null, prior).equals(prior),
                    "four-way selection ignored the currently aligned target");
        }
        var primary = new SlimeMlgTrajectoryPlanner.Candidate(4, 64, 5, 2, .26, 67, .26);
        var weaker = new SlimeMlgTrajectoryPlanner.Candidate(5, 64, 5, 2, .21, 67, .21);
        var invalid = new SlimeMlgTrajectoryPlanner.Candidate(6, 64, 5, 2, .08, 67, .08);
        check(SlimeMlgTrajectoryPlanner.choose(List.of(primary, weaker), weaker, null).equals(weaker),
                "two-way choice should retain a viable near-tie");
        var alignedNearTie = new SlimeMlgTrajectoryPlanner.Candidate(5, 64, 5, 2, .24, 67, .24);
        check(SlimeMlgTrajectoryPlanner.choose(List.of(primary, alignedNearTie), primary, alignedNearTie)
                        .equals(alignedNearTie),
                "imminent server-aligned support must override stale aim");
        check(SlimeMlgTrajectoryPlanner.choose(List.of(primary, invalid), invalid, null).equals(primary),
                "stability must not override materially safer coverage");
        check(SlimeMlgTrajectoryPlanner.choose(List.of(primary, weaker), invalid, null).equals(primary),
                "stale prior must be ignored");
        check(SlimeMlgTrajectoryPlanner.choose(List.of(), primary, primary) == null,
                "no viable candidate must not be committed");
    }

    public static void main(String[] args) {
        movingCornerPrefersDirectionOfTravel();
        tinySideOverlapIsNotReliableLanding();
        rankIsStableAtFourColumns();
        stableChoiceBetweenBlocks();
        stationaryJump();
        fourWayCorner();
        crossingBoundary();
        noFalsePlacement();
        fallingAndUnexpectedDrift();
        randomizedFootprints();
        incompleteLandingProjection();
        lateDescentRemembersApex();
        System.out.println("SlimeMlgTrajectoryPlannerTest: " + assertions + " assertions passed");
    }
}
