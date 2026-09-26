/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import com.mojang.blaze3d.textures.GpuSampler;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.postprocess.TemporalAntiAliasingPass;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes Sodium's terrain boundary and keeps its raster projection in the same TAA space
 * as vanilla/entity feature rendering without cancelling or replacing the production draw.
 */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class SodiumWorldRendererTerrainMixin {
    /**
     * Sodium snapshots the projection in its own GameRenderer mixin and later passes that
     * snapshot through ChunkRenderMatrices. Combatant jitters the RenderSystem projection at
     * the same call site, so relying on wrapper order can leave terrain and vanilla/entity
     * rendering in different projection spaces. Replace only the raster matrix here with the
     * exact effective projection already published to the vanilla path. Culling stays stable.
     */
    @ModifyVariable(
            method = "renderLayer(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;DDDLnet/caffeinemc/mods/sodium/client/util/FogParameters;Lcom/mojang/blaze3d/textures/GpuSampler;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false
    )
    private ChunkRenderMatrices combatant$syncTaaTerrainProjection(ChunkRenderMatrices matrices) {
        if (matrices == null || !TemporalAntiAliasingPass.shouldJitter() || !CombatantWorldMatrices.isValid()) {
            return matrices;
        }

        var projection = CombatantWorldMatrices.renderProjectionMatrix();
        if (projection == null) {
            return matrices;
        }
        return new ChunkRenderMatrices(projection, matrices.modelView());
    }

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
