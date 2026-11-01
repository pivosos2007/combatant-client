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
package combatant.client.render.engine.asset.gltf.io;

import net.minecraft.resources.Identifier;
import combatant.client.render.engine.asset.model.data.ModelAsset;
import combatant.client.render.engine.asset.gltf.io.JgltfAssetLoader;

import java.io.IOException;

/** Side-neutral entry point for synchronous glTF/GLB decoding. */
public final class GltfLoader {
    private GltfLoader() {
    }

    public static ModelAsset load(Identifier location, GltfResolver resolver) throws IOException {
        return new JgltfAssetLoader().load(location, resolver::open);
    }

    public static ModelAsset load(Identifier location, GltfResolver resolver,
                                 long maxResourceBytes) throws IOException {
        return new JgltfAssetLoader(maxResourceBytes).load(location, resolver::open);
    }
}
