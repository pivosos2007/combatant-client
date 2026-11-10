/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.action;

/**
 * Handles a UI action request. The result is propagated as the dispatch result.
 */
@FunctionalInterface
public interface UiAction {
    boolean run(UiActionContext context);
}
