/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.api.v0.render;

/** Receives addon rendering callbacks at the registered render stage. */
@FunctionalInterface
public interface CombatantRenderCallback {
    void render(CombatantRenderContext context);
}
