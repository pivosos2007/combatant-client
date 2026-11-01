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

/**
 * Internal zero-copy bridge for Combatant model's hot render paths.
 *
 * <p>The returned arrays are the immutable asset's backing storage. They are exposed only so the
 * renderer can avoid cloning complete vertex streams every frame. Treat every returned array as
 * strictly read-only. Normal integrations should keep using {@link ModelPrimitive}'s defensive-copy
 * accessors.</p>
 */
public final class ModelPrimitiveAccess {
    private ModelPrimitiveAccess() {}

    public static float[] positions(ModelPrimitive primitive) { return primitive.positionsView(); }
    public static float[] normals(ModelPrimitive primitive) { return primitive.normalsView(); }
    public static float[] tangents(ModelPrimitive primitive) { return primitive.tangentsView(); }
    public static float[] texCoords0(ModelPrimitive primitive) { return primitive.texCoords0View(); }
    public static float[] texCoords1(ModelPrimitive primitive) { return primitive.texCoords1View(); }
    public static float[] colors(ModelPrimitive primitive) { return primitive.colorsView(); }
    public static int[] joints(ModelPrimitive primitive) { return primitive.jointsView(); }
    public static float[] weights(ModelPrimitive primitive) { return primitive.weightsView(); }
    public static int[] indices(ModelPrimitive primitive) { return primitive.indicesView(); }
}
