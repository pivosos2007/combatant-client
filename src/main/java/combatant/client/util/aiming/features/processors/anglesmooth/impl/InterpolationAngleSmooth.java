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

import combatant.client.config.values.NumberValue;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.RotationUtil;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.processors.anglesmooth.FactorAngleSmooth;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Interpolation angle smooth (Sigmoid + Bezier).
 * <p>
 * Ported from LiquidBounce (CCBlueX).
 */
public final class InterpolationAngleSmooth extends FactorAngleSmooth {

    private final NumberValue<Integer> horizontalMin;
    private final NumberValue<Integer> horizontalMax;
    private final NumberValue<Integer> verticalMin;
    private final NumberValue<Integer> verticalMax;
    private final NumberValue<Integer> directionChangeMin;
    private final NumberValue<Integer> directionChangeMax;
    private final NumberValue<Float> midpoint;

    public InterpolationAngleSmooth(NumberValue<Integer> horizontalMin,
                                    NumberValue<Integer> horizontalMax,
                                    NumberValue<Integer> verticalMin,
                                    NumberValue<Integer> verticalMax,
                                    NumberValue<Integer> directionChangeMin,
                                    NumberValue<Integer> directionChangeMax,
                                    NumberValue<Float> midpoint) {
        super("Interpolation");
        this.horizontalMin = horizontalMin;
        this.horizontalMax = horizontalMax;
        this.verticalMin = verticalMin;
        this.verticalMax = verticalMax;
        this.directionChangeMin = directionChangeMin;
        this.directionChangeMax = directionChangeMax;
        this.midpoint = midpoint;
    }

    private static float calculateFactor(float rotationDifference, float turnSpeed, float directionChange,
                                         float midpoint) {
        float t = clamp01(rotationDifference / 180f);
        float bezierSpeed = bezierTransform(0.05f, 1f, 1f - t);
        float sigmoidSpeed = sigmoidTransform(t);
        if (t > midpoint) {
            return bezierSpeed * turnSpeed;
        }
        return sigmoidSpeed * clamp01(turnSpeed + directionChange);
    }

    private static float sigmoidTransform(float t) {
        return (float) (1f / (1f + Math.exp(-0.5f * (t - 0.3f))));
    }

    private static float bezierTransform(float start, float end, float t) {
        return (1f - t) * (1f - t) * start + 2f * (1f - t) * t * 1f + t * t * end;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float random(int min, int max) {
        if (max <= min) return min;
        return (float) ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    @Override
    protected float[] calculateFactors(RotationTarget rotationTarget,
                                       Rotation currentRotation,
                                       Rotation targetRotation) {
        float yawDiff = RotationUtil.wrapDegrees(targetRotation.yaw() - currentRotation.yaw());
        float pitchDiff = RotationUtil.wrapDegrees(targetRotation.pitch() - currentRotation.pitch());

        float directionChange = 0f;
        RotationTarget prev = RotationManager.INSTANCE.getPreviousRotationTarget();
        if (rotationTarget != null && prev != null) {
            int dMin = Math.min(directionChangeMin.get(), directionChangeMax.get());
            int dMax = Math.max(directionChangeMin.get(), directionChangeMax.get());
            float factor = random(dMin, dMax) / 100.0f;
            float diff = prev.rotation.angleTo(targetRotation);
            directionChange = clamp01(diff) * factor;
        }

        int hMin = Math.min(horizontalMin.get(), horizontalMax.get());
        int hMax = Math.max(horizontalMin.get(), horizontalMax.get());
        int vMin = Math.min(verticalMin.get(), verticalMax.get());
        int vMax = Math.max(verticalMin.get(), verticalMax.get());

        float horizontalSpeed = (rotationTarget != null ? random(hMin, hMax) : hMin) / 100.0f;
        float verticalSpeed = (rotationTarget != null ? random(vMin, vMax) : vMin) / 100.0f;

        float hFactor = calculateFactor(Math.abs(yawDiff), clamp01(horizontalSpeed), directionChange, midpoint.get());
        float vFactor = calculateFactor(Math.abs(pitchDiff), clamp01(verticalSpeed), directionChange, midpoint.get());

        return new float[]{hFactor * Math.abs(yawDiff), vFactor * Math.abs(pitchDiff)};
    }
}
