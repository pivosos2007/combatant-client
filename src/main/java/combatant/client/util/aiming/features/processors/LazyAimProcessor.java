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

public final class LazyAimProcessor implements RotationProcessor {

    private final NumberValue<Float> threshold;
    private final NumberValue<Float> followFactor;

    public LazyAimProcessor(NumberValue<Float> threshold, NumberValue<Float> followFactor) {
        this.threshold = threshold;
        this.followFactor = followFactor;
    }

    @Override
    public Rotation process(RotationTarget rotationTarget, Rotation currentRotation, Rotation targetRotation) {
        float angle = currentRotation.angleTo(targetRotation);
        if (angle <= threshold.get()) {
            return currentRotation;
        }

        float factor = Math.max(0.1f, followFactor.get()) * Math.max(angle, 1.0f);
        return currentRotation.towardsLinear(targetRotation, factor, factor);
    }
}
