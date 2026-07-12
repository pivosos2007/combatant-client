/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.asset;

public record UiAssetRef(UiAssetKind kind, String id, float intrinsicWidth, float intrinsicHeight) {
    public static UiAssetRef texture(String id, float width, float height) {
        return new UiAssetRef(UiAssetKind.TEXTURE, id, width, height);
    }

    public String getId() {
        return id;
    }
}
