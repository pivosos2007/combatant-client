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

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Short stop rotation processor.
 * <p>
 * Ported from LiquidBounce (CCBlueX).
 */
public final class ShortStopRotationProcessor implements RotationProcessor {

    public final BooleanValue enabled;
    private final NumberValue<Integer> rate;
    private final NumberValue<Integer> durationMin;
    private final NumberValue<Integer> durationMax;

    private int ticksElapsed = 0;
    private int currentDuration = 1;

    public ShortStopRotationProcessor(BooleanValue enabled,
                                      NumberValue<Integer> rate,
                                      NumberValue<Integer> durationMin,
                                      NumberValue<Integer> durationMax) {
        this.enabled = enabled;
        this.rate = rate;
        this.durationMin = durationMin;
        this.durationMax = durationMax;
    }

    private static int random(int min, int max) {
        if (max <= min) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static float randomFloat(float min, float max) {
        if (max <= min) return min;
        return (float) ThreadLocalRandom.current().nextDouble(min, max);
    }

    @Override
    public Rotation process(RotationTarget rotationTarget, Rotation currentRotation, Rotation targetRotation) {
        if (!enabled.get()) {
            return targetRotation;
        }

        if (currentRotation.angleTo(targetRotation) > 10.0f) {
            ticksElapsed = currentDuration;
            return targetRotation;
        }

        int chance = ThreadLocalRandom.current().nextInt(0, 101);
        if (rate.get() > chance) {
            currentDuration = random(durationMin.get(), durationMax.get());
            ticksElapsed = 0;
        }

        if (ticksElapsed < currentDuration) {
            ticksElapsed++;
            float yawSpeed = randomFloat(0.0f, 0.1f);
            float pitchSpeed = randomFloat(0.0f, 0.1f);
            return currentRotation.towardsLinear(targetRotation, yawSpeed, pitchSpeed);
        }

        return targetRotation;
    }
}
