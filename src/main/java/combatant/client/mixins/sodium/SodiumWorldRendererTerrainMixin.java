/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import com.mojang.blaze3d.textures.GpuSampler;
import combatant.client.render.engine.core.CombatantRenderSystem;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures Sodium's exact terrain pass boundary without cancelling or replacing the production draw.
 */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class SodiumWorldRendererTerrainMixin {
    @Inject(method = "renderLayer(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;DDDLnet/caffeinemc/mods/sodium/client/util/FogParameters;Lcom/mojang/blaze3d/textures/GpuSampler;)V", at = @At("HEAD"), remap = false)
    private void combatant$beforeTerrainLayer(ChunkRenderMatrices matrices,
                                              TerrainRenderPass pass,
                                              double cameraX,
                                              double cameraY,
                                              double cameraZ,
                                              FogParameters fog,
                                              GpuSampler sampler,
                                              CallbackInfo ci) {
        CombatantRenderSystem.sodium().terrainInterop().beforeTerrainDraw(
                matrices, pass, cameraX, cameraY, cameraZ, fog, sampler);
    }

    @Inject(method = "renderLayer(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;DDDLnet/caffeinemc/mods/sodium/client/util/FogParameters;Lcom/mojang/blaze3d/textures/GpuSampler;)V", at = @At("RETURN"), remap = false)
    private void combatant$afterTerrainLayer(ChunkRenderMatrices matrices,
                                             TerrainRenderPass pass,
                                             double cameraX,
                                             double cameraY,
                                             double cameraZ,
                                             FogParameters fog,
                                             GpuSampler sampler,
                                             CallbackInfo ci) {
        CombatantRenderSystem.sodium().terrainInterop().afterTerrainDraw(
                matrices, pass, cameraX, cameraY, cameraZ, fog, sampler);
    }
}
