/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixininterface;

import net.minecraft.world.entity.LivingEntity;

/** Client attachment identity is available even before projectile owner sync completes. */
public interface IAttachedFireworkRocket {
    boolean combatant$isAttachedTo(LivingEntity entity);
}
