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

public final class ModelMaterial {
    private final String name;
    private final float[] baseColor;
    private final ModelTextureInfo baseColorTexture;
    private final float metallicFactor, roughnessFactor;
    private final ModelTextureInfo metallicRoughnessTexture;
    private final ModelTextureInfo normalTexture;
    private final ModelTextureInfo occlusionTexture;
    private final float normalScale, occlusionStrength;
    private final float[] emissive;
    private final ModelTextureInfo emissiveTexture;
    private final float emissiveStrength;
    private final AlphaMode alphaMode;
    private final float alphaCutoff;
    private final boolean doubleSided;
    private final boolean unlit;

    public ModelMaterial(String name, float[] baseColor, int baseColorTexture,
                        int baseColorTexCoord, float[] emissive, int emissiveTexture,
                        int emissiveTexCoord, AlphaMode alphaMode,
                        float alphaCutoff, boolean doubleSided) {
        this(
            name,
            baseColor,
            new ModelTextureInfo(baseColorTexture, baseColorTexCoord),
            1.0f,
            1.0f,
            ModelTextureInfo.absent(),
            ModelTextureInfo.absent(),
            1.0f,
            ModelTextureInfo.absent(),
            1.0f,
            emissive,
            new ModelTextureInfo(emissiveTexture, emissiveTexCoord),
            1.0f,
            alphaMode,
            alphaCutoff,
            doubleSided,
            false
        );
    }

    public ModelMaterial(
        String name,
        float[] baseColor,
        int baseColorTexture,
        int baseColorTexCoord,
        float metallicFactor,
        float roughnessFactor,
        int metallicRoughnessTexture,
        int metallicRoughnessTexCoord,
        int normalTexture,
        int normalTexCoord,
        float normalScale,
        int occlusionTexture,
        int occlusionTexCoord,
        float occlusionStrength,
        float[] emissive,
        int emissiveTexture,
        int emissiveTexCoord,
        AlphaMode alphaMode,
        float alphaCutoff,
        boolean doubleSided
    ) {
        this.name = name == null ? "" : name;
        this.baseColor = copy(baseColor, 4, new float[]{1, 1, 1, 1}, "base color");
        requireRange(this.baseColor, 0.0f, 1.0f, "base color");
        this.baseColorTexture = new ModelTextureInfo(baseColorTexture, baseColorTexCoord);
        this.metallicFactor = unit(metallicFactor, "metallic factor");
        this.roughnessFactor = unit(roughnessFactor, "roughness factor");
        this.metallicRoughnessTexture =
            new ModelTextureInfo(texture(metallicRoughnessTexture, "metallic-roughness"),
                metallicRoughnessTexCoord);
        this.normalTexture = new ModelTextureInfo(texture(normalTexture, "normal"), normalTexCoord);
        this.normalScale = finite(normalScale, "normal scale");
        this.occlusionTexture =
            new ModelTextureInfo(texture(occlusionTexture, "occlusion"), occlusionTexCoord);
        this.occlusionStrength = unit(occlusionStrength, "occlusion strength");
        this.emissive = copy(emissive, 3, new float[]{0, 0, 0}, "emissive factor");
        requireRange(this.emissive, 0.0f, 1.0f, "emissive factor");
        this.emissiveTexture = new ModelTextureInfo(emissiveTexture, emissiveTexCoord);
        this.emissiveStrength = 1.0f;
        this.alphaMode = alphaMode == null ? AlphaMode.OPAQUE : alphaMode;
        if (!Float.isFinite(alphaCutoff) || alphaCutoff < 0.0f || alphaCutoff > 1.0f) {
            throw new IllegalArgumentException("Alpha cutoff must be between 0 and 1");
        }
        this.alphaCutoff = alphaCutoff;
        this.doubleSided = doubleSided;
        this.unlit = false;
    }

