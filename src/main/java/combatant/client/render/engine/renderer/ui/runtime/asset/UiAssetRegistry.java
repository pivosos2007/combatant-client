/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.asset;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;

import java.util.Locale;

public final class UiAssetRegistry {
    private final Object2ObjectOpenHashMap<String, UiAssetProvider> providers = new Object2ObjectOpenHashMap<>();

    private static String normalize(String raw) {
        return raw.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }

    public void register(String type, UiAssetProvider provider) {
        if (type == null || type.isBlank() || provider == null) return;
        providers.put(normalize(type), provider);
    }

    public void unregister(String type) {
        if (type == null || type.isBlank()) return;
        providers.remove(normalize(type));
    }

    public void clear() {
        providers.clear();
    }

    public UiAssetRef resolve(UiProps props, String type, String id, float width, float height) {
        UiAssetProvider provider = providers.get(normalize(type));
        return provider != null ? provider.resolve(props, type, id, width, height) : null;
    }
}
