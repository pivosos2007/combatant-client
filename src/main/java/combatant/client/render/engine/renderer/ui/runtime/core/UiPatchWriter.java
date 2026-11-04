/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.core;

/**
 * Sink for render-thread-only updates to an already compiled UI tree.
 *
 * <p>Callers describe the data they want to change, not temporary patch maps.
 * Implementations may store data in a preallocated typed command buffer.</p>
 */
public interface UiPatchWriter {
    void put(String nodeKey, String property, Object value);

    default void putFloat(String nodeKey, String property, float value) {
        put(nodeKey, property, value);
    }

    default void putInt(String nodeKey, String property, int value) {
        put(nodeKey, property, value);
    }

    default void putBoolean(String nodeKey, String property, boolean value) {
        put(nodeKey, property, value);
    }

    void putBounds(String nodeKey, float x, float y, float width, float height);
}
