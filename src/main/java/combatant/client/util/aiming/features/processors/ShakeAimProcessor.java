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
import combatant.client.config.values.NumberValue;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;

public final class ShakeAimProcessor implements RotationProcessor {

    private final NumberValue<Float> yawAmplitude;
    private final NumberValue<Float> pitchAmplitude;
    private final NumberValue<Float> speed;

    public ShakeAimProcessor(NumberValue<Float> yawAmplitude,
                             NumberValue<Float> pitchAmplitude,
                             NumberValue<Float> speed) {
        this.yawAmplitude = yawAmplitude;
        this.pitchAmplitude = pitchAmplitude;
        this.speed = speed;
    }

    @Override
    public Rotation process(RotationTarget rotationTarget, Rotation currentRotation, Rotation targetRotation) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return targetRotation;
        }

        float phase = mc.player.tickCount * Math.max(0.01f, speed.get());
        int entityId = rotationTarget != null && rotationTarget.entity != null ? rotationTarget.entity.getId() : 0;
        float seed = entityId * 0.173f;

        float yawOffset = (float) Math.sin(phase + seed) * yawAmplitude.get();
        float pitchOffset = (float) Math.cos(phase * 1.17f + seed) * pitchAmplitude.get();
        return new Rotation(targetRotation.yaw() + yawOffset, targetRotation.pitch() + pitchOffset);
    }
}
