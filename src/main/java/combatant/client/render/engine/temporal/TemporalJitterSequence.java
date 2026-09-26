/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.temporal;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2fc;

/** Low-discrepancy R2 sub-pixel sequence used internally by production TAA. */
public final class TemporalJitterSequence {
    // Photon native-resolution TAA attenuates its R2 offset by 0.66. Apply the same
    // attenuation here instead of using the full half-pixel sequence.
    public static final float NATIVE_TAA_JITTER_SCALE = 0.66f;
    private static final double R2_X = 1.324717957244746;
    private static final double R2_Y = 1.7548776662466927;

    private TemporalJitterSequence() { }

    public static Vector2f sample(long frameId) {
        double x = fractional(R2_X * frameId + 0.5) - 0.5;
        double y = fractional(R2_Y * frameId + 0.5) - 0.5;
        return new Vector2f((float) x, (float) y);
    }

    private static double fractional(double value) {
        return value - Math.floor(value);
    }

    /** Exact framebuffer UV translation produced by {@link #apply}. */
    public static Vector2f uvOffset(Vector2fc jitterSample, int width) {
        Vector2fc jitter = jitterSample == null ? new Vector2f() : jitterSample;
        float scale = NATIVE_TAA_JITTER_SCALE / Math.max(1, width);
        return new Vector2f(jitter.x() * scale, jitter.y() * scale);
    }

    public static Matrix4f apply(Matrix4fc unjitteredProjection, Vector2fc jitterPixels, int width, int height) {
        if (unjitteredProjection == null) return new Matrix4f();
        // Photon defines taa_offset as (r2.x / viewWidth, r2.y / viewWidth), then applies
        // taa_offset * clip.w * 0.66. sample() stores half of the [-1, 1] R2 value, so
        // framebuffer UV motion is sample * 0.66 / viewWidth and NDC motion is twice that.
        Vector2f uvOffset = uvOffset(jitterPixels, width);
        return new Matrix4f().translation(2.0f * uvOffset.x, 2.0f * uvOffset.y, 0.0f)
                .mul(unjitteredProjection);
    }
}
