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

package combatant.client.util.aiming.point;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.entity.simulation.PositionExtrapolation;
import combatant.client.util.raycast.RaycastUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lightweight aim-point tracker for KillAura.
 * <p>
 * Adapted from LiquidBounce (https://github.com/CCBlueX/LiquidBounce).
 * Original copyright (c) CCBlueX.
 */
public final class PointTracker {

    private int trackedEntityId = -1;
    private PointInsideBox delayedPoint;
    private int currentDelay = randomInt(2, 4);
    private PointInsideBox lazyPoint;
    private double currentLazyThreshold;
    private Vec3 currentOffset = Vec3.ZERO;
    private Vec3 targetOffset = Vec3.ZERO;
    private Vec3 smoothedPoint;
    private Vec3 lastSelectedPoint;
    private Vec3 lastEyes;
    private Vec3 lastAimDirection;
    private Vec3 instabilityOffset = Vec3.ZERO;
    private Vec3 targetInstabilityOffset = Vec3.ZERO;
    private int instabilityRetargetTicks;

    private boolean delayEnabled = true;
    private int delayMin = 2;
    private int delayMax = 4;
    private boolean lazyEnabled = true;
    private double lazyMin = 0.10;
    private double lazyMax = 0.20;
    private boolean gaussianEnabled;
    private double gaussianYawFactor;
    private double gaussianPitchFactor;
    private int gaussianChance = 100;
    private double gaussianSpeed = 0.15;

    private static float scoreCandidate(Vec3 eyes,
                                        Rotation initialRotation,
                                        Rotation candidateRotation,
                                        Vec3 point,
                                        Vec3 center,
                                        AABB box,
                                        Vec3 aimDir,
                                        Vec3 movement,
                                        double movementStrength) {
        float rotationScore = initialRotation.angleTo(candidateRotation) * 1.15f;
        double halfX = Math.max(1.0E-4, box.getXsize() * 0.5);
        double halfY = Math.max(1.0E-4, box.getYsize() * 0.5);
        double halfZ = Math.max(1.0E-4, box.getZsize() * 0.5);

        double nx = Math.abs(point.x - center.x) / halfX;
        double ny = Math.abs(point.y - center.y) / halfY;
        double nz = Math.abs(point.z - center.z) / halfZ;
        double edgeFactor = Math.max(Math.max(nx, nz), ny * 0.65);
        double centerPenalty = (1.0 - Mth.clamp(edgeFactor, 0.0, 1.0))
                * (movementStrength > 0.035 ? 6.0 : 4.0);

        double rayDistance = distanceToRay(eyes, aimDir, point);
        double rayScale = Math.max(0.08, Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())) * 0.5);
        double rayScore = (rayDistance / rayScale) * (movementStrength > 0.035 ? 5.5 : 4.0);

        double movementBenefit = movingPointBenefit(point, center, box, movement, movementStrength);
        return rotationScore + (float) rayScore + (float) centerPenalty - (float) movementBenefit;
    }

    private static List<Candidate> filterDiverse(List<Candidate> candidates, Vec3 reference, AABB box, boolean movingTarget) {
        List<Candidate> out = new ArrayList<>();
        if (reference == null) {
            return out;
        }

        double minAxis = Math.min(box.getXsize(), Math.min(box.getYsize(), box.getZsize()));
        double minDistance = Math.max(movingTarget ? 0.085 : 0.055, minAxis * (movingTarget ? 0.26 : 0.18));
        double minDistanceSq = minDistance * minDistance;
        for (Candidate candidate : candidates) {
            if (candidate.point().distanceToSqr(reference) >= minDistanceSq) {
                out.add(candidate);
            }
        }
        return out;
    }

    private static Candidate selectWeighted(List<Candidate> candidates, float bestScore) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        double total = 0.0;
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            double delta = Math.max(0.0, candidates.get(i).score() - bestScore);
            double weight = 1.0 / (1.0 + delta * delta * 0.08);
            weights[i] = weight;
            total += weight;
        }

        double pick = ThreadLocalRandom.current().nextDouble(total);
        for (int i = 0; i < candidates.size(); i++) {
            pick -= weights[i];
            if (pick <= 0.0) {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private static double movingPointBenefit(Vec3 point, Vec3 center, AABB box, Vec3 movement, double movementStrength) {
        if (movementStrength <= 0.035) {
            return 0.0;
        }

        double halfX = Math.max(1.0E-4, box.getXsize() * 0.5);
        double halfZ = Math.max(1.0E-4, box.getZsize() * 0.5);
        double dirX = movement.x / movementStrength;
        double dirZ = movement.z / movementStrength;
        double offX = Mth.clamp((point.x - center.x) / halfX, -1.0, 1.0);
        double offZ = Mth.clamp((point.z - center.z) / halfZ, -1.0, 1.0);
        double lead = Mth.clamp(offX * dirX + offZ * dirZ, -1.0, 1.0);
        double sideStrength = Math.min(1.0, Math.hypot(offX, offZ));

        return Math.max(0.0, lead) * 4.0 + sideStrength * 1.4;
    }

    private static double distanceToRay(Vec3 origin, Vec3 direction, Vec3 point) {
        Vec3 diff = point.subtract(origin);
        double t = Math.max(0.0, diff.dot(direction));
        Vec3 closest = origin.add(direction.scale(t));
        return point.distanceTo(closest);
    }

    private static Vec3 findRayHitPoint(Vec3 eyes, Vec3 aimDir, AABB box, double maxDistance) {
        double range = maxDistance > 0.0 && maxDistance < Double.MAX_VALUE ? maxDistance : 6.0;
        return box.clip(eyes, eyes.add(aimDir.scale(range))).orElse(null);
    }

    private static double horizontalLength(Vec3 vec) {
        if (vec == null) {
            return 0.0;
        }
        return Math.hypot(vec.x, vec.z);
    }

    private static boolean isValidPoint(Vec3 startPoint,
                                        Vec3 endPoint,
                                        double maxDistance,
                                        boolean ignoreWalls) {
        if (startPoint == null || endPoint == null) {
            return false;
        }

        if (maxDistance > 0.0 && startPoint.distanceToSqr(endPoint) > maxDistance * maxDistance) {
            return false;
        }

        return ignoreWalls || RaycastUtil.hasLineOfSightPoint(startPoint, endPoint);
    }

    private static Vec3 randomInstabilityOffset(AABB box, Vec3 entityVelocity, Vec3 aimDirection, double intensity) {
        double halfX = Math.max(0.001, box.getXsize() * 0.5);
        double halfY = Math.max(0.001, box.getYsize() * 0.5);
        double halfZ = Math.max(0.001, box.getZsize() * 0.5);

        double speed = horizontalLength(entityVelocity);
        double sideX;
        double sideZ;
        if (speed > 0.035) {
            sideX = entityVelocity.x / speed;
            sideZ = entityVelocity.z / speed;
        } else {
            Vec3 horizontalAim = new Vec3(aimDirection.x, 0.0, aimDirection.z);
            double aimLen = Math.max(1.0E-4, horizontalLength(horizontalAim));
            sideX = -horizontalAim.z / aimLen;
            sideZ = horizontalAim.x / aimLen;
        }

        double lateral = randomDouble(-1.0, 1.0) * (0.14 + 0.22 * intensity);
        double lead = speed > 0.035 ? randomDouble(0.05, 0.24) * intensity : 0.0;
        double vertical = randomDouble(-0.16, 0.14) * intensity;

        return new Vec3(
                sideX * halfX * lateral + sideX * halfX * lead,
                halfY * vertical,
                sideZ * halfZ * lateral + sideZ * halfZ * lead
        );
    }

    private static Vec3 clampToBox(Vec3 point, AABB box) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ)
        );
    }

    private static Vec3 getNearestPoint(AABB box, Vec3 eyes) {
        return new Vec3(
                Mth.clamp(eyes.x, box.minX, box.maxX),
                Mth.clamp(eyes.y, box.minY, box.maxY),
                Mth.clamp(eyes.z, box.minZ, box.maxZ)
        );
    }

    private static List<Vec3> projectPointsOnBox(Vec3 eyes, AABB box) {
        List<Vec3> points = new ArrayList<>();
        double[] steps = proportions();

        if (!box.contains(eyes)) {
            for (double y : steps) {
                for (double z : steps) {
                    points.add(new Vec3(box.minX, box.minY + box.getYsize() * y, box.minZ + box.getZsize() * z));
                    points.add(new Vec3(box.maxX, box.minY + box.getYsize() * y, box.minZ + box.getZsize() * z));
                }
            }
            for (double x : steps) {
                for (double z : steps) {
                    points.add(new Vec3(box.minX + box.getXsize() * x, box.minY, box.minZ + box.getZsize() * z));
                    points.add(new Vec3(box.minX + box.getXsize() * x, box.maxY, box.minZ + box.getZsize() * z));
                }
            }
            for (double x : steps) {
                for (double y : steps) {
                    points.add(new Vec3(box.minX + box.getXsize() * x, box.minY + box.getYsize() * y, box.minZ));
                    points.add(new Vec3(box.minX + box.getXsize() * x, box.minY + box.getYsize() * y, box.maxZ));
                }
            }
            return points;
        }

        for (double x : steps) {
            for (double y : steps) {
                for (double z : steps) {
                    points.add(new Vec3(
                            box.minX + box.getXsize() * x,
                            box.minY + box.getYsize() * y,
                            box.minZ + box.getZsize() * z
                    ));
                }
            }
        }
        return points;
    }

    private static int randomInt(int min, int max) {
        if (max <= min) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static double randomDouble(double min, double max) {
        if (max <= min) return min;
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private static double[] proportions() {
        double[] steps = new double[13];
        for (int i = 0; i < steps.length; i++) {
            steps[i] = (i + 0.5) / steps.length;
        }
        return steps;
    }

    public void setOptions(boolean delayEnabled,
                           int delayMin,
                           int delayMax,
                           boolean lazyEnabled,
                           double lazyMin,
                           double lazyMax,
                           boolean gaussianEnabled,
                           double gaussianYawFactor,
                           double gaussianPitchFactor,
                           int gaussianChance,
                           double gaussianSpeed) {
        this.delayEnabled = delayEnabled;
        this.delayMin = Math.max(0, Math.min(delayMin, delayMax));
        this.delayMax = Math.max(this.delayMin, delayMax);
        this.lazyEnabled = lazyEnabled;
        this.lazyMin = Math.max(0.0, Math.min(lazyMin, lazyMax));
        this.lazyMax = Math.max(this.lazyMin, lazyMax);
        this.gaussianEnabled = gaussianEnabled;
        this.gaussianYawFactor = Math.max(0.0, gaussianYawFactor);
        this.gaussianPitchFactor = Math.max(0.0, gaussianPitchFactor);
        this.gaussianChance = Math.max(0, Math.min(100, gaussianChance));
        this.gaussianSpeed = Math.max(0.01, Math.min(1.0, gaussianSpeed));
    }

    public PointInsideBox findPoint(Vec3 eyes,
                                    Entity entity,
                                    int ticks,
                                    Rotation initialRotation,
                                    double maxDistance,
                                    boolean ignoreWalls) {
        AABB box = PositionExtrapolation.getBestForEntity(entity).getBoxInTicks(ticks);
        List<Vec3> points = projectPointsOnBox(eyes, box);

        if (trackedEntityId != entity.getId()) {
            resetProcessors();
            trackedEntityId = entity.getId();
        }

        Vec3 bestPoint = selectMultiPoint(eyes, entity, box, initialRotation, maxDistance, ignoreWalls);
        if (bestPoint == null) {
            Vec3 anchor = smoothedPoint != null ? clampToBox(smoothedPoint, box) : eyes;
            bestPoint = points.isEmpty() ? getNearestPoint(box, anchor) : points.get(0);
            for (Vec3 point : points) {
                if (point.distanceToSqr(anchor) < bestPoint.distanceToSqr(anchor)) {
                    bestPoint = point;
                }
            }
        }

        PointInsideBox point = new PointInsideBox(clampToBox(bestPoint, box), box);
        point = applyDelay(point);
        point = applyLazy(point);
        point = applyGaussian(point);
        point = applyMotionInstability(point, eyes, entity, initialRotation);
        point = applyContinuity(point);
        smoothedPoint = point.pos();
        return new PointInsideBox(clampToBox(point.pos(), box), box);
    }

    public PointInsideBox findPoint(Vec3 eyes, Entity entity, int ticks, Rotation initialRotation) {
        return findPoint(eyes, entity, ticks, initialRotation, Double.MAX_VALUE, true);
    }

    private Vec3 selectMultiPoint(Vec3 eyes,
                                  Entity entity,
                                  AABB box,
                                  Rotation initialRotation,
                                  double maxDistance,
                                  boolean ignoreWalls) {
        if (eyes == null || box == null || initialRotation == null) {
            return null;
        }

        List<Candidate> candidates = new ArrayList<>();
        float bestScore = Float.MAX_VALUE;
        Vec3 center = box.getCenter();
        Vec3 aimDir = initialRotation.directionVector().normalize();
        Vec3 movement = entity != null ? entity.getDeltaMovement() : Vec3.ZERO;
        double movementStrength = horizontalLength(movement);

        Vec3 rayHit = findRayHitPoint(eyes, aimDir, box, maxDistance);
        if (rayHit != null && isValidPoint(eyes, rayHit, maxDistance, ignoreWalls)) {
            Rotation rayRotation = Rotation.lookingAt(rayHit, eyes).normalize();
            float score = scoreCandidate(eyes, initialRotation, rayRotation, rayHit, center, box,
                    aimDir, movement, movementStrength) - 2.0f;
            candidates.add(new Candidate(rayHit, score));
            bestScore = score;
        }

        for (Vec3 point : projectPointsOnBox(eyes, box)) {
            if (!isValidPoint(eyes, point, maxDistance, ignoreWalls)) {
                continue;
            }
            Rotation candidateRotation = Rotation.lookingAt(point, eyes).normalize();
            float score = scoreCandidate(eyes, initialRotation, candidateRotation, point, center, box,
                    aimDir, movement, movementStrength);
            candidates.add(new Candidate(point, score));
            if (score < bestScore) {
                bestScore = score;
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        boolean movingTarget = movementStrength > 0.035 || Math.abs(movement.y) > 0.03;
        float threshold = movingTarget
                ? Math.max(7.0f, Math.min(28.0f, bestScore * 0.55f + 6.0f))
                : Math.max(4.0f, Math.min(18.0f, bestScore * 0.35f + 3.0f));
        List<Candidate> pool = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.score() <= bestScore + threshold) {
                pool.add(candidate);
            }
        }
        if (pool.isEmpty()) {
            pool = candidates;
        }

        Vec3 reference = lastSelectedPoint != null ? lastSelectedPoint : smoothedPoint;
        List<Candidate> diversePool = filterDiverse(pool, reference, box, movingTarget);
        if (!diversePool.isEmpty()) {
            pool = diversePool;
        }

        Candidate selected = selectWeighted(pool, bestScore);
        lastSelectedPoint = selected.point();
        return selected.point();
    }

    public void reset() {
        trackedEntityId = -1;
        resetProcessors();
    }

    private void resetProcessors() {
        delayedPoint = null;
        currentDelay = randomInt(delayMin, delayMax);
        lazyPoint = null;
        currentLazyThreshold = 0.0;
        currentOffset = Vec3.ZERO;
        targetOffset = Vec3.ZERO;
        smoothedPoint = null;
        lastSelectedPoint = null;
        lastEyes = null;
        lastAimDirection = null;
        instabilityOffset = Vec3.ZERO;
        targetInstabilityOffset = Vec3.ZERO;
        instabilityRetargetTicks = 0;
    }

    private PointInsideBox applyDelay(PointInsideBox point) {
        if (!delayEnabled) {
            delayedPoint = point;
            currentDelay = 0;
            return point;
        }

        if (delayedPoint != null) {
            delayedPoint = new PointInsideBox(clampToBox(delayedPoint.pos(), point.box()), point.box());
        }

        if (point.equals(delayedPoint)) {
            return point;
        }

        if (delayedPoint == null) {
            delayedPoint = point;
            currentDelay = randomInt(delayMin, delayMax);
            return point;
        }

        PointInsideBox currentPoint = delayedPoint;
        currentDelay--;
        if (currentDelay > 0) {
            return currentPoint;
        }

        delayedPoint = point;
        currentDelay = randomInt(delayMin, delayMax);
        return currentPoint;
    }

    private PointInsideBox applyLazy(PointInsideBox point) {
        if (!lazyEnabled) {
            lazyPoint = point;
            currentLazyThreshold = 0.0;
            return point;
        }

        if (lazyPoint != null) {
            lazyPoint = new PointInsideBox(clampToBox(lazyPoint.pos(), point.box()), point.box());
        }

        if (lazyPoint == null) {
            lazyPoint = point;
            currentLazyThreshold = randomDouble(lazyMin, lazyMax);
            return point;
        }

        PointInsideBox currentPoint = lazyPoint;
        double thresholdSq = currentLazyThreshold * currentLazyThreshold;
        if (point.pos().distanceToSqr(currentPoint.pos()) < thresholdSq) {
            return currentPoint;
        }

        lazyPoint = point;
        currentLazyThreshold = randomDouble(lazyMin, lazyMax);
        return currentPoint;
    }

    private PointInsideBox applyGaussian(PointInsideBox point) {
        if (!gaussianEnabled || (gaussianYawFactor <= 0.0 && gaussianPitchFactor <= 0.0)) {
            currentOffset = Vec3.ZERO;
            targetOffset = Vec3.ZERO;
            return point;
        }

        if (hasReachedOffsetTarget()) {
            if (ThreadLocalRandom.current().nextInt(100) < gaussianChance) {
                targetOffset = new Vec3(
                        ThreadLocalRandom.current().nextGaussian() * gaussianYawFactor,
                        ThreadLocalRandom.current().nextGaussian() * gaussianPitchFactor,
                        ThreadLocalRandom.current().nextGaussian() * gaussianYawFactor
                );
            } else {
                targetOffset = Vec3.ZERO;
            }
        } else {
            currentOffset = new Vec3(
                    Mth.lerp(gaussianSpeed, currentOffset.x, targetOffset.x),
                    Mth.lerp(gaussianSpeed, currentOffset.y, targetOffset.y),
                    Mth.lerp(gaussianSpeed, currentOffset.z, targetOffset.z)
            );
        }

        return new PointInsideBox(point.pos().add(currentOffset), point.box());
    }

    private PointInsideBox applyMotionInstability(PointInsideBox point,
                                                  Vec3 eyes,
                                                  Entity entity,
                                                  Rotation initialRotation) {
        Vec3 aimDirection = initialRotation.directionVector().normalize();
        double angleChange = lastAimDirection != null
                ? Math.toDegrees(Math.acos(Mth.clamp(lastAimDirection.dot(aimDirection), -1.0, 1.0)))
                : 0.0;
        double playerMove = lastEyes != null ? eyes.distanceTo(lastEyes) : 0.0;
        Vec3 entityVelocity = entity != null ? entity.getDeltaMovement() : Vec3.ZERO;
        double entityMove = horizontalLength(entityVelocity) + Math.abs(entityVelocity.y) * 0.45;

        lastEyes = eyes;
        lastAimDirection = aimDirection;

        double intensity = Mth.clamp(angleChange / 14.0 + playerMove / 0.14 + entityMove / 0.11, 0.0, 1.0);
        if (intensity < 0.12) {
            instabilityOffset = instabilityOffset.scale(0.72);
            targetInstabilityOffset = targetInstabilityOffset.scale(0.72);
            return new PointInsideBox(clampToBox(point.pos().add(instabilityOffset), point.box()), point.box());
        }

        instabilityRetargetTicks--;
        if (instabilityRetargetTicks <= 0 || instabilityOffset.distanceToSqr(targetInstabilityOffset) < 1.0E-4) {
            targetInstabilityOffset = randomInstabilityOffset(point.box(), entityVelocity, aimDirection, intensity);
            instabilityRetargetTicks = randomInt(2, intensity > 0.65 ? 4 : 6);
        }

        double blend = 0.14 + 0.18 * intensity;
        instabilityOffset = new Vec3(
                Mth.lerp(blend, instabilityOffset.x, targetInstabilityOffset.x),
                Mth.lerp(blend, instabilityOffset.y, targetInstabilityOffset.y),
                Mth.lerp(blend, instabilityOffset.z, targetInstabilityOffset.z)
        );

        return new PointInsideBox(clampToBox(point.pos().add(instabilityOffset), point.box()), point.box());
    }

    private PointInsideBox applyContinuity(PointInsideBox point) {
        if (smoothedPoint == null) {
            return point;
        }

        Vec3 current = clampToBox(smoothedPoint, point.box());
        Vec3 target = clampToBox(point.pos(), point.box());
        double distance = current.distanceTo(target);
        if (distance <= 1.0E-4) {
            return new PointInsideBox(current, point.box());
        }

        double blend = distance > 0.35 ? 0.42 : distance > 0.15 ? 0.28 : 0.18;
        Vec3 blended = new Vec3(
                Mth.lerp(blend, current.x, target.x),
                Mth.lerp(blend, current.y, target.y),
                Mth.lerp(blend, current.z, target.z)
        );
        return new PointInsideBox(blended, point.box());
    }

    private boolean hasReachedOffsetTarget() {
        return Math.abs(currentOffset.x - targetOffset.x) < 0.01
                && Math.abs(currentOffset.y - targetOffset.y) < 0.01
                && Math.abs(currentOffset.z - targetOffset.z) < 0.01;
    }

    private record Candidate(Vec3 point, float score) {
    }
}
