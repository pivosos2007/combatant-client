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

import java.util.List;
import java.util.Objects;

public final class ModelPrimitive {
    public static final int MAX_MORPH_TARGETS = 128;
    private final PrimitiveMode mode;
    private final int material;
    private final int vertexCount;
    private final float[] positions, normals, tangents, texCoords0, texCoords1, colors, weights;
    private final int[] joints, indices;
    private final List<MorphTarget> morphTargets;
    private final ModelBounds bounds;

    public ModelPrimitive(PrimitiveMode mode, int material, int vertexCount,
                         float[] positions, float[] normals, float[] tangents,
                         float[] texCoords0, float[] texCoords1, float[] colors,
                         int[] joints, float[] weights, int[] indices,
                         List<MorphTarget> morphTargets) {
        this.mode = Objects.requireNonNull(mode, "mode");
        if (material < 0) throw new IllegalArgumentException("Material index must be non-negative");
        if (vertexCount < 0) throw new IllegalArgumentException("Vertex count must be non-negative");
        this.material = material;
        this.vertexCount = vertexCount;
        int vec2Count = checkedComponents(vertexCount, 2, "VEC2 attribute");
        int vec3Count = checkedComponents(vertexCount, 3, "VEC3 attribute");
        int vec4Count = checkedComponents(vertexCount, 4, "VEC4 attribute");
        this.positions = copyRequired(positions, vec3Count, "POSITION");
        this.normals = copyOptional(normals, vec3Count, "NORMAL");
        this.tangents = copyOptional(tangents, vec4Count, "TANGENT");
        this.texCoords0 = copyOptional(texCoords0, vec2Count, "TEXCOORD_0");
        this.texCoords1 = copyOptional(texCoords1, vec2Count, "TEXCOORD_1");
        this.colors = copyColors(colors, vertexCount);
        this.joints = copyOptional(joints, vec4Count, "JOINTS_0");
        this.weights = copyOptional(weights, vec4Count, "WEIGHTS_0");
        if ((this.joints == null) != (this.weights == null)) {
            throw new IllegalArgumentException("JOINTS_0 and WEIGHTS_0 must either both be present or both be absent");
        }
        if (this.joints != null) {
            for (int joint : this.joints) {
                if (joint < 0) throw new IllegalArgumentException("Joint indices must be non-negative");
            }
        }
        this.indices = indices == null ? null : indices.clone();
        if (this.indices != null) {
            for (int index : this.indices) {
                if (index < 0 || index >= vertexCount) {
                    throw new IllegalArgumentException("Index " + index + " is outside vertex range 0.." + Math.max(0, vertexCount - 1));
                }
            }
        }
        validateTopology(mode, this.indices == null ? vertexCount : this.indices.length);
        this.morphTargets = List.copyOf(Objects.requireNonNull(morphTargets, "morphTargets"));
        if (this.morphTargets.size() > MAX_MORPH_TARGETS) {
            throw new IllegalArgumentException("A primitive may contain at most " + MAX_MORPH_TARGETS + " morph targets");
        }
        validateMorphTargets(this.morphTargets, vertexCount, this.normals != null, this.tangents != null);
        this.bounds = ModelBounds.ofPositions(this.positions);
    }


    private static void validateTopology(PrimitiveMode mode, int elementCount) {
        boolean valid = switch (mode) {
            case POINTS -> elementCount >= 1;
            case LINES -> elementCount >= 2 && elementCount % 2 == 0;
            case LINE_LOOP, LINE_STRIP -> elementCount >= 2;
            case TRIANGLES -> elementCount >= 3 && elementCount % 3 == 0;
            case TRIANGLE_STRIP, TRIANGLE_FAN -> elementCount >= 3;
        };
        if (!valid) {
            throw new IllegalArgumentException("Element count " + elementCount + " is invalid for primitive mode " + mode);
        }
    }

