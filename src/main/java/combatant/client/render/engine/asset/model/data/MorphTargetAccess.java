/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are adapted from GFBS-glTF 1.5.1
 * (commit 8906d4083c23b5d5bbf5191a0555d149a5272900).
 * Copyright (c) 2026 LytharaLab. Original portions are licensed under the MIT License.
 * See THIRD_PARTY_LICENSES/GFBS-glTF-MIT.txt.
 *
 * Combatant modifications are distributed under GNU GPL v3.0 as part of Combatant.
 */
package combatant.client.render.engine.asset.model.data;

/** Internal read-only zero-copy access for realtime morph evaluation. */
public final class MorphTargetAccess {
    private MorphTargetAccess() {}

    public static float[] positions(MorphTarget target) { return target.positionsView(); }
    public static float[] normals(MorphTarget target) { return target.normalsView(); }
    public static float[] tangents(MorphTarget target) { return target.tangentsView(); }
}
