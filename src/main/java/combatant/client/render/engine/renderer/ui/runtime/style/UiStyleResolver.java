/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeSpec;

public final class UiStyleResolver {
    private final UiStyleCache cache;

    public UiStyleResolver(UiStyleCache cache) {
        this.cache = cache != null ? cache : new UiStyleCache();
    }

    public UiStyle resolve(UiNodeSpec spec) {
        if (spec == null) return UiStyle.DEFAULT;
        if (!spec.styleClass().isBlank()) {
            return cache.resolve(spec.styleClass());
        }
        return spec.style();
    }
}
