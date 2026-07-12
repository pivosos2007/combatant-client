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

package combatant.client.util.aiming;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.raycast.RaycastUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds a good aim point/rotation inside a target box.
 */
public enum AimPointUtil {
    ;

    public static RotationResult findBestRotation(Vec3 eyes,
                                                  AABB box,
                                                  float baseYaw,
                                                  float basePitch,
                                                  double range,
                                                  double wallsRange,
                                                  boolean allowThroughWalls) {
        if (eyes == null || box == null) return null;

        List<Vec3> points = collectPoints(box);
        RotationResult bestVisible = null;
        RotationResult bestInvisible = null;

        double rangeSq = range * range;
        double wallsSq = wallsRange * wallsRange;

        for (Vec3 point : points) {
            double distSq = eyes.distanceToSqr(point);
            if (distSq > rangeSq && distSq > wallsSq) continue;

            boolean visible = distSq <= wallsSq || RaycastUtil.hasLineOfSightPoint(eyes, point);
            if (!visible && !allowThroughWalls) continue;

            float[] rot = RotationUtil.getRotations(eyes, point);
            float score = rotationScore(baseYaw, basePitch, rot[0], rot[1]);
            RotationResult candidate = new RotationResult(rot[0], rot[1], point, visible);

            if (visible) {
                if (bestVisible == null || score < rotationScore(baseYaw, basePitch, bestVisible.yaw, bestVisible.pitch)) {
                    bestVisible = candidate;
                }
            } else {
                if (bestInvisible == null || score < rotationScore(baseYaw, basePitch, bestInvisible.yaw, bestInvisible.pitch)) {
                    bestInvisible = candidate;
                }
            }
        }

        return bestVisible != null ? bestVisible : bestInvisible;
    }

    private static float rotationScore(float baseYaw, float basePitch, float yaw, float pitch) {
        float yawDiff = Math.abs(RotationUtil.angleDifference(baseYaw, yaw));
        float pitchDiff = Math.abs(RotationUtil.angleDifference(basePitch, pitch));
        return yawDiff + pitchDiff;
    }

    private static List<Vec3> collectPoints(AABB box) {
        List<Vec3> out = new ArrayList<>(14);
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        double midX = (minX + maxX) * 0.5;
        double midY = (minY + maxY) * 0.5;
        double midZ = (minZ + maxZ) * 0.5;

        // center + face centers
        out.add(new Vec3(midX, midY, midZ));
        out.add(new Vec3(midX, minY, midZ));
        out.add(new Vec3(midX, maxY, midZ));
        out.add(new Vec3(minX, midY, midZ));
        out.add(new Vec3(maxX, midY, midZ));
        out.add(new Vec3(midX, midY, minZ));
        out.add(new Vec3(midX, midY, maxZ));

        // corners
        out.add(new Vec3(minX, minY, minZ));
        out.add(new Vec3(minX, minY, maxZ));
        out.add(new Vec3(minX, maxY, minZ));
        out.add(new Vec3(minX, maxY, maxZ));
        out.add(new Vec3(maxX, minY, minZ));
        out.add(new Vec3(maxX, minY, maxZ));
        out.add(new Vec3(maxX, maxY, minZ));
        out.add(new Vec3(maxX, maxY, maxZ));

        return out;
    }

    public record RotationResult(float yaw, float pitch, Vec3 point, boolean visible) {
    }
}
