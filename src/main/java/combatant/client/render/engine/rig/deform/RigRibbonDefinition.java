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
 * Bind-space source frame for ribbon reconstruction. The source frame maps the compile-time
 * longitudinal/lateral/depth coordinates onto each sampled parallel-transport frame.
 */
public final class RigRibbonDefinition {
    private static final float EPSILON = 1.0e-6f;

    private final int id;
    private final int sampleCount;
    private final Vector3f sourceTangent;
    private final Vector3f sourceNormal;
    private final Vector3f sourceBinormal;
    private final float handedness;

    public RigRibbonDefinition(int id,
                               int sampleCount,
                               Vector3fc sourceTangent,
                               Vector3fc sourceNormal,
                               Vector3fc sourceBinormal) {
        if (id < 0 || id >= RigShaderLimits.MAX_DEFORMS) {
            throw new IllegalArgumentException("Rig ribbon id outside shader capacity [0," + (RigShaderLimits.MAX_DEFORMS - 1) + "]: " + id);
        }
        if (sampleCount < 2 || sampleCount > RigShaderLimits.MAX_RIBBON_SAMPLES) {
            throw new IllegalArgumentException("Rig ribbon sample count must be in [2," + RigShaderLimits.MAX_RIBBON_SAMPLES + "]: " + sampleCount);
        }
        if (sourceTangent == null || sourceNormal == null || sourceBinormal == null) {
            throw new IllegalArgumentException("Rig ribbon source frame must not be null");
        }

        Vector3f tangent = new Vector3f(sourceTangent);
        Vector3f normal = new Vector3f(sourceNormal);
        Vector3f binormalHint = new Vector3f(sourceBinormal);
        requireDirection(tangent, "source tangent");
        requireDirection(normal, "source normal");
        requireDirection(binormalHint, "source binormal");
        tangent.normalize();

        normal.fma(-normal.dot(tangent), tangent);
        if (normal.lengthSquared() <= EPSILON) {
            throw new IllegalArgumentException("Rig ribbon source normal must not be parallel to source tangent");
        }
        normal.normalize();

        Vector3f canonicalBinormal = new Vector3f(normal).cross(tangent).normalize();
        binormalHint.fma(-binormalHint.dot(tangent), tangent)
                .fma(-binormalHint.dot(normal), normal);
        if (binormalHint.lengthSquared() <= EPSILON) {
            throw new IllegalArgumentException("Rig ribbon source binormal must complete a non-degenerate frame");
        }
        binormalHint.normalize();
        float handedness = canonicalBinormal.dot(binormalHint) < 0f ? -1f : 1f;
        canonicalBinormal.mul(handedness);

        this.id = id;
        this.sampleCount = sampleCount;
        this.sourceTangent = tangent;
        this.sourceNormal = normal;
        this.sourceBinormal = canonicalBinormal;
        this.handedness = handedness;
    }

    private static void requireDirection(Vector3f value, String name) {
        if (!Float.isFinite(value.x) || !Float.isFinite(value.y) || !Float.isFinite(value.z) || value.lengthSquared() <= EPSILON) {
            throw new IllegalArgumentException("Rig ribbon " + name + " must be finite and non-zero");
        }
    }

    public int id() { return id; }
    public int sampleCount() { return sampleCount; }
    public Vector3fc sourceTangent() { return sourceTangent; }
    public Vector3fc sourceNormal() { return sourceNormal; }
    public Vector3fc sourceBinormal() { return sourceBinormal; }
    public float handedness() { return handedness; }
}
