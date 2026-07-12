/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.aiming.raytrace;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.data.RotationWithVector;
import combatant.client.util.aiming.preference.LeastDifferencePreference;
import combatant.client.util.aiming.preference.RotationPreference;
import combatant.client.util.raycast.RaycastUtil;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Rotation-aware raytracing helpers.
 * <p>
 * Ported from LiquidBounce (CCBlueX), simplified for Combatant.
 */
public enum RotationRaytrace {
    ;

    public static final VisibilityPredicate OUTLINE_VISIBILITY =
            (eyes, target) -> RaycastUtil.hasLineOfSightPoint(eyes, target);

    public static RotationWithVector raytraceBox(
            Vec3 eyes,
            AABB box,
            double range,
            double wallsRange
    ) {
        return raytraceBox(eyes, box, range, wallsRange, OUTLINE_VISIBILITY,
                LeastDifferencePreference.LEAST_DISTANCE_TO_CURRENT_ROTATION, true);
    }

    public static RotationWithVector raytraceBox(
            Vec3 eyes,
            AABB box,
            double range,
            double wallsRange,
            VisibilityPredicate visibilityPredicate,
            RotationPreference rotationPreference,
            boolean prioritizeVisible
    ) {
        if (eyes == null || box == null) return null;
        final VisibilityPredicate vis =
                visibilityPredicate == null ? OUTLINE_VISIBILITY : visibilityPredicate;
        final RotationPreference pref =
                rotationPreference == null ? LeastDifferencePreference.LEAST_DISTANCE_TO_CURRENT_ROTATION : rotationPreference;

        double rangeSq = range * range;
        double wallsRangeSq = wallsRange * wallsRange;

        Vec3 preferredSpot = pref.getPreferredSpotOnBox(box, eyes, range);
        Vec3 preferredSpotOnBox = firstHit(box, eyes, preferredSpot);

        if (preferredSpotOnBox != null) {
            double preferredDist = eyes.distanceToSqr(preferredSpotOnBox);
            boolean visible = vis.isVisible(eyes, preferredSpotOnBox);
            if (preferredDist < wallsRangeSq || (visible && preferredDist < rangeSq)) {
                Rotation rot = Rotation.lookingAt(preferredSpot, eyes);
                return new RotationWithVector(rot, preferredSpot);
            }
        }

        BestRotationTracker tracker = new BestRotationTracker(pref, !prioritizeVisible);

        Vec3 nearestSpot = getNearestPoint(box, eyes);
        tracker.considerSpot(preferredSpot, box, eyes, vis, rangeSq, wallsRangeSq, nearestSpot);

        scanBoxPoints(eyes, box, spot -> tracker.considerSpot(
                spot, box, eyes, vis, rangeSq, wallsRangeSq, spot
        ));

        return tracker.bestVisible != null ? tracker.bestVisible : tracker.bestInvisible;
    }

    public static boolean canSeeBox(Vec3 eyes, AABB box, double range, double wallsRange) {
        if (eyes == null || box == null) return false;
        if (box.contains(eyes)) return true;

        double rangeSq = range * range;
        double wallsRangeSq = wallsRange * wallsRange;

        final boolean[] ok = {false};
        scanBoxPoints(eyes, box, pos -> {
            double dist = eyes.distanceToSqr(pos);
            if (dist > rangeSq) return;
            boolean visible = RaycastUtil.hasLineOfSightPoint(eyes, pos);
            if (!visible && dist > wallsRangeSq) return;
            ok[0] = true;
        });
        return ok[0];
    }

    private static Vec3 firstHit(AABB box, Vec3 from, Vec3 to) {
        Optional<Vec3> hit = box.clip(from, to);
        return hit.orElse(null);
    }

    private static Vec3 getNearestPoint(AABB box, Vec3 eyes) {
        double x = Mth.clamp(eyes.x, box.minX, box.maxX);
        double y = Mth.clamp(eyes.y, box.minY, box.maxY);
        double z = Mth.clamp(eyes.z, box.minZ, box.maxZ);
        return new Vec3(x, y, z);
    }

