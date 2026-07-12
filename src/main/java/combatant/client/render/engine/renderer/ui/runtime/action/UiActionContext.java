/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.action;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;

public record UiActionContext(UiNode node, UiActionRef ref, Object event) {
}
