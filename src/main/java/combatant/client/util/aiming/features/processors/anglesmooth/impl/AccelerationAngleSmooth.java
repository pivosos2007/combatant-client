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

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.RotationUtil;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.data.RotationDelta;
import combatant.client.util.aiming.features.processors.anglesmooth.AngleSmooth;
import combatant.client.util.raycast.RaycastUtil;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Acceleration angle smooth.
 * <p>
 * Ported from LiquidBounce (CCBlueX).
 */
public final class AccelerationAngleSmooth extends AngleSmooth {

    // Dynamic acceleration
    public final BooleanValue dynamicAccelEnabled;
    public final NumberValue<Float> coefDistance;
    // Acceleration error
    public final BooleanValue accelErrorEnabled;
    // Constant error
    public final BooleanValue constantErrorEnabled;
    // Sigmoid deceleration
    public final BooleanValue sigmoidDecelEnabled;
    private final NumberValue<Float> yawAccelerationMin;
    private final NumberValue<Float> yawAccelerationMax;
    private final NumberValue<Float> pitchAccelerationMin;
    private final NumberValue<Float> pitchAccelerationMax;
    private final NumberValue<Float> yawCrosshairMin;
    private final NumberValue<Float> yawCrosshairMax;
    private final NumberValue<Float> pitchCrosshairMin;
    private final NumberValue<Float> pitchCrosshairMax;
    private final NumberValue<Float> yawAccelError;
    private final NumberValue<Float> pitchAccelError;
    private final NumberValue<Float> yawConstantError;
    private final NumberValue<Float> pitchConstantError;
    private final NumberValue<Float> sigmoidSteepness;
    private final NumberValue<Float> sigmoidMidpoint;

    public AccelerationAngleSmooth(
            NumberValue<Float> yawAccelerationMin,
            NumberValue<Float> yawAccelerationMax,
            NumberValue<Float> pitchAccelerationMin,
            NumberValue<Float> pitchAccelerationMax,
            BooleanValue dynamicAccelEnabled,
            NumberValue<Float> coefDistance,
            NumberValue<Float> yawCrosshairMin,
            NumberValue<Float> yawCrosshairMax,
            NumberValue<Float> pitchCrosshairMin,
            NumberValue<Float> pitchCrosshairMax,
            BooleanValue accelErrorEnabled,
            NumberValue<Float> yawAccelError,
            NumberValue<Float> pitchAccelError,
            BooleanValue constantErrorEnabled,
            NumberValue<Float> yawConstantError,
            NumberValue<Float> pitchConstantError,
            BooleanValue sigmoidDecelEnabled,
            NumberValue<Float> sigmoidSteepness,
            NumberValue<Float> sigmoidMidpoint
    ) {
        super("Acceleration");
        this.yawAccelerationMin = yawAccelerationMin;
        this.yawAccelerationMax = yawAccelerationMax;
        this.pitchAccelerationMin = pitchAccelerationMin;
        this.pitchAccelerationMax = pitchAccelerationMax;
        this.dynamicAccelEnabled = dynamicAccelEnabled;
        this.coefDistance = coefDistance;
        this.yawCrosshairMin = yawCrosshairMin;
        this.yawCrosshairMax = yawCrosshairMax;
        this.pitchCrosshairMin = pitchCrosshairMin;
        this.pitchCrosshairMax = pitchCrosshairMax;
        this.accelErrorEnabled = accelErrorEnabled;
        this.yawAccelError = yawAccelError;
        this.pitchAccelError = pitchAccelError;
        this.constantErrorEnabled = constantErrorEnabled;
        this.yawConstantError = yawConstantError;
        this.pitchConstantError = pitchConstantError;
        this.sigmoidDecelEnabled = sigmoidDecelEnabled;
        this.sigmoidSteepness = sigmoidSteepness;
        this.sigmoidMidpoint = sigmoidMidpoint;
    }

    private static float calculateAcceleration(float diff, float prevDiff,
                                               float low, float high,
                                               float decel) {
        float delta = RotationUtil.angleDifference(diff, prevDiff);
        float clamped = clamp(delta, low, high);
        return clamped * decel;
    }

    private static float clamp(float v, float low, float high) {
        if (v < low) return low;
        if (v > high) return high;
        return v;
    }

    private static float randomRange(float a, float b) {
        float min = Math.min(a, b);
        float max = Math.max(a, b);
        if (max <= min) return min;
        return (float) ThreadLocalRandom.current().nextDouble(min, max);
    }

