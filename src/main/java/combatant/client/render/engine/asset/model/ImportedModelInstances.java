/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model;

import combatant.client.render.engine.asset.model.ModelRuntimeAsset;
import combatant.client.render.engine.asset.model.runtime.ModelSceneInstanceAsset;
import combatant.client.render.engine.asset.model.runtime.ModelSceneInstances;
import combatant.client.render.engine.scene.SceneDrawClass;
import combatant.client.render.engine.scene.instance.SceneAssetInstance;
import combatant.client.render.engine.scene.instance.SceneInstanceRegistry;
import combatant.client.render.engine.scene.lod.SceneLodProfile;
import org.joml.Matrix4fc;

/** Format-neutral scene spawning API; all imported formats share a single scene/RHI implementation. */
public final class ImportedModelInstances {
    private ImportedModelInstances() {}

    public static SceneAssetInstance<ModelSceneInstanceAsset> spawnDefault(
            SceneInstanceRegistry registry, ModelRuntimeAsset asset,
            Matrix4fc transform, SceneLodProfile lod) {
        return ModelSceneInstances.spawnDefault(registry, asset, transform, lod);
    }

    public static SceneAssetInstance<ModelSceneInstanceAsset> spawnDefault(
            SceneInstanceRegistry registry, ModelRuntimeAsset asset, Matrix4fc transform,
            SceneDrawClass drawClass, SceneLodProfile lod) {
        return ModelSceneInstances.spawnDefault(registry, asset, transform, drawClass, lod);
    }

    public static SceneAssetInstance<ModelSceneInstanceAsset> spawnHand(
            SceneInstanceRegistry registry, ModelRuntimeAsset asset,
            Matrix4fc handTransform, SceneLodProfile lod) {
        return ModelSceneInstances.spawnHand(registry, asset, handTransform, lod);
    }

    public static SceneAssetInstance<ModelSceneInstanceAsset> spawn(
            SceneInstanceRegistry registry, ModelRuntimeAsset asset, int sceneIndex,
            Matrix4fc transform, SceneDrawClass drawClass, SceneLodProfile lod) {
        return ModelSceneInstances.spawn(registry, asset, sceneIndex, transform, drawClass, lod);
    }
}
