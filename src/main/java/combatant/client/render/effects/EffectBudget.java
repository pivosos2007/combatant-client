/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects;

/** Runtime budget shared by current CPU and future GPU transient backends. */
public record EffectBudget(
        int maxActive,
        int maxSpawnPerTick,
        int minimumPriority
) {
    public static final EffectBudget DEFAULT = new EffectBudget(1024, 192, Integer.MIN_VALUE);

    public EffectBudget {
        maxActive = Math.max(1, maxActive);
        maxSpawnPerTick = Math.max(1, maxSpawnPerTick);
    }
}
