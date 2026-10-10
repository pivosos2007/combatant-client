/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.trail;

import combatant.client.render.engine.rig.deform.curve.RigRibbonCurve;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;

/**
 * Arc-length parameterized Catmull-Rom curve used by world trails.
 *
 * <p>The input points are immutable for the lifetime of this curve and are expected to be in the
 * same camera-relative coordinate system consumed by the world rig pipeline. Parameterizing by
 * cumulative distance instead of history index prevents short tick segments from producing visible
 * speed/bias changes when the actor moves slowly.</p>
 */
public final class TrailRibbonCurve implements RigRibbonCurve {
    private static final float EPSILON = 1.0e-5f;

    private final Vector3f[] points;
    private final float[] distance;
    private final float totalLength;
    private final boolean lockWorldUp;
    private final Vector3f scratchLeft = new Vector3f();
    private final Vector3f scratchRight = new Vector3f();

    public TrailRibbonCurve(List<? extends Vector3fc> input) {
        this(input, false);
    }

    public TrailRibbonCurve(List<? extends Vector3fc> input, boolean lockWorldUp) {
        if (input == null || input.size() < 2) {
            throw new IllegalArgumentException("Trail ribbon requires at least two points");
        }

        points = new Vector3f[input.size()];
        distance = new float[input.size()];
        float length = 0.0f;
        for (int i = 0; i < input.size(); i++) {
            Vector3fc point = input.get(i);
            if (point == null || !finite(point)) {
                throw new IllegalArgumentException("Trail ribbon contains a non-finite point at " + i);
            }
            points[i] = new Vector3f(point);
            if (i > 0) {
                float step = points[i].distance(points[i - 1]);
                if (Float.isFinite(step)) length += step;
                distance[i] = length;
            }
        }
        if (!(length > EPSILON)) {
            throw new IllegalArgumentException("Trail ribbon has zero arc length");
        }
        totalLength = length;
        this.lockWorldUp = lockWorldUp;
    }

    @Override
    public Vector3f sample(float t, Vector3f destination) {
        Segment segment = segment(t);
        int i = segment.index;
        Vector3fc p1 = points[i];
        Vector3fc p2 = points[i + 1];

        // Repeating an endpoint is numerically hostile to non-uniform Catmull-Rom because its knot
        // interval collapses. Mirror the adjacent point instead, which gives a stable endpoint tangent
        // and still interpolates p1/p2 exactly.
        float p0x = i > 0 ? points[i - 1].x() : 2.0f * p1.x() - p2.x();
        float p0y = i > 0 ? points[i - 1].y() : 2.0f * p1.y() - p2.y();
        float p0z = i > 0 ? points[i - 1].z() : 2.0f * p1.z() - p2.z();
        float p3x = i + 2 < points.length ? points[i + 2].x() : 2.0f * p2.x() - p1.x();
        float p3y = i + 2 < points.length ? points[i + 2].y() : 2.0f * p2.y() - p1.y();
        float p3z = i + 2 < points.length ? points[i + 2].z() : 2.0f * p2.z() - p1.z();

        return centripetal(
                p0x, p0y, p0z,
                p1.x(), p1.y(), p1.z(),
                p2.x(), p2.y(), p2.z(),
                p3x, p3y, p3z,
                segment.local,
                destination
        );
    }

    @Override
    public Vector3f tangent(float t, Vector3f destination) {
        // Numerical derivative of the same centripetal curve used by sample(). Keeping the position
        // and tangent definitions identical is more important here than saving a few scalar ops.
        float dt = Math.max(1.0e-3f, 0.35f / Math.max(8.0f, points.length));
        float a = clamp01(t - dt);
        float b = clamp01(t + dt);
        if (b - a <= EPSILON) {
            a = clamp01(t - 2.0f * dt);
            b = clamp01(t + 2.0f * dt);
        }
        sample(a, scratchLeft);
        sample(b, scratchRight);
        destination.set(scratchRight).sub(scratchLeft);
        if (lockWorldUp) destination.y = 0.0f;

        if (destination.lengthSquared() <= EPSILON * EPSILON) {
            Segment segment = segment(t);
            Vector3fc p1 = points[segment.index];
            Vector3fc p2 = points[Math.min(points.length - 1, segment.index + 1)];
            destination.set(p2).sub(p1);
            if (lockWorldUp) destination.y = 0.0f;
        }
        if (lockWorldUp && destination.lengthSquared() <= EPSILON * EPSILON) {
            destination.set(1.0f, 0.0f, 0.0f);
        }
        return destination;
    }

    public float totalLength() {
        return totalLength;
    }

    private Segment segment(float t) {
        float target = clamp01(t) * totalLength;
        int low = 0;
        int high = distance.length - 1;
        while (low + 1 < high) {
            int mid = (low + high) >>> 1;
            if (distance[mid] <= target) low = mid;
            else high = mid;
        }
        int index = Math.min(low, points.length - 2);
        float start = distance[index];
        float end = distance[index + 1];
        float local = end - start > EPSILON ? (target - start) / (end - start) : 0.0f;
        return new Segment(index, clamp01(local));
    }

    private static Vector3f centripetal(float p0x, float p0y, float p0z,
                                        float p1x, float p1y, float p1z,
                                        float p2x, float p2y, float p2z,
                                        float p3x, float p3y, float p3z,
                                        float local, Vector3f out) {
        // True centripetal Catmull-Rom (alpha = 0.5). The outer A1/A3 interpolation stages must
        // extrapolate; clamping them to [0,1] changes the spline and creates kinks/side pulls.
        float t0 = 0.0f;
        float t1 = t0 + knot(p0x, p0y, p0z, p1x, p1y, p1z);
        float t2 = t1 + knot(p1x, p1y, p1z, p2x, p2y, p2z);
        float t3 = t2 + knot(p2x, p2y, p2z, p3x, p3y, p3z);
        float t = t1 + (t2 - t1) * clamp01(local);

        return out.set(
                centripetalComponent(p0x, p1x, p2x, p3x, t0, t1, t2, t3, t),
                centripetalComponent(p0y, p1y, p2y, p3y, t0, t1, t2, t3, t),
                centripetalComponent(p0z, p1z, p2z, p3z, t0, t1, t2, t3, t)
        );
    }

    private static float centripetalComponent(float p0, float p1, float p2, float p3,
                                              float t0, float t1, float t2, float t3, float t) {
        float a1 = knotLerp(p0, p1, t0, t1, t);
        float a2 = knotLerp(p1, p2, t1, t2, t);
        float a3 = knotLerp(p2, p3, t2, t3, t);
        float b1 = knotLerp(a1, a2, t0, t2, t);
        float b2 = knotLerp(a2, a3, t1, t3, t);
        return knotLerp(b1, b2, t1, t2, t);
    }

    private static float knot(float ax, float ay, float az, float bx, float by, float bz) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float distanceSquared = dx * dx + dy * dy + dz * dz;
        // alpha=.5 => knot increment = |Pj-Pi|^.5 = (squared distance)^.25
        return Math.max(1.0e-3f, (float) Math.sqrt(Math.sqrt(Math.max(distanceSquared, 0.0f))));
    }

    private static float knotLerp(float a, float b, float ta, float tb, float t) {
        float denom = tb - ta;
        if (Math.abs(denom) <= 1.0e-6f) return a;
        float w = (t - ta) / denom;
        return a + (b - a) * w;
    }

    private static boolean finite(Vector3fc value) {
        return Float.isFinite(value.x()) && Float.isFinite(value.y()) && Float.isFinite(value.z());
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private record Segment(int index, float local) {
    }
}
