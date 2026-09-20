/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2fc;

/**
 * Canonical sub-pixel jitter producer for the final TAA/TAAU consumer.
 *
 * <p>Jitter is stored in render-pixel units with +X right and +Y down, matching texture UV space.
 * Projection application converts that convention to NDC (+Y up). Keeping the sample in pixel
 * units makes render-scale changes explicit and lets the resolve derive current/previous jitter UV
 * offsets from each frame's render extent.</p>
 */
public final class DeferredJitterSequence {
    private static final int PERIOD = 16;

    private DeferredJitterSequence() { }

    /** Centered Halton(2,3) sample in render-pixel units, each component in roughly [-0.5, 0.5]. */
    public static Vector2f sample(long frameId) {
        int index = (int) Math.floorMod(frameId, PERIOD) + 1;
        return new Vector2f(halton(index, 2) - 0.5f, halton(index, 3) - 0.5f);
    }

    /** Pre-multiplies a clip-space translation so geometry is actually rasterized at the jitter. */
    public static Matrix4f apply(Matrix4fc unjitteredProjection,
                                 Vector2fc jitterPixels,
                                 int renderWidth,
                                 int renderHeight) {
        if (unjitteredProjection == null) return new Matrix4f();
        Vector2fc jitter = jitterPixels == null ? new Vector2f() : jitterPixels;
        float ndcX = 2.0f * jitter.x() / Math.max(1, renderWidth);
        float ndcY = -2.0f * jitter.y() / Math.max(1, renderHeight);
        return new Matrix4f().translation(ndcX, ndcY, 0.0f).mul(unjitteredProjection);
    }

    private static float halton(int index, int base) {
        float value = 0.0f;
        float fraction = 1.0f / base;
        int i = Math.max(1, index);
        while (i > 0) {
            value += fraction * (i % base);
            i /= base;
            fraction /= base;
        }
        return value;
    }
}
