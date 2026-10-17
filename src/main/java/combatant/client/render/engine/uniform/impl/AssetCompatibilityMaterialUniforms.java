/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.uniform.impl;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import combatant.client.render.engine.asset.gltf.material.GltfMaterialBinding;
import combatant.client.render.engine.asset.gltf.model.GltfTextureInfo;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;

/** Std140 material payload for the shaderpack-off/dev imported-asset compatibility renderer. */
public final class AssetCompatibilityMaterialUniforms {
    public static final int BASE_COLOR = 1 << 0;
    public static final int METALLIC_ROUGHNESS = 1 << 1;
    public static final int NORMAL = 1 << 2;
    public static final int OCCLUSION = 1 << 3;
    public static final int EMISSIVE = 1 << 4;

    public static final int SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4().putVec4().putVec4().putVec4().putVec4()
            .putVec4().putVec4().putVec4().putVec4().putVec4()
            .get();

    private static final String UNIFORM_NAME = "Combatant - AssetCompatibilityMaterial UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 256;

    private AssetCompatibilityMaterialUniforms() {}

    public static GpuBufferSlice write(GltfMaterialBinding material) {
        if (material == null) throw new IllegalArgumentException("material");
        float[] base = material.baseColorFactor();
        float[] emissive = material.emissiveFactor();
        int presence = 0;
        int uv1Mask = 0;
        presence |= presence(material.baseColorTexture(), BASE_COLOR);
        presence |= presence(material.metallicRoughnessTexture(), METALLIC_ROUGHNESS);
        presence |= presence(material.normalTexture(), NORMAL);
        presence |= presence(material.occlusionTexture(), OCCLUSION);
        presence |= presence(material.emissiveTexture(), EMISSIVE);
        uv1Mask |= uv1(material.baseColorTexture(), BASE_COLOR);
        uv1Mask |= uv1(material.metallicRoughnessTexture(), METALLIC_ROUGHNESS);
        uv1Mask |= uv1(material.normalTexture(), NORMAL);
        uv1Mask |= uv1(material.occlusionTexture(), OCCLUSION);
        uv1Mask |= uv1(material.emissiveTexture(), EMISSIVE);

        float alphaMode = switch (material.alphaMode()) {
            case OPAQUE -> 0.0f;
            case MASK -> 1.0f;
            case BLEND -> 2.0f;
        };
        Data data = new Data(
                base,
                new float[]{emissive[0] * material.emissiveStrength(),
                        emissive[1] * material.emissiveStrength(),
                        emissive[2] * material.emissiveStrength(), material.normalScale()},
                new float[]{material.occlusionStrength(), material.surface().roughness(),
                        material.surface().metallic(), material.alphaCutoff()},
                new float[]{presence, uv1Mask, alphaMode, material.unlit() ? 1.0f : 0.0f},
                material.baseColorTexture(),
                material.metallicRoughnessTexture(),
                material.normalTexture(),
                material.occlusionTexture(),
                material.emissiveTexture()
        );
        return CombatantRenderSystem.uniforms().write(
                UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, data);
    }

    private static int presence(GltfTextureInfo info, int bit) {
        return info != null && info.present() ? bit : 0;
    }

    private static int uv1(GltfTextureInfo info, int bit) {
        return info != null && info.present() && info.texCoord() == 1 ? bit : 0;
    }

    private record Data(float[] baseColor,
                        float[] emissiveNormal,
                        float[] surfaceAlpha,
                        float[] flags,
                        GltfTextureInfo baseColorTexture,
                        GltfTextureInfo metallicRoughnessTexture,
                        GltfTextureInfo normalTexture,
                        GltfTextureInfo occlusionTexture,
                        GltfTextureInfo emissiveTexture) implements CombatantUniformAllocator.UniformWriter {
        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder out = Std140Builder.intoBuffer(buffer);
            put4(out, baseColor);
            put4(out, emissiveNormal);
            put4(out, surfaceAlpha);
            put4(out, flags);

            putTransformX(out, baseColorTexture);
            putTransformX(out, metallicRoughnessTexture);
            putTransformX(out, normalTexture);
            putTransformX(out, occlusionTexture);
            putTransformX(out, emissiveTexture);
            putTransformY(out, baseColorTexture);
            putTransformY(out, metallicRoughnessTexture);
            putTransformY(out, normalTexture);
            putTransformY(out, occlusionTexture);
            putTransformY(out, emissiveTexture);
        }

        private static void putTransformX(Std140Builder out, GltfTextureInfo info) {
            GltfTextureInfo resolved = info == null ? GltfTextureInfo.absent() : info;
            float cosine = (float) Math.cos(resolved.rotation());
            float sine = (float) Math.sin(resolved.rotation());
            out.putFloat(cosine * resolved.scaleU())
                    .putFloat(-sine * resolved.scaleV())
                    .putFloat(resolved.offsetU())
                    .putFloat(0.0f);
        }

        private static void putTransformY(Std140Builder out, GltfTextureInfo info) {
            GltfTextureInfo resolved = info == null ? GltfTextureInfo.absent() : info;
            float cosine = (float) Math.cos(resolved.rotation());
            float sine = (float) Math.sin(resolved.rotation());
            out.putFloat(sine * resolved.scaleU())
                    .putFloat(cosine * resolved.scaleV())
                    .putFloat(resolved.offsetV())
                    .putFloat(0.0f);
        }

        private static void put4(Std140Builder out, float[] value) {
            out.putFloat(value[0]).putFloat(value[1]).putFloat(value[2]).putFloat(value[3]);
        }
    }
}
