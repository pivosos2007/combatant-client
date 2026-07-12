/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.resource;

import combatant.client.render.engine.Texture;

public record GlyphAtlasPage(String id, Texture texture, int width, int height, boolean msdf) {
}
