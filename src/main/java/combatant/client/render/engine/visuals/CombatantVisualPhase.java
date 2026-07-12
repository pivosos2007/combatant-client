/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.visuals;

/**
 * Render graph ordering for the custom world visual stack.
 */
public enum CombatantVisualPhase {
    PREPARE,
    SHADOWS,
    VOLUMETRICS,
    REFLECTIONS,
    COMPOSITE
}
