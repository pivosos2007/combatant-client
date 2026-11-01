/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.runtime;

import combatant.client.render.engine.asset.model.ModelRuntimeAsset;

import java.util.Objects;

/** Immutable asset+scene reference stored by a live SceneAssetInstance. */
public record ModelSceneInstanceAsset(
        ModelRuntimeAsset asset,
        int sceneIndex,
        ModelSceneLayout layout
) {
    public ModelSceneInstanceAsset {
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(layout, "layout");
        if (sceneIndex != layout.sceneIndex()) throw new IllegalArgumentException("Scene/layout mismatch");
    }
}
