/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Per-play overrides. Gain and pitch multiply the catalog defaults. */
public record SoundOptions(
        float gain,
        float pitch,
        @Nullable Boolean looping,
        @Nullable Boolean spatial,
        @Nullable SoundSpatialization spatialization,
        @Nullable SoundEqualizer equalizer
) {
    public static final SoundOptions DEFAULT = new SoundOptions(
            1.0f, 1.0f, null, null, null, null
    );

    public SoundOptions {
        gain = Math.max(0.0f, gain);
        pitch = Math.max(0.01f, pitch);
    }

    public static SoundOptions gain(double gain) {
        return DEFAULT.withGain(gain);
    }

    public static SoundOptions at(Vec3 position) {
        return DEFAULT.withPosition(position);
    }

    public static SoundOptions at(double x, double y, double z) {
        return at(new Vec3(x, y, z));
    }

    public SoundOptions withGain(double value) {
        return new SoundOptions((float) value, pitch, looping, spatial, spatialization, equalizer);
    }

    public SoundOptions withPitch(double value) {
        return new SoundOptions(gain, (float) value, looping, spatial, spatialization, equalizer);
    }

    public SoundOptions withLooping(boolean value) {
        return new SoundOptions(gain, pitch, value, spatial, spatialization, equalizer);
    }

    public SoundOptions relative() {
        return new SoundOptions(gain, pitch, looping, false, null, equalizer);
    }

    public SoundOptions withPosition(Vec3 value) {
        return withSpatialization(SoundSpatialization.at(value));
    }

    public SoundOptions withPosition(double x, double y, double z) {
        return withPosition(new Vec3(x, y, z));
    }

    public SoundOptions withDistances(double reference, double maximum, double rolloffFactor) {
        SoundSpatialization current = spatialization == null ? SoundSpatialization.at(Vec3.ZERO) : spatialization;
        return withSpatialization(current.withDistances((float) reference, (float) maximum, (float) rolloffFactor));
    }

    public SoundOptions withSpatialization(SoundSpatialization value) {
        return new SoundOptions(gain, pitch, looping, true, value, equalizer);
    }

    public SoundOptions withEqualizer(SoundEqualizer value) {
        return new SoundOptions(gain, pitch, looping, spatial, spatialization, value);
    }
}