    private static int checkedComponents(int vertices, int components, String label) {
        try {
            return Math.multiplyExact(vertices, components);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(label + " is too large", overflow);
        }
    }

    private static float[] copyRequired(float[] values, int expected, String label) {
        if (values == null) throw new IllegalArgumentException(label + " is required");
        return copyOptional(values, expected, label);
    }

    private static float[] copyOptional(float[] values, int expected, String label) {
        if (values == null) return null;
        if (values.length != expected) throw new IllegalArgumentException(label + " has " + values.length + " values, expected " + expected);
        float[] result = values.clone();
        for (float value : result) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException(label + " contains a non-finite value");
        }
        return result;
    }

    private static int[] copyOptional(int[] values, int expected, String label) {
        if (values == null) return null;
        if (values.length != expected) throw new IllegalArgumentException(label + " has " + values.length + " values, expected " + expected);
        return values.clone();
    }

    private static float[] copyColors(float[] values, int vertexCount) {
        if (values == null) return null;
        int expected3 = checkedComponents(vertexCount, 3, "COLOR_0");
        int expected4 = checkedComponents(vertexCount, 4, "COLOR_0");
        if (values.length != expected3 && values.length != expected4) {
            throw new IllegalArgumentException("COLOR_0 must contain RGB or RGBA values for every vertex");
        }
        float[] result = values.clone();
        for (float value : result) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("COLOR_0 contains a non-finite value");
        }
        return result;
    }

    private static void validateMorphTargets(List<MorphTarget> targets, int vertexCount,
                                             boolean hasNormals, boolean hasTangents) {
        int vec3Count = checkedComponents(vertexCount, 3, "morph target");
        for (int i = 0; i < targets.size(); i++) {
            MorphTarget target = Objects.requireNonNull(targets.get(i), "morph target");
            requireLength(target.positions(), vec3Count, "morph POSITION", i);
            requireLength(target.normals(), vec3Count, "morph NORMAL", i);
            requireLength(target.tangents(), vec3Count, "morph TANGENT", i);
            if (target.normals() != null && !hasNormals) {
                throw new IllegalArgumentException("Morph NORMAL target " + i + " requires a base NORMAL attribute");
            }
            if (target.tangents() != null && !hasTangents) {
                throw new IllegalArgumentException("Morph TANGENT target " + i + " requires a base TANGENT attribute");
            }
        }
    }

    private static void requireLength(float[] values, int expected, String label, int target) {
        if (values != null && values.length != expected) {
            throw new IllegalArgumentException(label + " target " + target + " has " + values.length + " values, expected " + expected);
        }
    }

    private static float[] clone(float[] value) { return value == null ? null : value.clone(); }
    public PrimitiveMode mode() { return mode; }
    public int material() { return material; }
    public int vertexCount() { return vertexCount; }
    public float[] positions() { return clone(positions); }
    public float[] normals() { return clone(normals); }
    public float[] tangents() { return clone(tangents); }
    public float[] texCoords0() { return clone(texCoords0); }
    public float[] texCoords1() { return clone(texCoords1); }
    public float[] colors() { return clone(colors); }
    public int[] joints() { return joints == null ? null : joints.clone(); }
    public float[] weights() { return clone(weights); }
    public int[] indices() { return indices == null ? null : indices.clone(); }
    public int indexCount() { return indices == null ? vertexCount : indices.length; }
    public List<MorphTarget> morphTargets() { return morphTargets; }
    public ModelBounds bounds() { return bounds; }
    public boolean hasDynamicGeometry() {
        return joints != null || !morphTargets.isEmpty();
    }

    float[] positionsView() { return positions; }
    float[] normalsView() { return normals; }
    float[] tangentsView() { return tangents; }
    float[] texCoords0View() { return texCoords0; }
    float[] texCoords1View() { return texCoords1; }
    float[] colorsView() { return colors; }
    int[] jointsView() { return joints; }
    float[] weightsView() { return weights; }
    int[] indicesView() { return indices; }
}
