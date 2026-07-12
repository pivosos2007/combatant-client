/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounds patch builder keyed by stable UI node keys. */
public final class UiBoundsPatchSet {
    private final Map<String, UiBounds> patches;

    private UiBoundsPatchSet(int expectedNodes) {
        this.patches = new LinkedHashMap<>(Math.max(1, expectedNodes));
    }

    public static UiBoundsPatchSet create() {
        return new UiBoundsPatchSet(16);
    }

    public static UiBoundsPatchSet create(int expectedNodes) {
        return new UiBoundsPatchSet(expectedNodes);
    }

    public UiBoundsPatchSet put(String nodeKey, float x, float y, float width, float height) {
        if (nodeKey != null) {
            patches.put(nodeKey, new UiBounds(x, y, Math.max(0.0f, width), Math.max(0.0f, height)));
        }
        return this;
    }

    public Map<String, UiBounds> asMap() {
        return patches;
    }
}
