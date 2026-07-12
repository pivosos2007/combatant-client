/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text.backend;

/**
 * Placement is orthogonal to the glyph backend. It answers where text is placed,
 * not how glyphs are rasterized/uploaded.
 */
public enum TextPlacementMode {
    UI,
    SCREEN_SPACE,
    WORLD_BILLBOARD,
    WORLD_ALIGNED;

    public static TextPlacementMode fromDomain(TextRenderDomain domain) {
        if (domain == null) return UI;
        return switch (domain) {
            case UI -> UI;
            case SCREEN -> SCREEN_SPACE;
            case WORLD -> WORLD_BILLBOARD;
        };
    }

    public boolean world() {
        return this == WORLD_BILLBOARD || this == WORLD_ALIGNED;
    }

    public boolean screenLike() {
        return this == UI || this == SCREEN_SPACE;
    }

    public TextRenderDomain legacyDomain() {
        return switch (this) {
            case UI -> TextRenderDomain.UI;
            case SCREEN_SPACE -> TextRenderDomain.SCREEN;
            case WORLD_BILLBOARD, WORLD_ALIGNED -> TextRenderDomain.WORLD;
        };
    }
}
