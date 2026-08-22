/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

/**
 * Full four-band equalizer exposed by OpenAL EFX.
 * Gains are specified in dB and clamped to the EFX range (-18..+18 dB).
 */
public record SoundEqualizer(
        float lowGainDb,
        float lowCutoffHz,
        float mid1GainDb,
        float mid1CenterHz,
        float mid1Width,
        float mid2GainDb,
        float mid2CenterHz,
        float mid2Width,
        float highGainDb,
        float highCutoffHz,
        float wetMix
) {
    public static final SoundEqualizer FLAT = new SoundEqualizer(
            0.0f, 200.0f,
            0.0f, 500.0f, 1.0f,
            0.0f, 3_000.0f, 1.0f,
            0.0f, 6_000.0f,
            1.0f
    );

    public SoundEqualizer {
        lowGainDb = clamp(lowGainDb, -18.0f, 18.0f);
        lowCutoffHz = clamp(lowCutoffHz, 50.0f, 800.0f);
        mid1GainDb = clamp(mid1GainDb, -18.0f, 18.0f);
        mid1CenterHz = clamp(mid1CenterHz, 200.0f, 3_000.0f);
        mid1Width = clamp(mid1Width, 0.01f, 1.0f);
        mid2GainDb = clamp(mid2GainDb, -18.0f, 18.0f);
        mid2CenterHz = clamp(mid2CenterHz, 1_000.0f, 8_000.0f);
        mid2Width = clamp(mid2Width, 0.01f, 1.0f);
        highGainDb = clamp(highGainDb, -18.0f, 18.0f);
        highCutoffHz = clamp(highCutoffHz, 4_000.0f, 16_000.0f);
        wetMix = clamp(wetMix, 0.0f, 1.0f);
    }

    public SoundEqualizer withLow(float gainDb, float cutoffHz) {
        return new SoundEqualizer(gainDb, cutoffHz, mid1GainDb, mid1CenterHz, mid1Width,
                mid2GainDb, mid2CenterHz, mid2Width, highGainDb, highCutoffHz, wetMix);
    }

    public SoundEqualizer withMid1(float gainDb, float centerHz, float width) {
        return new SoundEqualizer(lowGainDb, lowCutoffHz, gainDb, centerHz, width,
                mid2GainDb, mid2CenterHz, mid2Width, highGainDb, highCutoffHz, wetMix);
    }

    public SoundEqualizer withMid2(float gainDb, float centerHz, float width) {
        return new SoundEqualizer(lowGainDb, lowCutoffHz, mid1GainDb, mid1CenterHz, mid1Width,
                gainDb, centerHz, width, highGainDb, highCutoffHz, wetMix);
    }

    public SoundEqualizer withHigh(float gainDb, float cutoffHz) {
        return new SoundEqualizer(lowGainDb, lowCutoffHz, mid1GainDb, mid1CenterHz, mid1Width,
                mid2GainDb, mid2CenterHz, mid2Width, gainDb, cutoffHz, wetMix);
    }

    public SoundEqualizer withWetMix(float mix) {
        return new SoundEqualizer(lowGainDb, lowCutoffHz, mid1GainDb, mid1CenterHz, mid1Width,
                mid2GainDb, mid2CenterHz, mid2Width, highGainDb, highCutoffHz, mix);
    }

    float lowGainLinear() { return dbToLinear(lowGainDb); }
    float mid1GainLinear() { return dbToLinear(mid1GainDb); }
    float mid2GainLinear() { return dbToLinear(mid2GainDb); }
    float highGainLinear() { return dbToLinear(highGainDb); }

    private static float dbToLinear(float db) {
        return clamp((float) Math.pow(10.0, db / 20.0), 0.126f, 7.943f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
