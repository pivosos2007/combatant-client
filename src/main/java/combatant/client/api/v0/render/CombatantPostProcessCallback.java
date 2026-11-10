/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.api.v0.render;

/** Receives a registered post-process pass callback with its source and destination context. */
@FunctionalInterface
public interface CombatantPostProcessCallback {
    boolean render(CombatantPostProcessContext context);
}
