/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.api.v0.render;

@FunctionalInterface
public interface CombatantRenderCallback {
    void render(CombatantRenderContext context);
}
