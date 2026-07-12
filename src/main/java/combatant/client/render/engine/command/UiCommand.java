/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.command;

/**
 * Normalized UI command root.
 * <p>
 * Renderer2D facade methods and the newer shape/effect/text/item command families can
 * coexist while callers move toward explicit UI command boundaries.
 */
public interface UiCommand {
    UiCommandKind kind();
}
