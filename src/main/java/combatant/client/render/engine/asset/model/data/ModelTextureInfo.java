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
 * Immutable model texture reference, including {@code KHR_texture_transform}.
 *
 * <p>The transform follows the glTF extension order: scale, rotate, then translate.</p>
 */
public record ModelTextureInfo(
    int texture,
    int texCoord,
    float offsetU,
    float offsetV,
    float scaleU,
    float scaleV,
    float rotation
) {
    public ModelTextureInfo {
        if (texture < -1) throw new IllegalArgumentException("Invalid texture index");
        if (texCoord < 0 || texCoord > 1) {
            throw new IllegalArgumentException("Combatant model supports TEXCOORD_0 and TEXCOORD_1");
        }
        if (!Float.isFinite(offsetU) || !Float.isFinite(offsetV)
            || !Float.isFinite(scaleU) || !Float.isFinite(scaleV)
            || !Float.isFinite(rotation)) {
            throw new IllegalArgumentException("Texture transform contains a non-finite value");
        }
    }

    public ModelTextureInfo(int texture, int texCoord) {
        this(texture, texCoord, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f);
    }

    public static ModelTextureInfo absent() {
        return new ModelTextureInfo(-1, 0);
    }

    public boolean present() {
        return texture >= 0;
    }

    public boolean transformed() {
        return offsetU != 0.0f || offsetV != 0.0f
            || scaleU != 1.0f || scaleV != 1.0f || rotation != 0.0f;
    }

    /**
     * Applies this texture transform and writes the result into {@code output[0..1]}.
     */
    public void transform(float u, float v, float[] output) {
        if (output == null || output.length < 2) {
            throw new IllegalArgumentException("Texture-coordinate output must contain two values");
        }
        float scaledU = u * scaleU;
        float scaledV = v * scaleV;
        float cosine = (float) Math.cos(rotation);
        float sine = (float) Math.sin(rotation);
        output[0] = offsetU + cosine * scaledU - sine * scaledV;
        output[1] = offsetV + sine * scaledU + cosine * scaledV;
    }
}
