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

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Small rotation helpers (yaw/pitch).
 */
public enum RotationUtil {
    ;


    public static double gcd() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return 0.0;
        double sens = mc.options.sensitivity().get();
        double f = sens * 0.6 + 0.2;
        return f * f * f * 8.0 * 0.15;
    }

    public static float[] getRotations(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, distXZ)));
        return new float[]{wrapDegrees(yaw), Mth.clamp(wrapDegrees(pitch), -90.0f, 90.0f)};
    }

    public static float[] getRotationsToEntity(Entity from, Entity target) {
        Vec3 src = from.getEyePosition();
        Vec3 dst = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        if (target instanceof LivingEntity living) {
            dst = living.getEyePosition();
        }
        return getRotations(src, dst);
    }

    public static float wrapDegrees(float degrees) {
        return Mth.wrapDegrees(degrees);
    }

    public static float angleDifference(float a, float b) {
        return wrapDegrees(a - b);
    }

    public static float directionAngleTo(Entity from, Entity target) {
        float[] rot = getRotationsToEntity(from, target);
        return Math.abs(angleDifference(from.getYRot(), rot[0]));
    }

    public static int estimateTicksToReach(float currentYaw, float currentPitch,
                                           float targetYaw, float targetPitch,
                                           float maxTurnPerTick) {
        if (maxTurnPerTick <= 0.0f) return 1;
        float yawDiff = Math.abs(angleDifference(currentYaw, targetYaw));
        float pitchDiff = Math.abs(angleDifference(currentPitch, targetPitch));
        float maxDiff = Math.max(yawDiff, pitchDiff);
        return Math.max(1, (int) Math.ceil(maxDiff / maxTurnPerTick));
    }

    public static Vec3 getRotationVector(float pitch, float yaw) {
        return Vec3.directionFromRotation(pitch, yaw);
    }

    public static float withFixedYaw(float currentYaw, float targetYaw) {
        return targetYaw + angleDifference(currentYaw, targetYaw);
    }

    /**
     * Rotate movement input from one yaw-space to another (used for silent rotations).
     */
    public static Vec2 rotateMovementInput(Vec2 input, float fromYaw, float toYaw) {
        Vec2 world = inputToWorld(input, fromYaw);
        return worldToInput(world, toYaw);
    }

    public static Input correctMovementInput(Input input, float fromYaw, float toYaw) {
        if (input == null) return null;

        float forward = impulse(input.forward(), input.backward());
        float sideways = impulse(input.left(), input.right());
        if (forward == 0.0f && sideways == 0.0f) {
            return input;
        }

        float deltaYaw = fromYaw - toYaw;
        float radians = deltaYaw * Mth.DEG_TO_RAD;

        float newX = sideways * Mth.cos(radians) - forward * Mth.sin(radians);
        float newZ = forward * Mth.cos(radians) + sideways * Mth.sin(radians);

        int movementSideways = Math.round(newX);
        int movementForward = Math.round(newZ);

        return new Input(
                movementForward > 0,
                movementForward < 0,
                movementSideways > 0,
                movementSideways < 0,
                input.jump(),
                input.shift(),
                input.sprint()
        );
    }

    private static float impulse(boolean positive, boolean negative) {
        if (positive == negative) return 0.0f;
        return positive ? 1.0f : -1.0f;
    }

    private static Vec2 inputToWorld(Vec2 input, float yaw) {
        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        float worldX = (float) (input.x * cos - input.y * sin);
        float worldZ = (float) (input.y * cos + input.x * sin);
        return new Vec2(worldX, worldZ);
    }

    private static Vec2 worldToInput(Vec2 world, float yaw) {
        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        float inputX = (float) (world.x * cos + world.y * sin);
        float inputY = (float) (world.y * cos - world.x * sin);
        return new Vec2(inputX, inputY);
    }
}
