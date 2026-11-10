/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/**
 * Caches parsed UI class styles by class string and active theme version.
 * {@link #clear()} invalidates parsed entries and resets hit/miss counters.
 */
public final class UiStyleCache {
    private final UiStyleParser parser = new UiStyleParser();
    private final Object2ObjectOpenHashMap<String, UiStyle> cache = new Object2ObjectOpenHashMap<>();
    private int hits;
    private int misses;
    private UiTheme cachedTheme;
    private int cachedVersion = -1;

    public UiStyle resolve(String classString) {
        UiTheme theme = UiThemeRegistry.current();
        if (cachedTheme != theme || cachedVersion != theme.version()) {
            cache.clear();
            cachedTheme = theme;
            cachedVersion = theme.version();
        }
        // The cache belongs to one theme generation. No per-lookup key concatenation is needed.
        String key = classString != null ? classString : "";
        UiStyle cached = cache.get(key);
        if (cached != null) {
            hits++;
            return cached;
        }
        misses++;
        UiStyle style = parser.parse(classString).style();
        // trim() on every miss rebuilt the hash table repeatedly as unique styles accumulated.
        cache.put(key, style);
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
        cachedTheme = null;
        cachedVersion = -1;
        hits = 0;
        misses = 0;
    }
}