    @Override
    public Rotation process(RotationTarget rotationTarget, Rotation currentRotation, Rotation targetRotation) {
        Rotation prevRotation = RotationManager.INSTANCE.getPreviousRotation();
        if (prevRotation == null) {
            prevRotation = RotationManager.getPlayerLastRotation();
        }

        RotationDelta prevDiff = prevRotation.rotationDeltaTo(currentRotation);
        RotationDelta diff = currentRotation.rotationDeltaTo(targetRotation);

        double distance = 0.0;
        boolean crosshair = false;
        if (rotationTarget != null && rotationTarget.entity != null) {
            distance = RotationManager.boxedDistanceToPlayer(rotationTarget.entity);
            crosshair = RaycastUtil.isLookingAtEntity(
                    RotationManager.player(),
                    rotationTarget.entity,
                    currentRotation.yaw(),
                    currentRotation.pitch(),
                    Math.max(3.0, distance),
                    0.0
            ) != null;
        }

        float[] newDiff = computeTurnSpeed(prevDiff, diff, crosshair, distance);
        return new Rotation(currentRotation.yaw() + newDiff[0], currentRotation.pitch() + newDiff[1]);
    }

    @Override
    public int calculateTicks(Rotation currentRotation, Rotation targetRotation) {
        Rotation prevRotation = RotationManager.INSTANCE.getPreviousRotation();
        if (prevRotation == null) {
            prevRotation = RotationManager.getPlayerLastRotation();
        }

        RotationDelta prevDiff = prevRotation.rotationDeltaTo(currentRotation);
        RotationDelta diff = currentRotation.rotationDeltaTo(targetRotation);

        if (diff.deltaYaw() == 0f && diff.deltaPitch() == 0f) {
            return 0;
        }

        float[] newDiff = computeTurnSpeed(prevDiff, diff, false, 0.0);

        if ((newDiff[0] == 0f && newDiff[1] == 0f) ||
                (Math.abs(diff.deltaYaw()) < Math.abs(newDiff[0]) &&
                        Math.abs(diff.deltaPitch()) < Math.abs(newDiff[1]))) {
            return 0;
        }

        double ticksH = Math.floor(Math.abs(diff.deltaYaw()) / Math.abs(newDiff[0]));
        double ticksV = Math.floor(Math.abs(diff.deltaPitch()) / Math.abs(newDiff[1]));
        if (Double.isNaN(ticksH) || Double.isNaN(ticksV)) return 0;
        return (int) Math.max(ticksH, ticksV);
    }

    private float[] computeTurnSpeed(RotationDelta prevDiff,
                                     RotationDelta diff,
                                     boolean crosshair,
                                     double distance) {
        float decelerationFactor = 1.0f;
        if (sigmoidDecelEnabled.get()) {
            decelerationFactor = computeDecelerationFactor(diff.length());
        }

        boolean crosshairCheck = dynamicAccelEnabled.get() && crosshair;
        float distanceFactor = (coefDistance.get() * (float) distance);

        float yawAccel = randomRange(yawAccelerationMin.get(), yawAccelerationMax.get());
        float pitchAccel = randomRange(pitchAccelerationMin.get(), pitchAccelerationMax.get());

        if (crosshairCheck) {
            yawAccel = randomRange(yawCrosshairMin.get(), yawCrosshairMax.get());
            pitchAccel = randomRange(pitchCrosshairMin.get(), pitchCrosshairMax.get());
        }

        float yawLow = -yawAccel + distanceFactor;
        float yawHigh = yawAccel + distanceFactor;
        float pitchLow = -pitchAccel + distanceFactor;
        float pitchHigh = pitchAccel + distanceFactor;

        float yawA = calculateAcceleration(diff.deltaYaw(), prevDiff.deltaYaw(),
                yawLow, yawHigh, decelerationFactor);
        float pitchA = calculateAcceleration(diff.deltaPitch(), prevDiff.deltaPitch(),
                pitchLow, pitchHigh, decelerationFactor);

        float[] errors = getErrors(yawA, pitchA);
        return new float[]{
                prevDiff.deltaYaw() + yawA + errors[0],
                prevDiff.deltaPitch() + pitchA + errors[1]
        };
    }

    private float computeDecelerationFactor(float rotationDifference) {
        float scaled = rotationDifference / 120f;
        double sigmoid = 1.0 / (1.0 + Math.exp(-(sigmoidSteepness.get() * (scaled - sigmoidMidpoint.get()))));
        float out = (float) sigmoid;
        if (out < 0f) out = 0f;
        if (out > 180f) out = 180f;
        return out;
    }

    private float[] getErrors(float yawAccel, float pitchAccel) {
        float yawErr = 0f;
        float pitchErr = 0f;

        if (accelErrorEnabled.get()) {
            float yawAcc = yawAccelError.get();
            float pitchAcc = pitchAccelError.get();
            yawErr += yawAccel * randomRange(-yawAcc, yawAcc);
            pitchErr += pitchAccel * randomRange(-pitchAcc, pitchAcc);
        }

        if (constantErrorEnabled.get()) {
            float yawConst = yawConstantError.get();
            float pitchConst = pitchConstantError.get();
            yawErr += randomRange(-yawConst, yawConst);
            pitchErr += randomRange(-pitchConst, pitchConst);
        }

        return new float[]{yawErr, pitchErr};
    }
}
