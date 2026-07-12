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

import net.minecraft.util.Mth;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.processors.anglesmooth.AngleSmooth;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Practical hybrid smooth with distance-aware speed and optional micro-jitter.
 * Designed to reach target reliably while keeping motion less robotic.
 */
public final class SmartAngleSmooth extends AngleSmooth {

    private final NumberValue<Float> yawMin;
    private final NumberValue<Float> yawMax;
    private final NumberValue<Float> pitchMin;
    private final NumberValue<Float> pitchMax;
    private final NumberValue<Float> snapThreshold;
    private final NumberValue<Float> jitterYaw;
    private final NumberValue<Float> jitterPitch;
    private final BooleanValue decelerateEnabled;
    private final NumberValue<Float> decelerateAngle;
    private final NumberValue<Float> decelerateMinFactor;
    private int trackedEntityId = Integer.MIN_VALUE;
    private Rotation virtualRotation;
    private boolean wasResetting;
    private int resetTicks;

    public SmartAngleSmooth(NumberValue<Float> yawMin,
                            NumberValue<Float> yawMax,
                            NumberValue<Float> pitchMin,
                            NumberValue<Float> pitchMax,
                            NumberValue<Float> snapThreshold,
                            NumberValue<Float> jitterYaw,
                            NumberValue<Float> jitterPitch,
                            BooleanValue decelerateEnabled,
                            NumberValue<Float> decelerateAngle,
                            NumberValue<Float> decelerateMinFactor) {
        super("Smart");
        this.yawMin = yawMin;
        this.yawMax = yawMax;
        this.pitchMin = pitchMin;
        this.pitchMax = pitchMax;
        this.snapThreshold = snapThreshold;
        this.jitterYaw = jitterYaw;
        this.jitterPitch = jitterPitch;
        this.decelerateEnabled = decelerateEnabled;
        this.decelerateAngle = decelerateAngle;
        this.decelerateMinFactor = decelerateMinFactor;
    }

    private static float smoothStep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private static float randomInRange(float a, float b) {
        float min = Math.min(a, b);
        float max = Math.max(a, b);
        if (max <= min) {
            return min;
        }
        return (float) ThreadLocalRandom.current().nextDouble(min, max);
    }

    @Override
    public Rotation process(RotationTarget rotationTarget, Rotation currentRotation, Rotation targetRotation) {
        boolean resetting = rotationTarget == null || rotationTarget.entity == null;
        int entityId = rotationTarget != null && rotationTarget.entity != null
                ? rotationTarget.entity.getId()
                : Integer.MIN_VALUE;
        if (entityId != trackedEntityId || virtualRotation == null || resetting != wasResetting) {
            trackedEntityId = entityId;
            virtualRotation = currentRotation;
            resetTicks = 0;
        }
        wasResetting = resetting;

        Rotation baseRotation = virtualRotation != null ? virtualRotation : currentRotation;
        float angle = baseRotation.angleTo(targetRotation);
        float finishThreshold = resetting
                ? Mth.clamp(snapThreshold.get() * 0.28f, 0.12f, 0.45f)
                : Math.max(0.05f, snapThreshold.get());
        if (angle <= finishThreshold) {
            virtualRotation = targetRotation;
            return targetRotation;
        }

        float yawFactor = randomInRange(yawMin.get(), yawMax.get());
        float pitchFactor = randomInRange(pitchMin.get(), pitchMax.get());

        float distanceScale = 1.0f;
        if (rotationTarget != null && rotationTarget.entity != null) {
            double distance = RotationManager.boxedDistanceToPlayer(rotationTarget.entity);
            distanceScale = Mth.clamp((float) (0.80 + distance * 0.15), 0.80f, 1.55f);
        }

        float motionScale = resetting ? computeResetScale(angle) : computeDecelerateScale(angle);
        Rotation stepped = baseRotation.towardsLinear(
                targetRotation,
                yawFactor * distanceScale * motionScale,
                pitchFactor * distanceScale * motionScale
        );

        float jitterScale = Mth.clamp(angle / 30.0f, 0.0f, 1.0f);
        if (resetting) {
            jitterScale *= Mth.clamp(angle / 18.0f, 0.0f, 0.35f);
            resetTicks++;
        }
        if (jitterScale > 0.0f) {
            float phase = (float) (System.currentTimeMillis() * 0.015);
            int jitterEntityId = rotationTarget != null && rotationTarget.entity != null ? rotationTarget.entity.getId() : 0;
            float seed = jitterEntityId * 0.137f;
            float yawOffset = (float) Math.sin(phase + seed) * jitterYaw.get() * jitterScale;
            float pitchOffset = (float) Math.cos(phase * 1.11f + seed) * jitterPitch.get() * jitterScale;
            Rotation output = new Rotation(
                    stepped.yaw() + yawOffset,
                    Mth.clamp(stepped.pitch() + pitchOffset, -90.0f, 90.0f),
                    false
            );
            virtualRotation = output;
            return output;
        }

        virtualRotation = stepped;
        return stepped;
    }

    @Override
    public int calculateTicks(Rotation currentRotation, Rotation targetRotation) {
        Rotation rot = currentRotation;
        int ticks = 0;
        while (!rot.approximatelyEquals(targetRotation) && ticks < 80) {
            float yawFactor = (Math.min(yawMin.get(), yawMax.get()) + Math.max(yawMin.get(), yawMax.get())) * 0.5f;
            float pitchFactor = (Math.min(pitchMin.get(), pitchMax.get()) + Math.max(pitchMin.get(), pitchMax.get())) * 0.5f;
            float angle = rot.angleTo(targetRotation);
            float decelerateScale = computeDecelerateScale(angle);
            rot = rot.towardsLinear(targetRotation, yawFactor * decelerateScale, pitchFactor * decelerateScale);
            ticks++;
        }
        return ticks;
    }

    private float computeResetScale(float angle) {
        float ramp = Mth.clamp((resetTicks + 1.0f) / 4.0f, 0.0f, 1.0f);
        float accel = 0.38f + 0.62f * smoothStep(ramp);
        return Mth.clamp(computeDecelerateScale(angle) * accel, 0.08f, 1.0f);
    }

    private float computeDecelerateScale(float angle) {
        if (!decelerateEnabled.get()) {
            return 1.0f;
        }

        float start = Math.max(0.05f, decelerateAngle.get());
        float min = Mth.clamp(decelerateMinFactor.get(), 0.05f, 1.0f);
        float t = Mth.clamp(angle / start, 0.0f, 1.0f);
        float smooth = smoothStep(t);
        return min + (1.0f - min) * smooth;
    }
}
