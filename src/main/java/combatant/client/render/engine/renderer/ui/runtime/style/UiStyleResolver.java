/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeSpec;

/**
 * Combines the class-derived or explicit base style with a node specification's inline overrides.
 */
public final class UiStyleResolver {
    private final UiStyleCache cache;

    public UiStyleResolver(UiStyleCache cache) {
        this.cache = cache != null ? cache : new UiStyleCache();
    }

    public UiStyle resolve(UiNodeSpec spec) {
        if (spec == null) return UiStyle.DEFAULT;
        UiStyle base = !spec.styleClass().isBlank()
                ? cache.resolve(spec.styleClass())
                : spec.style();
        return spec.inlineStyle().apply(base);
    }
}
