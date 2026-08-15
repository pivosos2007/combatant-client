/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module;

public enum WorldPhase {
    NONE,

    BEFORE_ENTITIES,     // WorldRenderEvents.BEFORE_ENTITIES
    AFTER_ENTITIES,      // WorldRenderEvents.AFTER_ENTITIES
    BEFORE_TRANSLUCENT,  // WorldRenderEvents.BEFORE_TRANSLUCENT
    END_MAIN,            // WorldRenderEvents.END_MAIN (аналог старого LAST)
    END_MAIN_BILLBOARD,  // Same END_MAIN world stage, submitted after regular effects/particles
    AFTER_POST_PROCESS   // After PRE_HAND postprocess, before hand render
}
