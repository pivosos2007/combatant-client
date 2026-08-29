/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.deform.curve;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Mutable cubic Hermite segment with endpoint positions and endpoint tangents. */
public final class RigHermiteCurve implements RigRibbonCurve {
    private final Vector3f p0 = new Vector3f();
    private final Vector3f p1 = new Vector3f();
    private final Vector3f m0 = new Vector3f();
    private final Vector3f m1 = new Vector3f();

    public RigHermiteCurve(Vector3fc p0, Vector3fc p1, Vector3fc m0, Vector3fc m1) {
        set(p0, p1, m0, m1);
    }

    public RigHermiteCurve set(Vector3fc p0, Vector3fc p1, Vector3fc m0, Vector3fc m1) {
        requireFinite(p0, "p0");
        requireFinite(p1, "p1");
        requireFinite(m0, "m0");
        requireFinite(m1, "m1");
        this.p0.set(p0);
        this.p1.set(p1);
        this.m0.set(m0);
        this.m1.set(m1);
        return this;
    }

    @Override
    public Vector3f sample(float t, Vector3f destination) {
        t = clamp01(t);
        float t2 = t * t;
        float t3 = t2 * t;
        float h00 = 2f * t3 - 3f * t2 + 1f;
        float h10 = t3 - 2f * t2 + t;
        float h01 = -2f * t3 + 3f * t2;
        float h11 = t3 - t2;
        return destination.set(
                p0.x * h00 + m0.x * h10 + p1.x * h01 + m1.x * h11,
                p0.y * h00 + m0.y * h10 + p1.y * h01 + m1.y * h11,
                p0.z * h00 + m0.z * h10 + p1.z * h01 + m1.z * h11
        );
    }

    @Override
    public Vector3f tangent(float t, Vector3f destination) {
        t = clamp01(t);
        float t2 = t * t;
        float h00 = 6f * t2 - 6f * t;
        float h10 = 3f * t2 - 4f * t + 1f;
        float h01 = -6f * t2 + 6f * t;
        float h11 = 3f * t2 - 2f * t;
        return destination.set(
                p0.x * h00 + m0.x * h10 + p1.x * h01 + m1.x * h11,
                p0.y * h00 + m0.y * h10 + p1.y * h01 + m1.y * h11,
                p0.z * h00 + m0.z * h10 + p1.z * h01 + m1.z * h11
        );
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Rig curve t must be finite");
        return Math.max(0f, Math.min(1f, value));
    }

    private static void requireFinite(Vector3fc value, String name) {
        if (value == null || !Float.isFinite(value.x()) || !Float.isFinite(value.y()) || !Float.isFinite(value.z())) {
            throw new IllegalArgumentException("Rig Hermite " + name + " must be finite");
        }
    }
}
