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

import combatant.client.config.values.NumberValue;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;

public final class SnapAssistAimProcessor implements RotationProcessor {

    private final NumberValue<Float> snapThreshold;
    private final NumberValue<Float> snapFactor;

    public SnapAssistAimProcessor(NumberValue<Float> snapThreshold, NumberValue<Float> snapFactor) {
        this.snapThreshold = snapThreshold;
        this.snapFactor = snapFactor;
    }

    @Override
    public Rotation process(RotationTarget rotationTarget, Rotation currentRotation, Rotation targetRotation) {
        float angle = currentRotation.angleTo(targetRotation);
        if (angle <= snapThreshold.get()) {
            return targetRotation;
        }

        float rampWindow = Math.max(snapThreshold.get() * 2.0f, snapThreshold.get() + 1.0f);
        if (angle > rampWindow) {
            return targetRotation;
        }

        float factor = Math.max(1.0f, snapFactor.get()) * angle;
        return currentRotation.towardsLinear(targetRotation, factor, factor);
    }
}
