/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are adapted from GFBS-glTF 1.5.1
 * (commit 8906d4083c23b5d5bbf5191a0555d149a5272900).
 * Copyright (c) 2026 LytharaLab. Original portions are licensed under the MIT License.
 * See THIRD_PARTY_LICENSES/GFBS-glTF-MIT.txt.
 *
 * Combatant modifications are distributed under GNU GPL v3.0 as part of Combatant.
 */
package combatant.client.render.engine.asset.model.animation;

import java.util.Objects;

public final class AnimationSampler {
    private final float[] times;
    private final float[] values;
    private final int components;
    private final Interpolation interpolation;

    public AnimationSampler(float[] times, float[] values, int components, Interpolation interpolation) {
        Objects.requireNonNull(times, "times");
        Objects.requireNonNull(values, "values");
        this.interpolation = Objects.requireNonNull(interpolation, "interpolation");
        if (times.length == 0) throw new IllegalArgumentException("Animation sampler has no keyframes");
        if (components <= 0) throw new IllegalArgumentException("Animation sampler component count must be positive");
        int multiplier = interpolation == Interpolation.CUBIC_SPLINE ? 3 : 1;
        long expected = (long) times.length * components * multiplier;
        if (expected > Integer.MAX_VALUE || values.length != (int) expected) {
            throw new IllegalArgumentException("Animation output length is inconsistent with input");
        }
        float previous = Float.NEGATIVE_INFINITY;
        for (float time : times) {
            if (!Float.isFinite(time)) throw new IllegalArgumentException("Animation keyframe time is not finite");
            if (time < 0.0f) throw new IllegalArgumentException("Animation keyframe time must be non-negative");
            if (time <= previous) throw new IllegalArgumentException("Animation keyframe times must be strictly increasing");
            previous = time;
        }
        for (float value : values) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Animation value is not finite");
        }
        this.times = times.clone();
        this.values = values.clone();
        this.components = components;
    }

    public float[] times() { return times.clone(); }
    public float[] values() { return values.clone(); }
    public int components() { return components; }
    public Interpolation interpolation() { return interpolation; }
    public int keyframeCount() { return times.length; }
    public float time(int keyframe) { return times[keyframe]; }
    public float value(int index) { return values[index]; }
    public float startTime() { return times[0]; }
    public float endTime() { return times[times.length - 1]; }
}
