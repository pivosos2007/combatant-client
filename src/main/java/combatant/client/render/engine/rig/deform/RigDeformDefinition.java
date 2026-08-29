/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.deform;

import combatant.client.render.engine.rig.shader.RigShaderLimits;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Immutable bind-space frame for procedural deformation.
 * Dynamic bend/twist angles live in {@link RigDeformState}.
 */
public final class RigDeformDefinition {
    private static final float EPSILON = 1.0e-6f;

    private final int id;
    private final Vector3f origin;
    private final Vector3f axis;
    private final Vector3f bendAxis;
    private final float length;
    private final float bendStart;
    private final float bendEnd;
    private final float twistStart;
    private final float twistEnd;

    public RigDeformDefinition(int id,
                               Vector3fc origin,
                               Vector3fc axis,
                               Vector3fc bendAxis,
                               float length,
                               float bendStart,
                               float bendEnd,
                               float twistStart,
                               float twistEnd) {
        if (id < 0 || id >= RigShaderLimits.MAX_DEFORMS) {
            throw new IllegalArgumentException("Rig deform id outside shader capacity [0,"
                    + (RigShaderLimits.MAX_DEFORMS - 1) + "]: " + id);
        }
        if (origin == null || axis == null || bendAxis == null) {
            throw new IllegalArgumentException("Rig deform origin/axis/bendAxis must not be null");
        }
        if (!(length > EPSILON) || !Float.isFinite(length)) {
            throw new IllegalArgumentException("Rig deform length must be finite and > 0: " + length);
        }
        validateRange("bend", bendStart, bendEnd);
        validateRange("twist", twistStart, twistEnd);

        Vector3f originCopy = new Vector3f(origin);
        if (!finite(originCopy)) {
            throw new IllegalArgumentException("Rig deform origin must be finite");
        }

        Vector3f normalizedAxis = new Vector3f(axis);
        if (normalizedAxis.lengthSquared() <= EPSILON || !finite(normalizedAxis)) {
            throw new IllegalArgumentException("Rig deform axis must be finite and non-zero");
        }
        normalizedAxis.normalize();

        // A bend axis represents the hinge direction. Remove any longitudinal component so the
        // constant-curvature construction remains stable for arbitrary caller-provided orientation.
        Vector3f normalizedBendAxis = new Vector3f(bendAxis);
        if (normalizedBendAxis.lengthSquared() <= EPSILON || !finite(normalizedBendAxis)) {
            throw new IllegalArgumentException("Rig deform bend axis must be finite and non-zero");
        }
        float longitudinalComponent = normalizedBendAxis.dot(normalizedAxis);
        normalizedBendAxis.set(
                normalizedBendAxis.x - normalizedAxis.x * longitudinalComponent,
                normalizedBendAxis.y - normalizedAxis.y * longitudinalComponent,
                normalizedBendAxis.z - normalizedAxis.z * longitudinalComponent
        );
        if (normalizedBendAxis.lengthSquared() <= EPSILON) {
            throw new IllegalArgumentException("Rig deform bend axis must not be parallel to longitudinal axis");
        }
        normalizedBendAxis.normalize();

        this.id = id;
        this.origin = originCopy;
        this.axis = normalizedAxis;
        this.bendAxis = normalizedBendAxis;
        this.length = length;
        this.bendStart = bendStart;
        this.bendEnd = bendEnd;
        this.twistStart = twistStart;
        this.twistEnd = twistEnd;
    }

    public static RigDeformDefinition fullLength(int id,
                                                 Vector3fc origin,
                                                 Vector3fc axis,
                                                 Vector3fc bendAxis,
                                                 float length) {
        return new RigDeformDefinition(id, origin, axis, bendAxis, length, 0f, 1f, 0f, 1f);
    }

    private static boolean finite(Vector3f value) {
        return Float.isFinite(value.x) && Float.isFinite(value.y) && Float.isFinite(value.z);
    }

    private static void validateRange(String name, float start, float end) {
        if (!Float.isFinite(start) || !Float.isFinite(end) || start < 0f || end > 1f || !(end > start)) {
            throw new IllegalArgumentException("Rig " + name + " range must satisfy 0 <= start < end <= 1: "
                    + start + ".." + end);
        }
    }

    public int id() { return id; }
    public Vector3fc origin() { return origin; }
    public Vector3fc axis() { return axis; }
    public Vector3fc bendAxis() { return bendAxis; }
    public float length() { return length; }
    public float bendStart() { return bendStart; }
    public float bendEnd() { return bendEnd; }
    public float twistStart() { return twistStart; }
    public float twistEnd() { return twistEnd; }
}
