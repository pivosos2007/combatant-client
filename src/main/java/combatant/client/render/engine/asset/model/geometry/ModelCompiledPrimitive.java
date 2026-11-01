/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.geometry;

import combatant.client.render.engine.asset.model.data.ModelBounds;

/**
 * Backend-neutral, renderer-ready surface primitive. Arrays are defensive copies at the API
 * boundary; persistent GL/Vulkan residency is owned by the Combatant RHI layer, not this object.
 */
public record ModelCompiledPrimitive(
        int material,
        int vertexCount,
        int[] indices,
        float[] positions,
        float[] normals,
        float[] tangents,
        float[] texCoords0,
        float[] texCoords1,
        float[] colors,
        int[] joints,
        float[] weights,
        ModelBounds bounds,
        boolean generatedNormals,
        boolean generatedTangents
) {
    public ModelCompiledPrimitive {
        indices = indices.clone();
        positions = positions.clone();
        normals = normals.clone();
        tangents = tangents == null ? null : tangents.clone();
        texCoords0 = texCoords0 == null ? null : texCoords0.clone();
        texCoords1 = texCoords1 == null ? null : texCoords1.clone();
        colors = colors == null ? null : colors.clone();
        joints = joints == null ? null : joints.clone();
        weights = weights == null ? null : weights.clone();
    }

    @Override public int[] indices() { return indices.clone(); }
    @Override public float[] positions() { return positions.clone(); }
    @Override public float[] normals() { return normals.clone(); }
    @Override public float[] tangents() { return tangents == null ? null : tangents.clone(); }
    @Override public float[] texCoords0() { return texCoords0 == null ? null : texCoords0.clone(); }
    @Override public float[] texCoords1() { return texCoords1 == null ? null : texCoords1.clone(); }
    @Override public float[] colors() { return colors == null ? null : colors.clone(); }
    @Override public int[] joints() { return joints == null ? null : joints.clone(); }
    @Override public float[] weights() { return weights == null ? null : weights.clone(); }

    public int triangleCount() { return indices.length / 3; }
    public boolean skinned() { return joints != null && weights != null; }
}
