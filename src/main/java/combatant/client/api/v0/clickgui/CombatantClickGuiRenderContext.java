/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.api.v0.clickgui;

import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;

public record CombatantClickGuiRenderContext(
        Renderer2D renderer,
        TextRenderer regularFont,
        TextRenderer mediumFont
) {
}
