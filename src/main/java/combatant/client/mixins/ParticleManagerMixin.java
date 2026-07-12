/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.util.player.effect.StatusEffectHeuristics;

@Mixin(ParticleEngine.class)
public class ParticleManagerMixin {

    @Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"))
    private void combatant$heuristicParticle(ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<?> cir) {
        // combatant.client.util.logging.DebugLog.info("ParticleManager add type=%s pos=%.2f %.2f %.2f", parameters.getType().toString(), x, y, z);
        StatusEffectHeuristics.handle(parameters, x, y, z);
    }
}



