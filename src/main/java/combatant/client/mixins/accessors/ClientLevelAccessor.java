/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.accessors;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {
    @Invoker("doAddParticle")
    void invokeAddParticle(ParticleOptions effect,
                           boolean force,
                           boolean canSpawnOnMinimal,
                           double x, double y, double z,
                           double vx, double vy, double vz);

    @Invoker("getBlockStatePredictionHandler")
    BlockStatePredictionHandler combatant$getPendingUpdateManager();
}


