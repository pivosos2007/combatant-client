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

package combatant.client.util.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.aiming.RotationManager;

public enum MovementUtil {
    ;

    public static boolean isMoving() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        return player != null
                && player.input != null
                && (player.input.getMoveVector().x != 0.0F || player.input.getMoveVector().y != 0.0F);
    }

    public static float getMovementDirectionYaw(LocalPlayer player, float fallbackYaw) {
        if (player == null || player.input == null) return fallbackYaw;

        var move = player.input.getMoveVector();
        float forward = move.y;
        float strafe = move.x;
        if (forward == 0.0f && strafe == 0.0f) return fallbackYaw;

        float yaw = resolveControlYaw(player, fallbackYaw);
        double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        double x = forward * cos + strafe * sin;
        double z = forward * sin - strafe * cos;
        if (Math.abs(x) < 1.0E-6 && Math.abs(z) < 1.0E-6) return fallbackYaw;

        return (float) Math.toDegrees(Math.atan2(z, x)) - 90.0f;
    }

    public static double[] forward(double speed) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        if (player == null || player.input == null) return new double[]{0.0, 0.0};

        var move = player.input.getMoveVector();
        float forward = move.y;
        float strafe = move.x;
        float yaw = resolveControlYaw(player, player.getYRot());

        if (forward != 0.0f) {
            if (strafe > 0.0f) {
                yaw += (forward > 0.0f ? -45f : 45f);
            } else if (strafe < 0.0f) {
                yaw += (forward > 0.0f ? 45f : -45f);
            }
            strafe = 0.0f;
            if (forward > 0.0f) forward = 1.0f;
            else if (forward < 0.0f) forward = -1.0f;
        }

        double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        double x = forward * speed * cos + strafe * speed * sin;
        double z = forward * speed * sin - strafe * speed * cos;
        return new double[]{x, z};
    }

    public static double[] forwardWithoutStrafe(double speed) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        if (player == null) return new double[]{0.0, 0.0};

        float yaw = resolveControlYaw(player, player.getYRot());
        double x = speed * Math.cos(Math.toRadians(yaw + 90.0f));
        double z = speed * Math.sin(Math.toRadians(yaw + 90.0f));
        return new double[]{x, z};
    }

    private static float resolveControlYaw(LocalPlayer player, float fallbackYaw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && player != null && player == mc.player) {
            var rotation = RotationManager.INSTANCE.getMovementRotation();
            if (rotation != null) {
                return rotation.yaw();
            }
        }
        return fallbackYaw;
    }

    /**
     * Strafe helper inspired by LiquidBounce.
     * Applies directional input to current horizontal speed with strength blend.
     */
    public static Vec3 withStrafe(Vec3 currentVelocity, Vec3 movementInput, float yaw, double speed, double strength) {
        if (currentVelocity == null) currentVelocity = Vec3.ZERO;
        if (movementInput == null || movementInput.lengthSqr() < 1.0E-7) {
            return new Vec3(0.0, currentVelocity.y, 0.0);
        }

        if (strength < 0.0) strength = 0.0;
        if (strength > 1.0) strength = 1.0;

        Vec3 input = movementInput;
        double lenSq = input.lengthSqr();
        if (lenSq > 1.0) {
            input = input.normalize();
        }

        double prevX = currentVelocity.x * (1.0 - strength);
        double prevZ = currentVelocity.z * (1.0 - strength);
        double useSpeed = speed * strength;

        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double x = (input.x * cos - input.z * sin) * useSpeed + prevX;
        double z = (input.z * cos + input.x * sin) * useSpeed + prevZ;

        return new Vec3(x, currentVelocity.y, z);
    }

    /**
     * Strafe helper that uses a world-space direction vector.
     */
    public static Vec3 withStrafeDirection(Vec3 currentVelocity, Vec3 direction, double speed, double strength) {
        if (currentVelocity == null) currentVelocity = Vec3.ZERO;
        if (direction == null) {
            return new Vec3(0.0, currentVelocity.y, 0.0);
        }
        double dirLen = Math.hypot(direction.x, direction.z);
        if (dirLen < 1.0E-7) {
            return new Vec3(0.0, currentVelocity.y, 0.0);
        }

        if (strength < 0.0) strength = 0.0;
        if (strength > 1.0) strength = 1.0;

        double dirX = direction.x / dirLen;
        double dirZ = direction.z / dirLen;

        double prevX = currentVelocity.x * (1.0 - strength);
        double prevZ = currentVelocity.z * (1.0 - strength);
        double useSpeed = speed * strength;

        double x = dirX * useSpeed + prevX;
        double z = dirZ * useSpeed + prevZ;
        return new Vec3(x, currentVelocity.y, z);
    }
}
