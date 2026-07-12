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
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.processors.anglesmooth.FactorAngleSmooth;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Linear angle smooth.
 * <p>
 * Ported from LiquidBounce (CCBlueX).
 */
public final class LinearAngleSmooth extends FactorAngleSmooth {

    private final NumberValue<Float> horizontalMin;
    private final NumberValue<Float> horizontalMax;
    private final NumberValue<Float> verticalMin;
    private final NumberValue<Float> verticalMax;
    private float currentHorizontalFactor = -1.0f;
    private float currentVerticalFactor = -1.0f;
    private float targetHorizontalFactor = -1.0f;
    private float targetVerticalFactor = -1.0f;
    private int trackedEntityId = Integer.MIN_VALUE;

    public LinearAngleSmooth(NumberValue<Float> horizontalMin,
                             NumberValue<Float> horizontalMax,
                             NumberValue<Float> verticalMin,
                             NumberValue<Float> verticalMax) {
        super("Linear");
        this.horizontalMin = horizontalMin;
        this.horizontalMax = horizontalMax;
        this.verticalMin = verticalMin;
        this.verticalMax = verticalMax;
    }

    private static float pickFactor(float min, float max, float angle) {
        if (max <= min) return min;
        float normalized = Math.max(0.0f, Math.min(1.0f, angle / 90.0f));
        float center = lerp(min, max, normalized);
        float spread = Math.max(1.0f, (max - min) * 0.12f);
        return clamp(random(center - spread, center + spread), min, max);
    }

    private static float lerp(float from, float to, float factor) {
        return from + (to - from) * factor;
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static float random(float min, float max) {
        if (max <= min) return min;
        return (float) (ThreadLocalRandom.current().nextDouble(min, max));
    }

    @Override
    protected float[] calculateFactors(RotationTarget rotationTarget,
                                       Rotation currentRotation,
                                       Rotation targetRotation) {
        float hMin = Math.min(horizontalMin.get(), horizontalMax.get());
        float hMax = Math.max(horizontalMin.get(), horizontalMax.get());
        float vMin = Math.min(verticalMin.get(), verticalMax.get());
        float vMax = Math.max(verticalMin.get(), verticalMax.get());

        if (rotationTarget == null) {
            resetState();
            return new float[]{hMin, vMin};
        }

        int entityId = rotationTarget.entity != null ? rotationTarget.entity.getId() : Integer.MIN_VALUE;
        if (trackedEntityId != entityId) {
            trackedEntityId = entityId;
            currentHorizontalFactor = random(hMin, hMax);
            currentVerticalFactor = random(vMin, vMax);
            targetHorizontalFactor = currentHorizontalFactor;
            targetVerticalFactor = currentVerticalFactor;
        }

        float angle = currentRotation.angleTo(targetRotation);
        if (Math.abs(currentHorizontalFactor - targetHorizontalFactor) < 0.5f) {
            targetHorizontalFactor = pickFactor(hMin, hMax, angle);
        }
        if (Math.abs(currentVerticalFactor - targetVerticalFactor) < 0.5f) {
            targetVerticalFactor = pickFactor(vMin, vMax, angle);
        }

        float blend = angle > 45.0f ? 0.35f : angle > 12.0f ? 0.22f : 0.14f;
        currentHorizontalFactor = lerp(currentHorizontalFactor, targetHorizontalFactor, blend);
        currentVerticalFactor = lerp(currentVerticalFactor, targetVerticalFactor, blend);
        return new float[]{currentHorizontalFactor, currentVerticalFactor};
    }

    private void resetState() {
        trackedEntityId = Integer.MIN_VALUE;
        currentHorizontalFactor = -1.0f;
        currentVerticalFactor = -1.0f;
        targetHorizontalFactor = -1.0f;
        targetVerticalFactor = -1.0f;
    }
}
