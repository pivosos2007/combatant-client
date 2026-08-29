/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.deform;

import combatant.client.render.engine.rig.shader.RigShaderLimits;

/**
 * Dense per-instance dynamic deformation state. Ids are direct array indices so frame-time access
 * is branch-light and allocation-free; no map is used in this hot path.
 */
public final class RigDeformState {
    private final RigDeformDefinition[] definitions = new RigDeformDefinition[RigShaderLimits.MAX_DEFORMS];
    private final float[] bendAngles = new float[RigShaderLimits.MAX_DEFORMS];
    private final float[] twistAngles = new float[RigShaderLimits.MAX_DEFORMS];
    private final float[] bendFalloffs = new float[RigShaderLimits.MAX_DEFORMS];
    private final float[] twistFalloffs = new float[RigShaderLimits.MAX_DEFORMS];
    private final int[] flags = new int[RigShaderLimits.MAX_DEFORMS];

    public RigDeformState define(RigDeformDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("Rig deform definition must not be null");
        definitions[definition.id()] = definition;
        return this;
    }

    public RigDeformState undefine(int id) {
        checkId(id);
        definitions[id] = null;
        bendAngles[id] = 0f;
        twistAngles[id] = 0f;
        bendFalloffs[id] = 0f;
        twistFalloffs[id] = 0f;
        flags[id] = RigDeformFlags.NONE;
        return this;
    }

    public RigDeformState setBend(int id, float angleRadians, float falloff) {
        requireDefinition(id);
        if (!Float.isFinite(angleRadians)) throw new IllegalArgumentException("Rig bend angle must be finite");
        bendAngles[id] = angleRadians;
        bendFalloffs[id] = clamp01(falloff);
        flags[id] |= RigDeformFlags.BEND;
        return this;
    }

    public RigDeformState disableBend(int id) {
        checkId(id);
        flags[id] &= ~RigDeformFlags.BEND;
        bendAngles[id] = 0f;
        return this;
    }

    public RigDeformState setTwist(int id, float angleRadians, float falloff) {
        requireDefinition(id);
        if (!Float.isFinite(angleRadians)) throw new IllegalArgumentException("Rig twist angle must be finite");
        twistAngles[id] = angleRadians;
        twistFalloffs[id] = clamp01(falloff);
        flags[id] |= RigDeformFlags.TWIST;
        return this;
    }

    public RigDeformState disableTwist(int id) {
        checkId(id);
        flags[id] &= ~RigDeformFlags.TWIST;
        twistAngles[id] = 0f;
        return this;
    }

    public RigDeformState clearDynamic(int id) {
        checkId(id);
        bendAngles[id] = 0f;
        twistAngles[id] = 0f;
        bendFalloffs[id] = 0f;
        twistFalloffs[id] = 0f;
        flags[id] = RigDeformFlags.NONE;
        return this;
    }

    public boolean defined(int id) {
        checkId(id);
        return definitions[id] != null;
    }

    public int flags(int id) {
        checkId(id);
        return flags[id];
    }

    public float bendAngle(int id) {
        checkId(id);
        return bendAngles[id];
    }

    public float twistAngle(int id) {
        checkId(id);
        return twistAngles[id];
    }

    public float bendFalloff(int id) {
        checkId(id);
        return bendFalloffs[id];
    }

    public float twistFalloff(int id) {
        checkId(id);
        return twistFalloffs[id];
    }

    public RigDeformDefinition definition(int id) {
        checkId(id);
        return definitions[id];
    }

    private RigDeformDefinition requireDefinition(int id) {
        checkId(id);
        RigDeformDefinition definition = definitions[id];
        if (definition == null) throw new IllegalStateException("Rig deform " + id + " has no definition");
        return definition;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Rig deform falloff must be finite");
        return Math.max(0f, Math.min(1f, value));
    }

    private static void checkId(int id) {
        if (id < 0 || id >= RigShaderLimits.MAX_DEFORMS) {
            throw new IndexOutOfBoundsException("Rig deform id outside [0," + (RigShaderLimits.MAX_DEFORMS - 1) + "]: " + id);
        }
    }
}
