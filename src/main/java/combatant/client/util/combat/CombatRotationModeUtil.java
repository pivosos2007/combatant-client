/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.ModeValue;
import combatant.client.util.raycast.RaycastUtil;
import combatant.client.util.target.TargetingUtil;

/**
 * Shared combat rotation-mode helpers for modules that can operate with or without
 * synthetic rotation control.
 */
public enum CombatRotationModeUtil {
    ;

    public static final String MODE_ROTATIONS = "ROTATIONS";
    public static final String MODE_NO_ROTATIONS = "NO_ROTATIONS";

    public static boolean usesRotations(ModeValue modeValue) {
        return modeValue != null && MODE_ROTATIONS.equalsIgnoreCase(modeValue.get());
    }

    public static boolean isNoRotations(ModeValue modeValue) {
        return modeValue != null && MODE_NO_ROTATIONS.equalsIgnoreCase(modeValue.get());
    }

    public static boolean canAttackWithoutRotations(Minecraft mc, LivingEntity target, double range) {
        return canAttackWithoutRotations(mc, target, range, false);
    }

    public static boolean canAttackWithoutRotations(Minecraft mc,
                                                    LivingEntity target,
                                                    double range,
                                                    boolean requireLineOfSight) {
        if (mc == null || mc.player == null || target == null || !target.isAlive()) {
            return false;
        }

        double safeRange = Math.max(0.0, range);
        Vec3 eyes = mc.player.getEyePosition();
        double distSq = TargetingUtil.distanceToEntityBoxSq(eyes, target);
        if (distSq > safeRange * safeRange) {
            return false;
        }

        return !requireLineOfSight || hasLineOfSightToEntityBox(eyes, target.getBoundingBox(), safeRange);
    }

    private static boolean hasLineOfSightToEntityBox(Vec3 eyes, AABB box, double range) {
        if (eyes == null || box == null) {
            return false;
        }

        if (box.contains(eyes)) {
            return true;
        }

        double maxDistanceSq = range * range + 1.0E-6;
        double centerX = (box.minX + box.maxX) * 0.5;
        double centerY = (box.minY + box.maxY) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        double lowerY = box.minY + Math.min(0.25, Math.max(0.0, (box.maxY - box.minY) * 0.25));
        double upperY = box.maxY - Math.min(0.25, Math.max(0.0, (box.maxY - box.minY) * 0.25));

        return isVisiblePointInRange(eyes, clampToBox(eyes, box), maxDistanceSq)
                || isVisiblePointInRange(eyes, new Vec3(centerX, centerY, centerZ), maxDistanceSq)
                || isVisiblePointInRange(eyes, new Vec3(centerX, lowerY, centerZ), maxDistanceSq)
                || isVisiblePointInRange(eyes, new Vec3(centerX, upperY, centerZ), maxDistanceSq)
                || isVisiblePointInRange(eyes, new Vec3(box.minX, centerY, centerZ), maxDistanceSq)
                || isVisiblePointInRange(eyes, new Vec3(box.maxX, centerY, centerZ), maxDistanceSq)
                || isVisiblePointInRange(eyes, new Vec3(centerX, centerY, box.minZ), maxDistanceSq)
                || isVisiblePointInRange(eyes, new Vec3(centerX, centerY, box.maxZ), maxDistanceSq);
    }

    private static Vec3 clampToBox(Vec3 point, AABB box) {
        return new Vec3(
                clamp(point.x, box.minX, box.maxX),
                clamp(point.y, box.minY, box.maxY),
                clamp(point.z, box.minZ, box.maxZ)
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isVisiblePointInRange(Vec3 eyes, Vec3 point, double maxDistanceSq) {
        return point != null
                && eyes.distanceToSqr(point) <= maxDistanceSq
                && RaycastUtil.hasLineOfSightPoint(eyes, point);
    }
}
