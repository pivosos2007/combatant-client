/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.reconcile;

public record UiKeyPath(String value) {
    public UiKeyPath child(String keyOrIndex) {
        String child = keyOrIndex == null || keyOrIndex.isBlank() ? "?" : keyOrIndex;
        return new UiKeyPath(value == null || value.isBlank() ? child : value + "/" + child);
    }
}
