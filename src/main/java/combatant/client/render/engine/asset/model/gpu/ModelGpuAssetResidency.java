/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.gpu;

import combatant.client.render.engine.asset.model.ModelRuntimeAsset;
import combatant.client.render.engine.asset.model.material.ModelMaterialBinding;
import combatant.client.render.engine.asset.model.data.ModelMesh;
import combatant.client.render.engine.asset.model.data.ModelTextureInfo;
import combatant.client.render.engine.rhi.CombatantRhi;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persistent GPU residency for immutable geometry and imported images of one asset.
 * Mesh/image data is shared by every world instance of the asset.
 */
public final class ModelGpuAssetResidency implements AutoCloseable {
    private final CombatantRhi owner;
    private final ModelRuntimeAsset asset;
    private final List<List<ModelGpuPrimitive>> meshes;
    private final List<Map<ModelGpuTextureUsage, ModelGpuTexture>> textures;
    private final ModelGpuTexture fallbackWhiteColor;
    private final ModelGpuTexture fallbackWhiteData;
    private final ModelGpuTexture fallbackNormal;
    private final ModelGpuTexture fallbackBlackColor;
    private boolean closed;

    private ModelGpuAssetResidency(CombatantRhi owner,
                                  ModelRuntimeAsset asset,
                                  List<List<ModelGpuPrimitive>> meshes,
                                  List<Map<ModelGpuTextureUsage, ModelGpuTexture>> textures,
                                  ModelGpuTexture fallbackWhiteColor,
                                  ModelGpuTexture fallbackWhiteData,
                                  ModelGpuTexture fallbackNormal,
                                  ModelGpuTexture fallbackBlackColor) {
        this.owner = owner;
        this.asset = asset;
        this.meshes = List.copyOf(meshes);
        this.textures = List.copyOf(textures);
        this.fallbackWhiteColor = fallbackWhiteColor;
        this.fallbackWhiteData = fallbackWhiteData;
        this.fallbackNormal = fallbackNormal;
        this.fallbackBlackColor = fallbackBlackColor;
    }

    public static ModelGpuAssetResidency upload(CombatantRhi rhi, ModelRuntimeAsset asset) {
        Objects.requireNonNull(rhi, "rhi");
        Objects.requireNonNull(asset, "asset");
        ArrayList<List<ModelGpuPrimitive>> uploadedMeshes = new ArrayList<>(asset.asset().meshes().size());
        ArrayList<ModelGpuPrimitive> primitiveRollback = new ArrayList<>();
        ArrayList<ModelGpuTexture> textureRollback = new ArrayList<>();
        try {
            for (int meshIndex = 0; meshIndex < asset.asset().meshes().size(); meshIndex++) {
                ModelMesh mesh = asset.asset().meshes().get(meshIndex);
                ArrayList<ModelGpuPrimitive> primitives = new ArrayList<>(mesh.primitives().size());
                for (int primitiveIndex = 0; primitiveIndex < mesh.primitives().size(); primitiveIndex++) {
                    String label = "model:" + asset.asset().id() + "/mesh_" + meshIndex + "/primitive_" + primitiveIndex;
                    ModelGpuPrimitive primitive = ModelGpuPrimitiveCompiler.upload(
                            rhi, label, asset.compilePrimitive(meshIndex, primitiveIndex));
                    primitives.add(primitive);
                    primitiveRollback.add(primitive);
                }
                uploadedMeshes.add(List.copyOf(primitives));
            }

            boolean[][] required = requiredTextureUsages(asset);
            ArrayList<Map<ModelGpuTextureUsage, ModelGpuTexture>> uploadedTextures =
                    new ArrayList<>(asset.asset().textures().size());
            for (int textureIndex = 0; textureIndex < asset.asset().textures().size(); textureIndex++) {
                EnumMap<ModelGpuTextureUsage, ModelGpuTexture> variants = new EnumMap<>(ModelGpuTextureUsage.class);
                for (ModelGpuTextureUsage usage : ModelGpuTextureUsage.values()) {
                    if (!required[textureIndex][usage.ordinal()]) continue;
                    String label = "model:" + asset.asset().id() + "/texture_" + textureIndex
                            + "/" + usage.name().toLowerCase(java.util.Locale.ROOT);
                    ModelGpuTexture texture = ModelGpuTextureCompiler.upload(
                            rhi, label, asset.asset().textures().get(textureIndex), usage);
                    variants.put(usage, texture);
                    textureRollback.add(texture);
                }
                uploadedTextures.add(Map.copyOf(variants));
            }

            ModelGpuTexture whiteColor = ModelGpuTextureCompiler.solid(rhi,
                    "model:" + asset.asset().id() + "/fallback_white_color",
                    0xFFFFFFFF, ModelGpuTextureUsage.SRGB_COLOR);
            textureRollback.add(whiteColor);
            ModelGpuTexture whiteData = ModelGpuTextureCompiler.solid(rhi,
                    "model:" + asset.asset().id() + "/fallback_white_data",
                    0xFFFFFFFF, ModelGpuTextureUsage.LINEAR_DATA);
            textureRollback.add(whiteData);
            ModelGpuTexture normal = ModelGpuTextureCompiler.solid(rhi,
                    "model:" + asset.asset().id() + "/fallback_normal",
                    0xFF8080FF, ModelGpuTextureUsage.NORMAL_DATA);
            textureRollback.add(normal);
            ModelGpuTexture blackColor = ModelGpuTextureCompiler.solid(rhi,
                    "model:" + asset.asset().id() + "/fallback_black_color",
                    0xFF000000, ModelGpuTextureUsage.SRGB_COLOR);
            textureRollback.add(blackColor);

            return new ModelGpuAssetResidency(rhi, asset, uploadedMeshes, uploadedTextures,
                    whiteColor, whiteData, normal, blackColor);
        } catch (RuntimeException | Error failure) {
            for (int i = textureRollback.size() - 1; i >= 0; i--) {
                try { textureRollback.get(i).close(); } catch (Throwable ignored) { }
            }
            for (int i = primitiveRollback.size() - 1; i >= 0; i--) {
                try { primitiveRollback.get(i).close(); } catch (Throwable ignored) { }
            }
            throw failure;
        }
    }

