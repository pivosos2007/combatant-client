/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.deform.curve;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Mutable cubic Bezier curve suitable for per-frame procedural control-point updates. */
public final class RigCubicBezierCurve implements RigRibbonCurve {
    private final Vector3f p0 = new Vector3f();
    private final Vector3f p1 = new Vector3f();
    private final Vector3f p2 = new Vector3f();
    private final Vector3f p3 = new Vector3f();

    public RigCubicBezierCurve(Vector3fc p0, Vector3fc p1, Vector3fc p2, Vector3fc p3) {
        set(p0, p1, p2, p3);
    }

    public RigCubicBezierCurve set(Vector3fc p0, Vector3fc p1, Vector3fc p2, Vector3fc p3) {
        requireFinite(p0, "p0");
        requireFinite(p1, "p1");
        requireFinite(p2, "p2");
        requireFinite(p3, "p3");
        this.p0.set(p0);
        this.p1.set(p1);
        this.p2.set(p2);
        this.p3.set(p3);
        return this;
    }

    @Override
    public Vector3f sample(float t, Vector3f destination) {
        t = clamp01(t);
        float it = 1f - t;
        float w0 = it * it * it;
        float w1 = 3f * it * it * t;
        float w2 = 3f * it * t * t;
        float w3 = t * t * t;
        return destination.set(
                p0.x * w0 + p1.x * w1 + p2.x * w2 + p3.x * w3,
                p0.y * w0 + p1.y * w1 + p2.y * w2 + p3.y * w3,
                p0.z * w0 + p1.z * w1 + p2.z * w2 + p3.z * w3
        );
    }

    @Override
    public Vector3f tangent(float t, Vector3f destination) {
        t = clamp01(t);
        float it = 1f - t;
        float w0 = 3f * it * it;
        float w1 = 6f * it * t;
        float w2 = 3f * t * t;
        return destination.set(
                (p1.x - p0.x) * w0 + (p2.x - p1.x) * w1 + (p3.x - p2.x) * w2,
                (p1.y - p0.y) * w0 + (p2.y - p1.y) * w1 + (p3.y - p2.y) * w2,
                (p1.z - p0.z) * w0 + (p2.z - p1.z) * w1 + (p3.z - p2.z) * w2
        );
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Rig curve t must be finite");
        return Math.max(0f, Math.min(1f, value));
    }

    private static void requireFinite(Vector3fc value, String name) {
        if (value == null || !Float.isFinite(value.x()) || !Float.isFinite(value.y()) || !Float.isFinite(value.z())) {
            throw new IllegalArgumentException("Rig Bezier " + name + " must be finite");
        }
    }
}
