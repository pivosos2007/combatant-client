/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.mesh;

import combatant.client.render.engine.rig.shader.RigShaderLimits;

/** Shared CPU/shader packing rules for the rig vertex format. */
public final class RigVertexEncoding {
    public static final int MAX_VERTEX_BONE_INDEX = Math.min(0xFF, RigShaderLimits.MAX_BONES - 1);
    public static final int MAX_DEFORM_ID = Math.min(0xFFFE, RigShaderLimits.MAX_DEFORMS - 1);
    public static final int MAX_DEFORM_FLAGS = 0xFFFF;

    private static final int DEFORM_ID_BITS = 16;
    private static final int DEFORM_ID_MASK = 0xFFFF;

    private RigVertexEncoding() {
    }

    /**
     * RGBA8_UINT bone slots cannot carry -1. Unused zero-weight slots are encoded as bone zero.
     */
    public static int encodeBoneIndex(int boneIndex) {
        if (boneIndex == RigVertex.UNUSED_BONE) return 0;
        if (boneIndex < 0 || boneIndex > MAX_VERTEX_BONE_INDEX) {
            throw new IllegalArgumentException("Rig vertex bone index outside current shader palette [0," + MAX_VERTEX_BONE_INDEX + "]: " + boneIndex);
        }
        return boneIndex;
    }

    /**
     * Low 16 bits: deform id + 1 (zero means no deformer). High 16 bits: per-vertex deform flags.
     */
    public static int packDeformMeta(int deformId, int deformFlags) {
        if (deformId < -1 || deformId > MAX_DEFORM_ID) {
            throw new IllegalArgumentException("Rig deform id outside [-1," + MAX_DEFORM_ID + "]: " + deformId);
        }
        if ((deformFlags & ~MAX_DEFORM_FLAGS) != 0) {
            throw new IllegalArgumentException("Rig deform flags exceed 16 bits: 0x" + Integer.toHexString(deformFlags));
        }
        int encodedId = deformId < 0 ? 0 : deformId + 1;
        return (deformFlags << DEFORM_ID_BITS) | (encodedId & DEFORM_ID_MASK);
    }

    public static int unpackDeformId(int packedMeta) {
        int encoded = packedMeta & DEFORM_ID_MASK;
        return encoded == 0 ? -1 : encoded - 1;
    }

    public static int unpackDeformFlags(int packedMeta) {
        return packedMeta >>> DEFORM_ID_BITS;
    }
}