    public ModelMaterial(
        String name,
        float[] baseColor,
        ModelTextureInfo baseColorTexture,
        float metallicFactor,
        float roughnessFactor,
        ModelTextureInfo metallicRoughnessTexture,
        ModelTextureInfo normalTexture,
        float normalScale,
        ModelTextureInfo occlusionTexture,
        float occlusionStrength,
        float[] emissive,
        ModelTextureInfo emissiveTexture,
        float emissiveStrength,
        AlphaMode alphaMode,
        float alphaCutoff,
        boolean doubleSided,
        boolean unlit
    ) {
        this.name = name == null ? "" : name;
        this.baseColor = copy(baseColor, 4, new float[]{1, 1, 1, 1}, "base color");
        requireRange(this.baseColor, 0.0f, 1.0f, "base color");
        this.baseColorTexture = requireTextureInfo(baseColorTexture);
        this.metallicFactor = unit(metallicFactor, "metallic factor");
        this.roughnessFactor = unit(roughnessFactor, "roughness factor");
        this.metallicRoughnessTexture = requireTextureInfo(metallicRoughnessTexture);
        this.normalTexture = requireTextureInfo(normalTexture);
        this.normalScale = finite(normalScale, "normal scale");
        this.occlusionTexture = requireTextureInfo(occlusionTexture);
        this.occlusionStrength = unit(occlusionStrength, "occlusion strength");
        this.emissive = copy(emissive, 3, new float[]{0, 0, 0}, "emissive factor");
        requireRange(this.emissive, 0.0f, 1.0f, "emissive factor");
        this.emissiveTexture = requireTextureInfo(emissiveTexture);
        if (!Float.isFinite(emissiveStrength) || emissiveStrength < 0.0f) {
            throw new IllegalArgumentException("Emissive strength must be finite and non-negative");
        }
        this.emissiveStrength = emissiveStrength;
        this.alphaMode = alphaMode == null ? AlphaMode.OPAQUE : alphaMode;
        if (!Float.isFinite(alphaCutoff) || alphaCutoff < 0.0f || alphaCutoff > 1.0f) {
            throw new IllegalArgumentException("Alpha cutoff must be between 0 and 1");
        }
        this.alphaCutoff = alphaCutoff;
        this.doubleSided = doubleSided;
        this.unlit = unlit;
    }

    private static int texture(int value, String label) {
        if (value < -1) throw new IllegalArgumentException("Invalid " + label + " texture index");
        return value;
    }

    private static float unit(float value, String label) {
        if (!Float.isFinite(value) || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(label + " must be between 0 and 1");
        }
        return value;
    }

    private static float finite(float value, String label) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(label + " must be finite");
        return value;
    }

    private static float[] copy(float[] input, int length, float[] fallback, String label) {
        float[] result;
        if (input == null) result = fallback.clone();
        else {
            if (input.length != length) throw new IllegalArgumentException("Expected " + length + " values for " + label);
            result = Arrays.copyOf(input, length);
        }
        for (float value : result) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException(label + " contains a non-finite value");
        }
        return result;
    }

    private static void requireRange(float[] values, float minimum, float maximum, String label) {
        for (float value : values) {
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(label + " values must be between " + minimum + " and " + maximum);
            }
        }
    }

    private static ModelTextureInfo requireTextureInfo(ModelTextureInfo info) {
        if (info == null) return ModelTextureInfo.absent();
        return info;
    }

    public static ModelMaterial defaultMaterial() {
        return new ModelMaterial("default", null, -1, 0, null, -1, 0,
            AlphaMode.OPAQUE, 0.5f, false);
    }

    public String name() { return name; }
    public float[] baseColor() { return baseColor.clone(); }
    /** Allocation-free scalar accessors for render hot paths. */
    public float baseColorRed() { return baseColor[0]; }
    public float baseColorGreen() { return baseColor[1]; }
    public float baseColorBlue() { return baseColor[2]; }
    public float baseColorAlpha() { return baseColor[3]; }
    public int baseColorTexture() { return baseColorTexture.texture(); }
    public int baseColorTexCoord() { return baseColorTexture.texCoord(); }
    public ModelTextureInfo baseColorTextureInfo() { return baseColorTexture; }
    public float metallicFactor() { return metallicFactor; }
    public float roughnessFactor() { return roughnessFactor; }
    public int metallicRoughnessTexture() { return metallicRoughnessTexture.texture(); }
    public int metallicRoughnessTexCoord() { return metallicRoughnessTexture.texCoord(); }
    public ModelTextureInfo metallicRoughnessTextureInfo() { return metallicRoughnessTexture; }
    public int normalTexture() { return normalTexture.texture(); }
    public int normalTexCoord() { return normalTexture.texCoord(); }
    public ModelTextureInfo normalTextureInfo() { return normalTexture; }
    public float normalScale() { return normalScale; }
    public int occlusionTexture() { return occlusionTexture.texture(); }
    public int occlusionTexCoord() { return occlusionTexture.texCoord(); }
    public ModelTextureInfo occlusionTextureInfo() { return occlusionTexture; }
    public float occlusionStrength() { return occlusionStrength; }
    public float[] emissive() { return emissive.clone(); }
    /** Allocation-free scalar accessors for render hot paths. */
    public float emissiveRed() { return emissive[0]; }
    public float emissiveGreen() { return emissive[1]; }
    public float emissiveBlue() { return emissive[2]; }
    public int emissiveTexture() { return emissiveTexture.texture(); }
    public int emissiveTexCoord() { return emissiveTexture.texCoord(); }
    public ModelTextureInfo emissiveTextureInfo() { return emissiveTexture; }
    public float emissiveStrength() { return emissiveStrength; }
    public AlphaMode alphaMode() { return alphaMode; }
    public float alphaCutoff() { return alphaCutoff; }
    public boolean doubleSided() { return doubleSided; }
    public boolean unlit() { return unlit; }
}
