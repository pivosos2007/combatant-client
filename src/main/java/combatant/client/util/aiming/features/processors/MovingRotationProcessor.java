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

package combatant.client.util.aiming.features.processors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.RotationUtil;
import combatant.client.util.aiming.data.Rotation;

/**
 * Adds conservative per-tick rotation limits while moving to avoid sharp
 * silent-yaw snaps that anti-cheats can flag more easily than pure input mismatch.
 */
public final class MovingRotationProcessor implements RotationProcessor {

    private static final double MIN_HORIZONTAL_SPEED = 0.045;
    private static final float MAX_MOVING_YAW_STEP = 7.5f;
    private static final float MAX_MOVING_PITCH_STEP = 10.0f;
    private static final float MAX_MOVEMENT_YAW_OFFSET = 82.0f;

    @Override
    public Rotation process(RotationTarget rotationTarget, Rotation currentRotation, Rotation targetRotation) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc == null ? null : mc.player;
        if (player == null || currentRotation == null || targetRotation == null) {
            return targetRotation;
        }

        Vec3 velocity = player.getDeltaMovement();
        double horizontalSpeedSq = velocity.x * velocity.x + velocity.z * velocity.z;
        if (horizontalSpeedSq < MIN_HORIZONTAL_SPEED * MIN_HORIZONTAL_SPEED) {
            return targetRotation;
        }

        float movementYaw = (float) Math.toDegrees(Math.atan2(velocity.z, velocity.x)) - 90.0f;
        movementYaw = Mth.wrapDegrees(movementYaw);

        float movementDelta = RotationUtil.angleDifference(targetRotation.yaw(), movementYaw);
        float clampedYaw = movementYaw + Mth.clamp(movementDelta, -MAX_MOVEMENT_YAW_OFFSET, MAX_MOVEMENT_YAW_OFFSET);

        Rotation softenedTarget = new Rotation(clampedYaw, targetRotation.pitch(), false);
        return currentRotation.towardsLinear(softenedTarget, MAX_MOVING_YAW_STEP, MAX_MOVING_PITCH_STEP);
    }
}
