/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.material;

import combatant.client.render.engine.asset.model.data.AlphaMode;
import combatant.client.render.engine.asset.model.data.ModelAsset;
import combatant.client.render.engine.asset.model.data.ModelMaterial;
import combatant.client.render.engine.asset.model.data.ModelTextureInfo;
import combatant.client.render.engine.material.*;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Maps metallic-roughness semantics into Combatant's backend-neutral material contract. */
public final class ModelMaterialBridge {
    private ModelMaterialBridge() {}

    public static List<ModelMaterialBinding> build(ModelAsset asset) {
        Objects.requireNonNull(asset, "asset");
        List<ModelMaterialBinding> result = new ArrayList<>(asset.materials().size());
        for (int index = 0; index < asset.materials().size(); index++) {
            result.add(build(asset.id(), index, asset.materials().get(index)));
        }
        return List.copyOf(result);
    }

    public static ModelMaterialBinding build(Identifier assetId, int materialIndex, ModelMaterial material) {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(material, "material");
        if (materialIndex < 0) throw new IllegalArgumentException("materialIndex");

        EnumMap<MaterialTextureSemantic, MaterialTextureSource> textures = new EnumMap<>(MaterialTextureSemantic.class);
        put(textures, MaterialTextureSemantic.ALBEDO, assetId, material.baseColorTextureInfo());
        put(textures, MaterialTextureSemantic.NORMAL, assetId, material.normalTextureInfo());
        put(textures, MaterialTextureSemantic.AMBIENT_OCCLUSION, assetId, material.occlusionTextureInfo());
        put(textures, MaterialTextureSemantic.METALLIC_ROUGHNESS, assetId, material.metallicRoughnessTextureInfo());
        put(textures, MaterialTextureSemantic.EMISSIVE, assetId, material.emissiveTextureInfo());

        int traits = 0;
        if (material.doubleSided()) traits |= MaterialTrait.DOUBLE_SIDED.bit();
        float emission = Math.max(material.emissiveRed(), Math.max(material.emissiveGreen(), material.emissiveBlue()))
                * material.emissiveStrength();
        if (emission > 0.0f || material.emissiveTextureInfo().present()) traits |= MaterialTrait.EMISSIVE.bit();

        MaterialDomain domain = switch (material.alphaMode()) {
            case OPAQUE -> MaterialDomain.OPAQUE;
            case MASK -> MaterialDomain.CUTOUT;
            case BLEND -> MaterialDomain.TRANSLUCENT;
        };
        MaterialSubmissionRoute route = material.alphaMode() == AlphaMode.BLEND
                ? MaterialSubmissionRoute.FORWARD_TRANSLUCENT
                : MaterialSubmissionRoute.COMPATIBILITY;
        MaterialTemporalPolicy temporal = material.alphaMode() == AlphaMode.BLEND || emission > 0.0f
                ? MaterialTemporalPolicy.RESPONSIVE
                : MaterialTemporalPolicy.STABLE;

        int stableId = stableId(assetId, materialIndex);
        MaterialSurfaceDescriptor surface = new MaterialSurfaceDescriptor(
                stableId,
                virtualMaterialId(assetId, materialIndex),
                domain,
                traits,
                route,
                MaterialResolutionSource.IMPORTED_ASSET,
                MaterialTextureSet.fromSources(textures),
                material.occlusionStrength(),
                material.roughnessFactor(),
                material.metallicFactor(),
                0.04f,
                emission,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.25f,
                0.5f,
                1.0f,
                temporal,
                MaterialWeatherResponse.NONE,
                MaterialTessellationProfile.NONE
        );

        return new ModelMaterialBinding(
                surface,
                material.baseColor(),
                material.emissive(),
                material.emissiveStrength(),
                material.normalScale(),
                material.occlusionStrength(),
                material.alphaMode(),
                material.alphaCutoff(),
                material.doubleSided(),
                material.unlit(),
                material.baseColorTextureInfo(),
                material.metallicRoughnessTextureInfo(),
                material.normalTextureInfo(),
                material.occlusionTextureInfo(),
                material.emissiveTextureInfo()
        );
    }

    private static void put(Map<MaterialTextureSemantic, MaterialTextureSource> target,
                            MaterialTextureSemantic semantic, Identifier assetId, ModelTextureInfo info) {
        if (info != null && info.present()) {
            target.put(semantic, new MaterialTextureSource.Imported(assetId, info.texture()));
        }
    }

    private static int stableId(Identifier assetId, int materialIndex) {
        int value = 31 * assetId.hashCode() + materialIndex + 1;
        return value == 0 ? 1 : value;
    }

    private static Identifier virtualMaterialId(Identifier assetId, int materialIndex) {
        String path = assetId.getPath().replaceAll("[^a-z0-9/._-]", "_");
        return Identifier.fromNamespaceAndPath("combatant",
                "imported/" + assetId.getNamespace() + "/" + path + "/material_" + materialIndex);
    }
}
