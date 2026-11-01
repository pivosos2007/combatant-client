/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model;

import combatant.client.render.engine.asset.model.io.MinecraftModelResolver;
import combatant.client.render.engine.asset.model.io.ModelImporters;
import combatant.client.render.engine.asset.model.material.ModelMaterialBridge;
import combatant.client.render.engine.asset.model.ModelRuntimeAsset;
import combatant.client.render.engine.asset.model.data.ModelAsset;
import combatant.client.util.resources.ResourceAccess;
import combatant.client.util.resources.asset.AssetLoad;
import combatant.client.util.resources.asset.AssetLoadPhase;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Resource-pack scoped format-independent CPU asset cache. GPU handles intentionally do not live here: a resource
 * reload invalidates this repository, while GL/Vulkan residency is owned by the RHI lifecycle.
 */
public final class ImportedModelRepository {
    private static final ImportedModelRepository GLOBAL = new ImportedModelRepository();

    private final Map<Identifier, ModelRuntimeAsset> cache = new ConcurrentHashMap<>();
    private final AtomicLong epoch = new AtomicLong(1L);

    public static ImportedModelRepository global() {
        return GLOBAL;
    }

    public ModelRuntimeAsset load(ResourceManager resources, Enum<?> asset) throws IOException {
        return load(resources, ResourceAccess.id(asset));
    }

    public ModelRuntimeAsset load(ResourceManager resources, Identifier id) throws IOException {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(id, "id");
        long loadEpoch = epoch.get();
        ModelRuntimeAsset cached = cache.get(id);
        if (cached != null && (cached.sourceEpoch() == 0L || cached.sourceEpoch() == loadEpoch)) return cached;

        ModelAsset imported = ModelImporters.load(id, new MinecraftModelResolver(resources));
        if (epoch.get() != loadEpoch) {
            throw new IOException("Resource epoch changed while loading imported asset " + id);
        }
        ModelRuntimeAsset prepared = new ModelRuntimeAsset(imported, ModelMaterialBridge.build(imported), loadEpoch);
        ModelRuntimeAsset result = cache.compute(id, (key, previous) ->
                previous != null && previous.sourceEpoch() == loadEpoch ? previous : prepared);
        if (epoch.get() != loadEpoch) {
            cache.remove(id, result);
            throw new IOException("Resource epoch changed while caching imported asset " + id);
        }
        return result;
    }

    public ModelRuntimeAsset cached(Identifier id) {
        return id == null ? null : cache.get(id);
    }

    public boolean isCached(Identifier id) {
        return id != null && cache.containsKey(id);
    }

    public int cachedCount() {
        return cache.size();
    }

    public long epoch() {
        return epoch.get();
    }

    public void invalidateAll() {
        epoch.incrementAndGet();
        cache.clear();
    }

    @AssetLoad(value = {AssetLoadPhase.INITIALIZE, AssetLoadPhase.RELOAD}, order = 50)
    public static void invalidateResourcePackAssets() {
        GLOBAL.invalidateAll();
    }
}
