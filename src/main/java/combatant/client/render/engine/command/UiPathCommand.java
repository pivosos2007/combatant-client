/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.command;

import combatant.client.render.engine.renderer.ui.draw.UiPaint;
import combatant.client.render.engine.renderer.ui.draw.UiShape;
import combatant.client.render.engine.renderer.ui.draw.UiStroke;

public record UiPathCommand(UiShape path, UiPaint paint, UiStroke stroke, boolean fill) implements UiCommand {
    @Override
    public UiCommandKind kind() {
        return UiCommandKind.PATH;
    }
}
