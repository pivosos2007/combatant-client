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

import java.util.Arrays;
import java.util.Objects;

public final class ModelNode {
    private final String name;
    private final int parent;
    private final int[] children;
    private final int[] meshes;
    private final int skin;
    private final float[] matrix, translation, rotation, scale, morphWeights;

    public ModelNode(String name, int parent, int[] children, int[] meshes, int skin,
                    float[] matrix, float[] translation, float[] rotation,
                    float[] scale, float[] morphWeights) {
        this.name = name == null ? "" : name;
        if (parent < -1) throw new IllegalArgumentException("Invalid parent node index");
        if (skin < -1) throw new IllegalArgumentException("Invalid skin index");
        this.parent = parent;
        this.children = Objects.requireNonNull(children, "children").clone();
        this.meshes = Objects.requireNonNull(meshes, "meshes").clone();
        this.skin = skin;
        if (matrix != null && (translation != null || rotation != null || scale != null)) {
            throw new IllegalArgumentException("A model node cannot define both matrix and TRS transforms");
        }
        this.matrix = copy(matrix, 16, null, "matrix");
        this.translation = copy(translation, 3, new float[]{0, 0, 0}, "translation");
        this.rotation = copy(rotation, 4, new float[]{0, 0, 0, 1}, "rotation");
        normalizeQuaternion(this.rotation);
        this.scale = copy(scale, 3, new float[]{1, 1, 1}, "scale");
        this.morphWeights = morphWeights == null ? null : morphWeights.clone();
        requireFinite(this.morphWeights, "morph weights");
    }

    private static float[] copy(float[] value, int length, float[] fallback, String label) {
        if (value == null) return fallback == null ? null : fallback.clone();
        if (value.length != length) throw new IllegalArgumentException("Expected " + length + " values for " + label);
        float[] result = Arrays.copyOf(value, length);
        requireFinite(result, label);
        return result;
    }

    private static void requireFinite(float[] values, String label) {
        if (values == null) return;
        for (float value : values) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException(label + " contains a non-finite value");
        }
    }

    private static void normalizeQuaternion(float[] quaternion) {
        double lengthSquared = 0.0;
        for (float value : quaternion) lengthSquared += (double) value * value;
        if (lengthSquared < 1.0e-20) {
            quaternion[0] = quaternion[1] = quaternion[2] = 0.0f;
            quaternion[3] = 1.0f;
            return;
        }
        float inverseLength = (float) (1.0 / Math.sqrt(lengthSquared));
        for (int i = 0; i < 4; i++) quaternion[i] *= inverseLength;
    }

    public String name() { return name; }
    public int parent() { return parent; }
    public int[] children() { return children.clone(); }
    public int[] meshes() { return meshes.clone(); }
    public int skin() { return skin; }
    public float[] matrix() { return matrix == null ? null : matrix.clone(); }
    public boolean hasMatrix() { return matrix != null; }
    public void copyMatrixTo(float[] target) {
        if (matrix == null) throw new IllegalStateException("Node has no fixed matrix");
        copyTo(matrix, target, 16, "matrix");
    }
    public float[] translation() { return translation.clone(); }
    public float[] rotation() { return rotation.clone(); }
    public float[] scale() { return scale.clone(); }
    public float[] morphWeights() { return morphWeights == null ? null : morphWeights.clone(); }
    public int morphWeightCount() { return morphWeights == null ? 0 : morphWeights.length; }

    public void copyTranslationTo(float[] target) { copyTo(translation, target, 3, "translation"); }
    public void copyRotationTo(float[] target) { copyTo(rotation, target, 4, "rotation"); }
    public void copyScaleTo(float[] target) { copyTo(scale, target, 3, "scale"); }
    public void copyMorphWeightsTo(float[] target) {
        if (morphWeights == null) {
            if (target.length != 0) throw new IllegalArgumentException("Node has no morph weights");
            return;
        }
        copyTo(morphWeights, target, morphWeights.length, "morph weights");
    }

    private static void copyTo(float[] source, float[] target, int length, String label) {
        Objects.requireNonNull(target, "target");
        if (target.length != length) throw new IllegalArgumentException("Wrong target length for " + label);
        System.arraycopy(source, 0, target, 0, length);
    }
}
