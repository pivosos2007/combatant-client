/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.NoRender;
import combatant.client.util.block.BlockObservationHub;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @Inject(method = "setServerVerifiedBlockState", at = @At("TAIL"))
    private void combatant$recordBlockUpdate(BlockPos pos, BlockState state, @Block.UpdateFlags int flags, CallbackInfo ci) {
        BlockObservationHub.observeWorldUpdate(pos, state);
    }

    @Inject(
            method = "doAddParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$noRenderEatParticles(
            ParticleOptions effect,
            boolean force,
            boolean canSpawnOnMinimal,
            double x, double y, double z,
            double vx, double vy, double vz,
            CallbackInfo ci
    ) {
        NoRender nr = Modules.get(NoRender.class);
        if (nr == null || !nr.isEnabled()) return;

        // частицы еды / питья
        if (nr.off("eat_particles")
                && effect instanceof ItemParticleOption) {
            ci.cancel();
        }

        if (nr.off("hit_particles")) {
            var type = effect.getType();
            if (type == ParticleTypes.DAMAGE_INDICATOR
                    || type == ParticleTypes.CRIT
                    || type == ParticleTypes.ENCHANTED_HIT) {
                ci.cancel();
            }
        }

        if (nr.off("sweep_particles")) {
            var type = effect.getType();
            if (type == ParticleTypes.SWEEP_ATTACK) {
                ci.cancel();
            }
        }
    }

@Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
    private void combatant$noRenderBlockBreakParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
        NoRender noRender = Modules.get(NoRender.class);
        if (noRender != null
                && (noRender.offParticle("all_particles") || noRender.offParticle("block_break_particles"))) {
            ci.cancel();
        }
    }

    @Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true)
    private void combatant$noRenderBlockBreakingParticles(BlockPos pos, Direction direction, CallbackInfo ci) {
        NoRender noRender = Modules.get(NoRender.class);
        if (noRender != null
                && (noRender.offParticle("all_particles") || noRender.offParticle("block_breaking_particles"))) {
            ci.cancel();
        }
    }

    @org.spongepowered.asm.mixin.injection.Redirect(
            method = "tickWeatherEffects",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
            )
    )
    private void combatant$noRenderRainSplash(
            ClientLevel level,
            ParticleOptions effect,
            double x, double y, double z,
            double vx, double vy, double vz
    ) {
        NoRender noRender = Modules.get(NoRender.class);
        if (noRender != null
                && (noRender.offParticle("all_particles") || noRender.offParticle("rain_splash_particles"))) {
            return;
        }
        level.addParticle(effect, x, y, z, vx, vy, vz);
    }
}
