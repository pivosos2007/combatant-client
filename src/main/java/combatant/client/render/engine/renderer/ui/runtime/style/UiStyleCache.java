/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public final class UiStyleCache {
    private final UiStyleParser parser = new UiStyleParser();
    private final Object2ObjectOpenHashMap<String, UiStyle> cache = new Object2ObjectOpenHashMap<>();
    private int hits;
    private int misses;

    public UiStyle resolve(String classString) {
        String key = (classString != null ? classString : "") + "|theme:" + UiThemeRegistry.current().version();
        UiStyle cached = cache.get(key);
        if (cached != null) {
            hits++;
            return cached;
        }
        misses++;
        UiStyle style = parser.parse(classString).style();
        cache.put(key, style);
        cache.trim();
        return style;
    }

    public int hits() {
        return hits;
    }

    public int misses() {
        return misses;
    }

    public void clear() {
        cache.clear();
        hits = 0;
        misses = 0;
    }
}
