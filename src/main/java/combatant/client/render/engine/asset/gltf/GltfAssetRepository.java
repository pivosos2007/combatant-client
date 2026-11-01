/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.gltf;

import combatant.client.render.engine.asset.model.ImportedModelRepository;
import combatant.client.render.engine.asset.model.ModelRuntimeAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;

/** @deprecated Use ImportedModelRepository; this source-compatible adapter owns no cache. */
@Deprecated
public final class GltfAssetRepository {
    private static final GltfAssetRepository GLOBAL = new GltfAssetRepository();
    private final ImportedModelRepository shared = ImportedModelRepository.global();

    public GltfAssetRepository() {}
    public static GltfAssetRepository global() { return GLOBAL; }
    public ModelRuntimeAsset load(ResourceManager resources, Enum<?> asset) throws IOException {
        return shared.load(resources, asset);
    }
    public ModelRuntimeAsset load(ResourceManager resources, Identifier id) throws IOException {
        return shared.load(resources, id);
    }
    public ModelRuntimeAsset cached(Identifier id) { return shared.cached(id); }
    public boolean isCached(Identifier id) { return shared.isCached(id); }
    public int cachedCount() { return shared.cachedCount(); }
    public long epoch() { return shared.epoch(); }
    public void invalidateAll() { shared.invalidateAll(); }
    /** @deprecated Reload registration now lives in ImportedModelRepository. */
    @Deprecated
    public static void invalidateResourcePackAssets() { ImportedModelRepository.invalidateResourcePackAssets(); }
}
