/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiStyleCacheTest {
    @Test
    void repeatedClassHitsDoNotCreateAnotherParsedStyle() {
        UiStyleCache cache = new UiStyleCache();
        UiStyle first = cache.resolve("p-2");
        assertSame(first, cache.resolve("p-2"));
        assertEquals(1, cache.misses());
        assertEquals(1, cache.hits());
    }

    @Test
    void themeChangeWithSameVersionInvalidatesParsedStyles() {
        UiTheme original = UiThemeRegistry.current();
        try {
            UiStyleCache cache = new UiStyleCache();
            UiThemeRegistry.setCurrent(new UiTheme("one", 7));
            cache.resolve("p-2");
            cache.resolve("p-2");
            UiThemeRegistry.setCurrent(new UiTheme("two", 7));
            cache.resolve("p-2");
            assertEquals(2, cache.misses());
            assertEquals(1, cache.hits());

            cache.clear();
            cache.resolve("p-2");
            assertEquals(1, cache.misses());
            assertEquals(0, cache.hits());
        } finally {
            UiThemeRegistry.setCurrent(original);
        }
    }
}
