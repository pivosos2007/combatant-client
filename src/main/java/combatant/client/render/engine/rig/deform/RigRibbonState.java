/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.deform;

import combatant.client.render.engine.rig.deform.curve.RigRibbonCurve;
import combatant.client.render.engine.rig.shader.RigShaderLimits;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Dense per-instance ribbon sample storage. Frames are sampled on CPU once per curve update and
 * uploaded as one fixed-size UBO. Storage is flat primitive arrays to keep frame updates allocation-free.
 */
public final class RigRibbonState {
    private static final float EPSILON = 1.0e-10f;

    private final RigRibbonDefinition[] definitions = new RigRibbonDefinition[RigShaderLimits.MAX_DEFORMS];
    private final boolean[] active = new boolean[RigShaderLimits.MAX_DEFORMS];

    private final float[] positions = new float[RigShaderLimits.MAX_RIBBON_FRAMES * 3];
    private final float[] normals = new float[RigShaderLimits.MAX_RIBBON_FRAMES * 3];
    private final float[] binormals = new float[RigShaderLimits.MAX_RIBBON_FRAMES * 3];
    private final float[] tangents = new float[RigShaderLimits.MAX_RIBBON_FRAMES * 3];

    private final Vector3f samplePosition = new Vector3f();
    private final Vector3f sampleTangent = new Vector3f();
    private final Vector3f previousTangent = new Vector3f();
    private final Vector3f frameNormal = new Vector3f();
    private final Vector3f frameBinormal = new Vector3f();
    private final Vector3f rotationAxis = new Vector3f();

    public RigRibbonState define(RigRibbonDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("Rig ribbon definition must not be null");
        definitions[definition.id()] = definition;
        active[definition.id()] = false;
        clearFrames(definition.id());
        return this;
    }

    public RigRibbonState undefine(int id) {
        checkId(id);
        definitions[id] = null;
        active[id] = false;
        clearFrames(id);
        return this;
    }

    public RigRibbonState update(int id, RigRibbonCurve curve) {
        RigRibbonDefinition definition = requireDefinition(id);
        if (curve == null) throw new IllegalArgumentException("Rig ribbon curve must not be null");

        int count = definition.sampleCount();
        int baseFrame = id * RigShaderLimits.MAX_RIBBON_SAMPLES;

        for (int i = 0; i < count; i++) {
            float t = (float) i / (float) (count - 1);
            curve.sample(t, samplePosition);
            requireFinite(samplePosition, "sample position", id, i);
            put(positions, baseFrame + i, samplePosition);
        }

        for (int i = 0; i < count; i++) {
            float t = (float) i / (float) (count - 1);
            curve.tangent(t, sampleTangent);
            if (!finiteDirection(sampleTangent)) {
                fallbackTangent(baseFrame, count, i, sampleTangent);
            }
            if (!finiteDirection(sampleTangent)) {
                throw new IllegalStateException("Rig ribbon " + id + " has a degenerate tangent at sample " + i);
            }
            sampleTangent.normalize();
            put(tangents, baseFrame + i, sampleTangent);
        }

        previousTangent.set(definition.sourceTangent());
        frameNormal.set(definition.sourceNormal());
        frameBinormal.set(definition.sourceBinormal());

        for (int i = 0; i < count; i++) {
            get(tangents, baseFrame + i, sampleTangent);
            transport(previousTangent, sampleTangent, frameNormal, frameBinormal, definition.handedness());
            put(normals, baseFrame + i, frameNormal);
            put(binormals, baseFrame + i, frameBinormal);
            previousTangent.set(sampleTangent);
        }

        active[id] = true;
        return this;
    }

    public RigRibbonState disable(int id) {
        checkId(id);
        active[id] = false;
        return this;
    }

    public boolean defined(int id) {
        checkId(id);
        return definitions[id] != null;
    }

    public boolean active(int id) {
        checkId(id);
        return active[id];
    }

    public RigRibbonDefinition definition(int id) {
        checkId(id);
        return definitions[id];
    }

    public float positionX(int frame) { return positions[offset(frame)]; }
    public float positionY(int frame) { return positions[offset(frame) + 1]; }
    public float positionZ(int frame) { return positions[offset(frame) + 2]; }
    public float normalX(int frame) { return normals[offset(frame)]; }
    public float normalY(int frame) { return normals[offset(frame) + 1]; }
    public float normalZ(int frame) { return normals[offset(frame) + 2]; }
    public float binormalX(int frame) { return binormals[offset(frame)]; }
    public float binormalY(int frame) { return binormals[offset(frame) + 1]; }
    public float binormalZ(int frame) { return binormals[offset(frame) + 2]; }

    private void fallbackTangent(int baseFrame, int count, int index, Vector3f destination) {
        if (count <= 1) {
            destination.zero();
            return;
        }
        if (index == 0) {
            get(positions, baseFrame + 1, destination);
            get(positions, baseFrame, samplePosition);
            destination.sub(samplePosition);
        } else if (index == count - 1) {
            get(positions, baseFrame + count - 1, destination);
            get(positions, baseFrame + count - 2, samplePosition);
            destination.sub(samplePosition);
        } else {
            get(positions, baseFrame + index + 1, destination);
            get(positions, baseFrame + index - 1, samplePosition);
            destination.sub(samplePosition);
        }
    }

