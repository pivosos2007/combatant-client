/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.asset;

import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;

import java.util.Locale;

public final class UiAssetResolver {
    private final UiAssetRegistry registry;

    public UiAssetResolver() {
        this(null);
    }

    public UiAssetResolver(UiAssetRegistry registry) {
        this.registry = registry;
    }

    private static float number(Object value, float fallback) {
        if (value instanceof Number n) return n.floatValue();
        if (value instanceof String s) {
            try {
                return Float.parseFloat(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    public UiAssetRef resolve(UiProps props) {
        if (props == null) return null;
        String type = props.string("assetType", props.string("kind", "texture"));
        String id = props.string("asset", props.string("id", ""));
        if (id.isBlank()) return null;
        float width = number(props.get("intrinsicWidth"), 16.0f);
        float height = number(props.get("intrinsicHeight"), width);
        if (registry != null) {
            UiAssetRef dynamic = registry.resolve(props, type, id, width, height);
            if (dynamic != null) return dynamic;
        }
        UiAssetKind kind = switch (type.toLowerCase(Locale.ROOT)) {
            case "gui-sprite", "gui_sprite", "sprite" -> UiAssetKind.GUI_SPRITE;
            case "svg" -> UiAssetKind.SVG;
            case "player-head", "player_head", "head" -> UiAssetKind.PLAYER_HEAD;
            case "media", "media-artwork", "media_artwork" -> UiAssetKind.MEDIA_ARTWORK;
            case "item" -> UiAssetKind.ITEM;
            default -> UiAssetKind.TEXTURE;
        };
        return new UiAssetRef(kind, id, width, height);
    }
}