    public CombatantRhi owner() { return owner; }
    public ModelRuntimeAsset asset() { return asset; }
    public int meshCount() { return meshes.size(); }
    public boolean isClosed() { return closed; }

    public ModelGpuPrimitive primitive(int meshIndex, int primitiveIndex) {
        ensureOpen();
        return meshes.get(meshIndex).get(primitiveIndex);
    }

    public List<ModelGpuPrimitive> mesh(int meshIndex) {
        ensureOpen();
        return meshes.get(meshIndex);
    }

    /** Returns the exact semantic variant uploaded for this texture, or null when unused by the asset. */
    public ModelGpuTexture texture(int textureIndex, ModelGpuTextureUsage usage) {
        ensureOpen();
        if (textureIndex < 0 || textureIndex >= textures.size() || usage == null) return null;
        return textures.get(textureIndex).get(usage);
    }

    public ModelGpuTexture colorOrWhite(ModelTextureInfo info) {
        if (info == null || !info.present()) return fallbackWhiteColor;
        ModelGpuTexture texture = texture(info.texture(), ModelGpuTextureUsage.SRGB_COLOR);
        return texture != null ? texture : fallbackWhiteColor;
    }

    public ModelGpuTexture emissiveOrBlack(ModelTextureInfo info) {
        if (info == null || !info.present()) return fallbackBlackColor;
        ModelGpuTexture texture = texture(info.texture(), ModelGpuTextureUsage.SRGB_COLOR);
        return texture != null ? texture : fallbackBlackColor;
    }

    public ModelGpuTexture dataOrWhite(ModelTextureInfo info) {
        if (info == null || !info.present()) return fallbackWhiteData;
        ModelGpuTexture texture = texture(info.texture(), ModelGpuTextureUsage.LINEAR_DATA);
        return texture != null ? texture : fallbackWhiteData;
    }

    public ModelGpuTexture normalOrFlat(ModelTextureInfo info) {
        if (info == null || !info.present()) return fallbackNormal;
        ModelGpuTexture texture = texture(info.texture(), ModelGpuTextureUsage.NORMAL_DATA);
        return texture != null ? texture : fallbackNormal;
    }

    public int primitiveCount() {
        int count = 0;
        for (List<ModelGpuPrimitive> mesh : meshes) count += mesh.size();
        return count;
    }

    public int textureVariantCount() {
        int count = 4; // fallback textures are real resident resources.
        for (Map<ModelGpuTextureUsage, ModelGpuTexture> variants : textures) count += variants.size();
        return count;
    }

    public long textureGpuBytesEstimate() {
        long bytes = fallbackWhiteColor.gpuBytesEstimate() + fallbackWhiteData.gpuBytesEstimate()
                + fallbackNormal.gpuBytesEstimate() + fallbackBlackColor.gpuBytesEstimate();
        for (Map<ModelGpuTextureUsage, ModelGpuTexture> variants : textures) {
            for (ModelGpuTexture texture : variants.values()) bytes += texture.gpuBytesEstimate();
        }
        return bytes;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (Map<ModelGpuTextureUsage, ModelGpuTexture> variants : textures) {
            for (ModelGpuTexture texture : variants.values()) {
                try { texture.close(); } catch (Throwable ignored) { }
            }
        }
        try { fallbackWhiteColor.close(); } catch (Throwable ignored) { }
        try { fallbackWhiteData.close(); } catch (Throwable ignored) { }
        try { fallbackNormal.close(); } catch (Throwable ignored) { }
        try { fallbackBlackColor.close(); } catch (Throwable ignored) { }
        for (List<ModelGpuPrimitive> mesh : meshes) {
            for (ModelGpuPrimitive primitive : mesh) {
                try { primitive.close(); } catch (Throwable ignored) { }
            }
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Model GPU residency is closed");
    }

    private static boolean[][] requiredTextureUsages(ModelRuntimeAsset asset) {
        int textureCount = asset.asset().textures().size();
        boolean[][] required = new boolean[textureCount][ModelGpuTextureUsage.values().length];
        for (ModelMaterialBinding material : asset.materials()) {
            require(required, material.baseColorTexture(), ModelGpuTextureUsage.SRGB_COLOR);
            require(required, material.emissiveTexture(), ModelGpuTextureUsage.SRGB_COLOR);
            require(required, material.normalTexture(), ModelGpuTextureUsage.NORMAL_DATA);
            require(required, material.metallicRoughnessTexture(), ModelGpuTextureUsage.LINEAR_DATA);
            require(required, material.occlusionTexture(), ModelGpuTextureUsage.LINEAR_DATA);
        }
        return required;
    }

    private static void require(boolean[][] required, ModelTextureInfo info, ModelGpuTextureUsage usage) {
        if (info == null || !info.present()) return;
        if (info.texture() < 0 || info.texture() >= required.length) {
            throw new IllegalArgumentException("Material references model texture outside asset range: " + info.texture());
        }
        required[info.texture()][usage.ordinal()] = true;
    }
}
