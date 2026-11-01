/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.runtime;

import combatant.client.render.engine.asset.model.data.ModelBounds;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Immutable placement of one mesh primitive inside glTF asset-local scene space. */
public final class ModelPrimitivePlacement {
    private final int nodeIndex;
    private final int meshIndex;
    private final int primitiveIndex;
    private final Matrix4f localToAsset;
    private final ModelBounds assetLocalBounds;
    private final boolean mirroredWinding;

    ModelPrimitivePlacement(int nodeIndex, int meshIndex, int primitiveIndex,
                           Matrix4fc localToAsset, ModelBounds assetLocalBounds) {
        this.nodeIndex = nodeIndex;
        this.meshIndex = meshIndex;
        this.primitiveIndex = primitiveIndex;
        this.localToAsset = new Matrix4f(localToAsset);
        this.assetLocalBounds = assetLocalBounds;
        this.mirroredWinding = determinant3x3(localToAsset) < 0.0;
    }

    public int nodeIndex() { return nodeIndex; }
    public int meshIndex() { return meshIndex; }
    public int primitiveIndex() { return primitiveIndex; }
    public Matrix4f localToAsset() { return new Matrix4f(localToAsset); }
    public ModelBounds assetLocalBounds() { return assetLocalBounds; }
    public boolean mirroredWinding() { return mirroredWinding; }

    private static double determinant3x3(Matrix4fc m) {
        return (double) m.m00() * ((double) m.m11() * m.m22() - (double) m.m21() * m.m12())
                - (double) m.m10() * ((double) m.m01() * m.m22() - (double) m.m21() * m.m02())
                + (double) m.m20() * ((double) m.m01() * m.m12() - (double) m.m11() * m.m02());
    }
}
