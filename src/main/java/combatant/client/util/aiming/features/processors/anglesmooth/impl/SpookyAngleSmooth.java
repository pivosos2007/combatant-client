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

package combatant.client.util.aiming.features.processors.anglesmooth.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.KillAura;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.processors.anglesmooth.AngleSmooth;
import combatant.client.util.click.AttackPressing;
import combatant.client.util.raycast.RaycastUtil;

import java.util.concurrent.ThreadLocalRandom;

public final class SpookyAngleSmooth extends AngleSmooth {

    private float currentJitterYaw = 0.0f;
    private float currentJitterPitch = 0.0f;
    private float targetJitterYaw = 0.0f;
    private float targetJitterPitch = 0.0f;

    private float circlePhase = 0.0f;
    private float circleRadius = 0.0f;
    private float targetCircleRadius = 0.0f;
    private float currentSpeed = 0.0f;
    private int trackedEntityId = Integer.MIN_VALUE;
    private Rotation virtualRotation;

    public SpookyAngleSmooth() {
        super("Spooky");
    }

    private static boolean isAttackWindow() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getAttackStrengthScale(0.0f) >= 0.95f;
    }

    private static Vec3 hitbox(Entity entity, float xDiv, float yDiv, float zDiv, float widthDiv) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return entity.getBoundingBox().getCenter();
        }

        double wHalf = entity.getBbWidth() / Math.max(1.0f, widthDiv);
        double yExpand = Mth.clamp(entity.getEyeY() - entity.getY(), 0.0, entity.getBbHeight());
        double xExpand = Mth.clamp(mc.player.getX() - entity.getX(), -wHalf, wHalf);
        double zExpand = Mth.clamp(mc.player.getZ() - entity.getZ(), -wHalf, wHalf);

        return new Vec3(
                entity.getX() + xExpand / Math.max(0.001f, xDiv),
                entity.getY() + yExpand / Math.max(0.001f, yDiv),
                entity.getZ() + zExpand / Math.max(0.001f, zDiv)
        );
    }

    private static boolean isLookingAtHitbox(Entity entity, Rotation currentRotation) {
        if (entity == null) {
            return false;
        }
        return RaycastUtil.isLookingAtEntity(
                RotationManager.player(),
                entity,
                currentRotation.yaw(),
                currentRotation.pitch(),
                4.0,
                0.0
        ) != null;
    }

    private static float random(float a, float b) {
        float min = Math.min(a, b);
        float max = Math.max(a, b);
        if (max <= min) {
            return min;
        }
        return (float) ThreadLocalRandom.current().nextDouble(min, max);
    }

    @Override
    public Rotation process(RotationTarget rotationTarget, Rotation currentRotation, Rotation targetRotation) {
        Entity entity = rotationTarget != null ? rotationTarget.entity : null;
        Minecraft mc = Minecraft.getInstance();
        int entityId = entity != null ? entity.getId() : Integer.MIN_VALUE;
        if (entityId != trackedEntityId || virtualRotation == null) {
            trackedEntityId = entityId;
            virtualRotation = currentRotation;
        }

        Rotation baseRotation = virtualRotation != null ? virtualRotation : currentRotation;
        boolean canAttack = entity != null && isAttackWindow();
        boolean lookingAtHitbox = !canAttack && isLookingAtHitbox(entity, baseRotation);

        if (mc.player != null && entity != null && canAttack) {
            Vec3 aimPoint = hitbox(entity, 1.0f, entity.onGround() ? 1.0f : 1.256f, 1.0f, 2.0f);
            targetRotation = Rotation.lookingAt(aimPoint, mc.player.getEyePosition());
        }

        float yawDelta = Mth.wrapDegrees(targetRotation.yaw() - baseRotation.yaw());
        float pitchDelta = Mth.wrapDegrees(targetRotation.pitch() - baseRotation.pitch());
        float rotationDifference = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
        if (rotationDifference < 0.01f) {
            rotationDifference = 1.0f;
        }

        circlePhase += 0.75f * random(7.5f, 12.5f);
        if (circlePhase > Math.PI * 2) {
            circlePhase -= (float) (Math.PI * 2);
        }

        if (canAttack) {
            targetCircleRadius = random(0.5f, 4.5f);
        } else if (lookingAtHitbox) {
            targetCircleRadius = 12.0f;
        } else {
            targetCircleRadius = random(8.0f, 12.0f);
        }
        circleRadius += (targetCircleRadius - circleRadius) * 0.18f;

        float circleYaw = (float) (Math.cos(circlePhase) * circleRadius);
        float circlePitch = (float) (Math.sin(circlePhase * 11.3f) * circleRadius * 0.4f);

        long elapsedMs = AttackPressing.INSTANCE.lastClickPassed();
        int count = AttackPressing.INSTANCE.clickCount();
        float timeRandom = elapsedMs / 100.0f + (count % 5);
        int pattern = count % 4;

        float sinA = (float) Math.sin(timeRandom);
        float cosA = (float) Math.cos(timeRandom);
        float sinB = (float) Math.sin(timeRandom * 2.2f);
        float cosB = (float) Math.cos(timeRandom * 0.6f);
        float sinC = (float) Math.sin(timeRandom * 2.1f);
        float cosC = (float) Math.cos(timeRandom * 0.5f);

        float patternYaw = switch (pattern) {
            case 0 -> cosA;
            case 1 -> sinB;
            case 2 -> sinA;
            default -> -cosC;
        };
        float patternPitch = switch (pattern) {
            case 0 -> sinA;
            case 1 -> cosB;
            case 2 -> -cosA;
            default -> sinC;
        };

        float jitterMultiplier = canAttack ? 0.5f : (lookingAtHitbox ? 0.6f : 1.0f);
        targetJitterYaw = random(32.0f, 35.0f) * patternYaw * jitterMultiplier;
        targetJitterPitch = random(2.0f, 5.0f) * patternPitch * jitterMultiplier;

        currentJitterYaw += (targetJitterYaw - currentJitterYaw) * 0.15f;
        currentJitterPitch += (targetJitterPitch - currentJitterPitch) * 0.15f;

        float targetSpeed;
        if (canAttack) {
            targetSpeed = 1.0f;
        } else if (lookingAtHitbox) {
            targetSpeed = random(0.15f, 0.35f);
        } else if (entity != null) {
            float distanceFactor = Mth.clamp(rotationDifference / 30.0f, 0.1f, 1.0f);
            targetSpeed = random(0.25f, 0.45f) * distanceFactor;
        } else {
            targetSpeed = AttackPressing.INSTANCE.lastClickPassed() > 600L ? random(0.2f, 0.35f) : 0.53f;
        }
        currentSpeed += (targetSpeed - currentSpeed) * 0.65f;

        float lineYaw = Math.abs(yawDelta / rotationDifference) * 180.0f;
        float linePitch = Math.abs(pitchDelta / rotationDifference) * 90.0f;
        float moveYaw = Mth.clamp(yawDelta, -lineYaw, lineYaw);
        float movePitch = Mth.clamp(pitchDelta, -linePitch, linePitch);

        float totalJitterYaw = currentJitterYaw + circleYaw;
        float totalJitterPitch = currentJitterPitch + circlePitch;

        KillAura aura = Modules.get(KillAura.class);
        if ((aura == null || !aura.isEnabled() || entity == null) && AttackPressing.INSTANCE.lastClickPassed() > 800L) {
            totalJitterYaw *= 0.3f;
            totalJitterPitch *= 0.3f;
        }

        float newYaw = Mth.lerp(currentSpeed, baseRotation.yaw(), baseRotation.yaw() + moveYaw) + totalJitterYaw;
        float newPitch = Mth.lerp(currentSpeed, baseRotation.pitch(), baseRotation.pitch() + movePitch) + totalJitterPitch;
        Rotation output = new Rotation(newYaw, Mth.clamp(newPitch, -90.0f, 90.0f), false);
        virtualRotation = output;
        return output;
    }

    @Override
    public int calculateTicks(Rotation currentRotation, Rotation targetRotation) {
        Rotation rot = currentRotation;
        int ticks = 0;
        while (!rot.approximatelyEquals(targetRotation) && ticks < 80) {
            rot = rot.towardsLinear(targetRotation, 90.0f, 65.0f);
            ticks++;
        }
        return ticks;
    }
}
