/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Optional source-local 3D parameters. No global OpenAL listener/distance state is changed. */
public record SoundSpatialization(
        Vec3 position,
        Vec3 velocity,
        Vec3 direction,
        @Nullable Float rolloff,
        @Nullable Float referenceDistance,
        @Nullable Float maxDistance,
        float coneInnerAngle,
        float coneOuterAngle,
        float coneOuterGain,
        float airAbsorption,
        float roomRolloff
) {
    public SoundSpatialization {
        position = position == null ? Vec3.ZERO : position;
        velocity = velocity == null ? Vec3.ZERO : velocity;
        direction = direction == null ? Vec3.ZERO : direction;
        coneInnerAngle = clamp(coneInnerAngle, 0.0f, 360.0f);
        coneOuterAngle = clamp(coneOuterAngle, coneInnerAngle, 360.0f);
        coneOuterGain = clamp(coneOuterGain, 0.0f, 1.0f);
        airAbsorption = clamp(airAbsorption, 0.0f, 10.0f);
        roomRolloff = clamp(roomRolloff, 0.0f, 10.0f);
    }

    public static SoundSpatialization at(Vec3 position) {
        return new SoundSpatialization(position, Vec3.ZERO, Vec3.ZERO, null, null, null,
                360.0f, 360.0f, 0.0f, 0.0f, 0.0f);
    }

    public SoundSpatialization withVelocity(Vec3 value) {
        return new SoundSpatialization(position, value, direction, rolloff, referenceDistance, maxDistance,
                coneInnerAngle, coneOuterAngle, coneOuterGain, airAbsorption, roomRolloff);
    }

    public SoundSpatialization withCone(Vec3 direction, float innerAngle, float outerAngle, float outerGain) {
        return new SoundSpatialization(position, velocity, direction, rolloff, referenceDistance, maxDistance,
                innerAngle, outerAngle, outerGain, airAbsorption, roomRolloff);
    }

    public SoundSpatialization withDistances(float reference, float maximum, float rolloffFactor) {
        return new SoundSpatialization(position, velocity, direction, rolloffFactor, reference, maximum,
                coneInnerAngle, coneOuterAngle, coneOuterGain, airAbsorption, roomRolloff);
    }

    public SoundSpatialization withEnvironment(float airAbsorptionFactor, float roomRolloffFactor) {
        return new SoundSpatialization(position, velocity, direction, rolloff, referenceDistance, maxDistance,
                coneInnerAngle, coneOuterAngle, coneOuterGain, airAbsorptionFactor, roomRolloffFactor);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
