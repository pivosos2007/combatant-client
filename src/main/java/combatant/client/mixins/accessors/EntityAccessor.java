/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityAccessor {

    @Accessor("portalProcess")
    PortalProcessor combatant$getPortalManager();

    @Accessor("stuckSpeedMultiplier")
    void combatant$setMovementMultiplier(Vec3 multiplier);

    @Invoker("unsetRemoved")
    void combatant$unsetRemoved();
}


