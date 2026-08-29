/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.deform.curve;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Mutable uniform Catmull-Rom segment spanning p1 -> p2, with p0/p3 supplying endpoint context. */
public final class RigCatmullRomCurve implements RigRibbonCurve {
    private final Vector3f p0 = new Vector3f();
    private final Vector3f p1 = new Vector3f();
    private final Vector3f p2 = new Vector3f();
    private final Vector3f p3 = new Vector3f();

    public RigCatmullRomCurve(Vector3fc p0, Vector3fc p1, Vector3fc p2, Vector3fc p3) {
        set(p0, p1, p2, p3);
    }

    public RigCatmullRomCurve set(Vector3fc p0, Vector3fc p1, Vector3fc p2, Vector3fc p3) {
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
        float t2 = t * t;
        float t3 = t2 * t;
        return destination.set(
                0.5f * ((2f * p1.x) + (-p0.x + p2.x) * t + (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 + (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3),
                0.5f * ((2f * p1.y) + (-p0.y + p2.y) * t + (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 + (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3),
                0.5f * ((2f * p1.z) + (-p0.z + p2.z) * t + (2f * p0.z - 5f * p1.z + 4f * p2.z - p3.z) * t2 + (-p0.z + 3f * p1.z - 3f * p2.z + p3.z) * t3)
        );
    }

    @Override
    public Vector3f tangent(float t, Vector3f destination) {
        t = clamp01(t);
        float t2 = t * t;
        return destination.set(
                0.5f * ((-p0.x + p2.x) + 2f * (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t + 3f * (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t2),
                0.5f * ((-p0.y + p2.y) + 2f * (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t + 3f * (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t2),
                0.5f * ((-p0.z + p2.z) + 2f * (2f * p0.z - 5f * p1.z + 4f * p2.z - p3.z) * t + 3f * (-p0.z + 3f * p1.z - 3f * p2.z + p3.z) * t2)
        );
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Rig curve t must be finite");
        return Math.max(0f, Math.min(1f, value));
    }

    private static void requireFinite(Vector3fc value, String name) {
        if (value == null || !Float.isFinite(value.x()) || !Float.isFinite(value.y()) || !Float.isFinite(value.z())) {
            throw new IllegalArgumentException("Rig Catmull-Rom " + name + " must be finite");
        }
    }
}
