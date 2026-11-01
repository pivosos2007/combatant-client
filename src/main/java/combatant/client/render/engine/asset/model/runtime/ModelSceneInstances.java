/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.runtime;

import combatant.client.render.engine.asset.model.ModelRuntimeAsset;
import combatant.client.render.engine.asset.model.data.ModelBounds;
import combatant.client.render.engine.scene.SceneDrawClass;
import combatant.client.render.engine.scene.instance.SceneAssetInstance;
import combatant.client.render.engine.scene.instance.SceneInstanceRegistry;
import combatant.client.render.engine.scene.lod.SceneLodProfile;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4fc;

/**
 * Bridge from imported model CPU assets to the generic instance/spatial runtime.
 * The default class is {@link SceneDrawClass#ENTITY}; callers must opt into
 * {@link SceneDrawClass#WORLD_OVERLAY} (no depth) or {@link SceneDrawClass#HAND} explicitly.
 */
public final class ModelSceneInstances {
    private ModelSceneInstances() {}

    public static SceneAssetInstance<ModelSceneInstanceAsset> spawnDefault(
            SceneInstanceRegistry registry,
            ModelRuntimeAsset asset,
            Matrix4fc worldTransform,
            SceneLodProfile lodProfile) {
        if (asset == null) throw new IllegalArgumentException("Imported model runtime asset is required");
        int sceneIndex = asset.asset().defaultScene();
        if (sceneIndex < 0) throw new IllegalArgumentException("Imported model has no default scene: " + asset.asset().id());
        return spawn(registry, asset, sceneIndex, worldTransform, SceneDrawClass.ENTITY, lodProfile);
    }

    public static SceneAssetInstance<ModelSceneInstanceAsset> spawnDefault(
            SceneInstanceRegistry registry,
            ModelRuntimeAsset asset,
            Matrix4fc worldTransform,
            SceneDrawClass drawClass,
            SceneLodProfile lodProfile) {
        if (asset == null) throw new IllegalArgumentException("Imported model runtime asset is required");
        int sceneIndex = asset.asset().defaultScene();
        if (sceneIndex < 0) throw new IllegalArgumentException("Imported model has no default scene: " + asset.asset().id());
        return spawn(registry, asset, sceneIndex, worldTransform, drawClass, lodProfile);
    }

    /**
     * Registers a first-person imported scene. The supplied transform is hand-local and is applied
     * on top of the live Minecraft hand pose when {@code renderItemInHand} executes.
     */
    public static SceneAssetInstance<ModelSceneInstanceAsset> spawnHand(
            SceneInstanceRegistry registry,
            ModelRuntimeAsset asset,
            Matrix4fc handTransform,
            SceneLodProfile lodProfile) {
        return spawnDefault(registry, asset, handTransform, SceneDrawClass.HAND, lodProfile);
    }

    public static SceneAssetInstance<ModelSceneInstanceAsset> spawn(
            SceneInstanceRegistry registry,
            ModelRuntimeAsset asset,
            int sceneIndex,
            Matrix4fc worldTransform,
            SceneLodProfile lodProfile) {
        return spawn(registry, asset, sceneIndex, worldTransform, SceneDrawClass.ENTITY, lodProfile);
    }

    public static SceneAssetInstance<ModelSceneInstanceAsset> spawn(
            SceneInstanceRegistry registry,
            ModelRuntimeAsset asset,
            int sceneIndex,
            Matrix4fc worldTransform,
            SceneDrawClass drawClass,
            SceneLodProfile lodProfile) {
        if (registry == null) throw new IllegalArgumentException("scene instance registry is required");
        if (asset == null) throw new IllegalArgumentException("Imported model runtime asset is required");
        ModelSceneLayout layout = asset.sceneLayout(sceneIndex);
        if (!layout.hasGeometry()) {
            throw new IllegalArgumentException("Imported model scene contains no renderable geometry: "
                    + asset.asset().id() + " scene=" + sceneIndex);
        }
        ModelBounds bounds = layout.bounds();
        AABB localBounds = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
        ModelSceneInstanceAsset source = new ModelSceneInstanceAsset(asset, sceneIndex, layout);
        return registry.register(source, localBounds, worldTransform,
                drawClass == null ? SceneDrawClass.ENTITY : drawClass, lodProfile);
    }
}