    private static void scanBoxPoints(Vec3 eyes, AABB box, Consumer<Vec3> fn) {
        boolean outside = projectPointsOnBox(eyes, box, 256, fn);
        if (!outside) {
            double[] steps = proportions();
            for (double x : steps) {
                for (double y : steps) {
                    for (double z : steps) {
                        Vec3 vec3 = new Vec3(
                                box.minX + box.getXsize() * x,
                                box.minY + box.getYsize() * y,
                                box.minZ + box.getZsize() * z
                        );
                        fn.accept(vec3);
                    }
                }
            }
        }
    }

    private static boolean projectPointsOnBox(Vec3 eyes, AABB box, int maxPoints, Consumer<Vec3> fn) {
        boolean outside = !box.contains(eyes);
        if (!outside) return false;

        double[] steps = proportions();
        int count = 0;

        // Faces: X-min/max
        for (double y : steps) {
            for (double z : steps) {
                fn.accept(new Vec3(box.minX, box.minY + box.getYsize() * y, box.minZ + box.getZsize() * z));
                if (++count >= maxPoints) return true;
                fn.accept(new Vec3(box.maxX, box.minY + box.getYsize() * y, box.minZ + box.getZsize() * z));
                if (++count >= maxPoints) return true;
            }
        }
        // Faces: Y-min/max
        for (double x : steps) {
            for (double z : steps) {
                fn.accept(new Vec3(box.minX + box.getXsize() * x, box.minY, box.minZ + box.getZsize() * z));
                if (++count >= maxPoints) return true;
                fn.accept(new Vec3(box.minX + box.getXsize() * x, box.maxY, box.minZ + box.getZsize() * z));
                if (++count >= maxPoints) return true;
            }
        }
        // Faces: Z-min/max
        for (double x : steps) {
            for (double y : steps) {
                fn.accept(new Vec3(box.minX + box.getXsize() * x, box.minY + box.getYsize() * y, box.minZ));
                if (++count >= maxPoints) return true;
                fn.accept(new Vec3(box.minX + box.getXsize() * x, box.minY + box.getYsize() * y, box.maxZ));
                if (++count >= maxPoints) return true;
            }
        }

        return true;
    }

    private static double[] proportions() {
        double[] steps = new double[10];
        double v = 0.05;
        for (int i = 0; i < steps.length; i++) {
            steps[i] = v;
            v += 0.1;
        }
        return steps;
    }

    public interface VisibilityPredicate {
        boolean isVisible(Vec3 eyesPos, Vec3 targetSpot);
    }

    private static class BestRotationTracker {
        private final RotationPreference comparator;
        private final boolean ignoreVisibility;
        private RotationWithVector bestVisible;
        private RotationWithVector bestInvisible;

        private BestRotationTracker(RotationPreference comparator, boolean ignoreVisibility) {
            this.comparator = comparator;
            this.ignoreVisibility = ignoreVisibility;
        }

        private void considerRotation(RotationWithVector rotation, boolean visible) {
            if (visible || ignoreVisibility) {
                if (bestVisible == null || comparator.compare(bestVisible.rotation(), rotation.rotation()) > 0) {
                    bestVisible = rotation;
                }
            } else {
                if (bestInvisible == null || comparator.compare(bestInvisible.rotation(), rotation.rotation()) > 0) {
                    bestInvisible = rotation;
                }
            }
        }

        private void considerSpot(
                Vec3 preferredSpot,
                AABB box,
                Vec3 eyes,
                VisibilityPredicate visibilityPredicate,
                double rangeSq,
                double wallsRangeSq,
                Vec3 spot
        ) {
            Vec3 raycastTarget = preferredSpot.subtract(eyes).scale(2.0).add(eyes);
            Vec3 spotOnBox = firstHit(box, eyes, raycastTarget);
            if (spotOnBox == null) return;

            double distSq = eyes.distanceToSqr(spotOnBox);
            boolean visible = visibilityPredicate.isVisible(eyes, spotOnBox);
            if ((!visible || distSq >= rangeSq) && distSq >= wallsRangeSq) return;

            Rotation rotation = Rotation.lookingAt(spot, eyes);
            considerRotation(new RotationWithVector(rotation, spot), visible);
        }
    }
}
