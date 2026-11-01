/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model;

import combatant.client.render.engine.asset.model.geometry.ModelCompiledPrimitive;
import combatant.client.render.engine.asset.model.geometry.ModelGeometryCompiler;
import combatant.client.render.engine.asset.model.material.ModelMaterialBinding;
import combatant.client.render.engine.asset.model.data.ModelAsset;
import combatant.client.render.engine.asset.model.data.ModelMesh;
import combatant.client.render.engine.asset.model.data.ModelPrimitive;
import combatant.client.render.engine.asset.model.runtime.ModelSceneLayout;

import java.util.List;
import java.util.Objects;

/**
 * Immutable imported asset plus Combatant material bindings. This deliberately stops before GPU
 * residency: OpenGL and Vulkan upload the same compiled primitive through the shared RHI layer.
 */
public final class ModelRuntimeAsset {
    private final ModelAsset asset;
    private final List<ModelMaterialBinding> materials;
    private final List<ModelSceneLayout> sceneLayouts;
    private final long sourceEpoch;

    public ModelRuntimeAsset(ModelAsset asset, List<ModelMaterialBinding> materials) {
        this(asset, materials, 0L);
    }

    public ModelRuntimeAsset(ModelAsset asset, List<ModelMaterialBinding> materials, long sourceEpoch) {
        this.asset = Objects.requireNonNull(asset, "asset");
        this.materials = List.copyOf(Objects.requireNonNull(materials, "materials"));
        this.sourceEpoch = Math.max(0L, sourceEpoch);
        if (this.materials.size() != asset.materials().size()) {
            throw new IllegalArgumentException("Material binding count does not match imported asset");
        }
        java.util.ArrayList<ModelSceneLayout> layouts = new java.util.ArrayList<>(asset.scenes().size());
        for (int sceneIndex = 0; sceneIndex < asset.scenes().size(); sceneIndex++) {
            layouts.add(ModelSceneLayout.build(asset, sceneIndex));
        }
        this.sceneLayouts = List.copyOf(layouts);
    }

    public ModelAsset asset() {
        return asset;
    }

    public List<ModelMaterialBinding> materials() {
        return materials;
    }

    public ModelMaterialBinding material(int index) {
        return materials.get(index);
    }

    public ModelSceneLayout sceneLayout(int sceneIndex) {
        return sceneLayouts.get(sceneIndex);
    }

    public ModelSceneLayout defaultSceneLayout() {
        int sceneIndex = asset.defaultScene();
        if (sceneIndex < 0) throw new IllegalStateException("Imported model has no default scene: " + asset.id());
        return sceneLayouts.get(sceneIndex);
    }

    public List<ModelSceneLayout> sceneLayouts() {
        return sceneLayouts;
    }

    /** Zero means caller-managed/non-repository asset; positive values are resource-pack epochs. */
    public long sourceEpoch() {
        return sourceEpoch;
    }

    public ModelCompiledPrimitive compilePrimitive(int meshIndex, int primitiveIndex) {
        ModelMesh mesh = asset.meshes().get(meshIndex);
        ModelPrimitive primitive = mesh.primitives().get(primitiveIndex);
        int normalTexCoordSet = material(primitive.material()).normalTexture().texCoord();
        return ModelGeometryCompiler.compile(primitive, normalTexCoordSet);
    }
}