    private void transport(Vector3fc fromTangent,
                           Vector3fc toTangent,
                           Vector3f normal,
                           Vector3f binormal,
                           float handedness) {
        rotationAxis.set(fromTangent).cross(toTangent);
        float sin = rotationAxis.length();
        float cos = clamp(fromTangent.dot(toTangent), -1f, 1f);

        if (sin > 1.0e-6f) {
            rotationAxis.div(sin);
            float angle = (float) Math.atan2(sin, cos);
            rotate(normal, rotationAxis, angle);
            rotate(binormal, rotationAxis, angle);
        } else if (cos < 0f) {
            // Exact 180-degree reversal has no unique minimal axis. Rotating around the current normal
            // preserves that axis and flips the binormal consistently with the reversed tangent.
            rotate(binormal, normal, (float) Math.PI);
        }

        // Suppress accumulated numerical drift and rebuild a stable frame with preserved handedness.
        normal.fma(-normal.dot(toTangent), toTangent);
        if (!finiteDirection(normal)) {
            choosePerpendicular(toTangent, normal);
        }
        normal.normalize();
        binormal.set(normal).cross(toTangent).mul(handedness).normalize();
    }

    private static void choosePerpendicular(Vector3fc tangent, Vector3f destination) {
        float ax = Math.abs(tangent.x());
        float ay = Math.abs(tangent.y());
        float az = Math.abs(tangent.z());
        if (ax <= ay && ax <= az) destination.set(1f, 0f, 0f);
        else if (ay <= az) destination.set(0f, 1f, 0f);
        else destination.set(0f, 0f, 1f);
        destination.fma(-destination.dot(tangent), tangent);
    }

    private static void rotate(Vector3f value, Vector3fc axis, float angle) {
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        float dot = value.dot(axis);
        float x = value.x;
        float y = value.y;
        float z = value.z;
        float crossX = axis.y() * z - axis.z() * y;
        float crossY = axis.z() * x - axis.x() * z;
        float crossZ = axis.x() * y - axis.y() * x;
        float oneMinusC = 1f - c;
        value.set(
                x * c + crossX * s + axis.x() * dot * oneMinusC,
                y * c + crossY * s + axis.y() * dot * oneMinusC,
                z * c + crossZ * s + axis.z() * dot * oneMinusC
        );
    }

    private void clearFrames(int id) {
        int base = id * RigShaderLimits.MAX_RIBBON_SAMPLES;
        for (int i = 0; i < RigShaderLimits.MAX_RIBBON_SAMPLES; i++) {
            int offset = offset(base + i);
            positions[offset] = positions[offset + 1] = positions[offset + 2] = 0f;
            normals[offset] = normals[offset + 1] = normals[offset + 2] = 0f;
            binormals[offset] = binormals[offset + 1] = binormals[offset + 2] = 0f;
            tangents[offset] = tangents[offset + 1] = tangents[offset + 2] = 0f;
        }
    }

    private RigRibbonDefinition requireDefinition(int id) {
        checkId(id);
        RigRibbonDefinition definition = definitions[id];
        if (definition == null) throw new IllegalStateException("Rig ribbon " + id + " has no definition");
        return definition;
    }

    private static void put(float[] values, int frame, Vector3fc value) {
        int offset = offset(frame);
        values[offset] = value.x();
        values[offset + 1] = value.y();
        values[offset + 2] = value.z();
    }

    private static void get(float[] values, int frame, Vector3f destination) {
        int offset = offset(frame);
        destination.set(values[offset], values[offset + 1], values[offset + 2]);
    }

    private static int offset(int frame) {
        if (frame < 0 || frame >= RigShaderLimits.MAX_RIBBON_FRAMES) {
            throw new IndexOutOfBoundsException("Rig ribbon frame outside [0," + (RigShaderLimits.MAX_RIBBON_FRAMES - 1) + "]: " + frame);
        }
        return frame * 3;
    }

    private static boolean finiteDirection(Vector3fc value) {
        return Float.isFinite(value.x()) && Float.isFinite(value.y()) && Float.isFinite(value.z()) && value.lengthSquared() > EPSILON;
    }

    private static void requireFinite(Vector3fc value, String what, int id, int sample) {
        if (!Float.isFinite(value.x()) || !Float.isFinite(value.y()) || !Float.isFinite(value.z())) {
            throw new IllegalStateException("Rig ribbon " + id + " produced non-finite " + what + " at sample " + sample);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void checkId(int id) {
        if (id < 0 || id >= RigShaderLimits.MAX_DEFORMS) {
            throw new IndexOutOfBoundsException("Rig ribbon id outside [0," + (RigShaderLimits.MAX_DEFORMS - 1) + "]: " + id);
        }
    }
}
