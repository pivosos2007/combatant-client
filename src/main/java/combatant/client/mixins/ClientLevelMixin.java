/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.NoRender;
import combatant.client.util.block.BlockObservationHub;
import combatant.client.render.engine.light.BlockLightChangeTracker;
import combatant.client.render.world.ClientLevelEnvironmentSeedView;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin implements ClientLevelEnvironmentSeedView {

    @Unique
    private long combatant$environmentSeed;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void combatant$captureEnvironmentSeed(ClientPacketListener connection,
                                                   ClientLevel.ClientLevelData levelData,
                                                   ResourceKey<Level> dimension,
                                                   Holder<DimensionType> dimensionType,
                                                   int viewDistance,
                                                   int simulationDistance,
                                                   LevelExtractor extractor,
                                                   boolean debug,
                                                   long seed,
                                                   int seaLevel,
                                                   CallbackInfo ci) {
        combatant$environmentSeed = seed;
    }

    @Override
    public long combatant$environmentSeed() {
        return combatant$environmentSeed;
    }

    @Inject(method = "setServerVerifiedBlockState", at = @At("TAIL"))
    private void combatant$recordBlockUpdate(BlockPos pos, BlockState state, @Block.UpdateFlags int flags, CallbackInfo ci) {
        BlockObservationHub.observeWorldUpdate(pos, state);
    }

    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void combatant$invalidateBlockLightOnChunkLoad(ChunkPos pos, CallbackInfo ci) {
        BlockLightChangeTracker.markChanged();
    }

    @Inject(method = "unload", at = @At("TAIL"))
    private void combatant$invalidateBlockLightOnChunkUnload(LevelChunk chunk, CallbackInfo ci) {
        BlockLightChangeTracker.markChanged();
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

    @Inject(method = "addBreakingBlockEffects", at = @At("HEAD"), cancellable = true)
    private void combatant$noRenderBlockBreakingParticles(BlockPos pos, Direction direction, boolean playSound, CallbackInfo ci) {
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
