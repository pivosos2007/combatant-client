/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.util.player.effect.StatusEffectHeuristics;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.NoRender;

@Mixin(ParticleEngine.class)
public class ParticleManagerMixin {

    @Inject(
            method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$particleCreate(
            ParticleOptions parameters,
            double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            CallbackInfoReturnable<Particle> cir
    ) {
        StatusEffectHeuristics.handle(parameters, x, y, z);

        NoRender noRender = Modules.get(NoRender.class);
        if (noRender != null && noRender.offParticle("all_particles")) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void combatant$particleAdd(Particle particle, CallbackInfo ci) {
        NoRender noRender = Modules.get(NoRender.class);
        if (noRender != null && noRender.shouldHideParticle(particle)) {
            ci.cancel();
        }
    }
}



