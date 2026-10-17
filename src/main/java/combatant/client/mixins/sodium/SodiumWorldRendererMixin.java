/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.oit.OitStage;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.postprocess.TemporalAntiAliasingPass;
import combatant.client.render.sodium.SodiumWorldVisibilityView;
import combatant.client.util.render.CameraOcclusionPolicy;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.Freecam;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes Sodium's terrain boundary and keeps its raster projection in the same TAA space
 * as vanilla/entity feature rendering without cancelling or replacing the production draw.
 */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class SodiumWorldRendererMixin implements SodiumWorldVisibilityView {
    @Shadow(remap = false)
    public abstract boolean isBoxVisible(double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ);

    @Override
    public boolean combatant$isBoxVisible(AABB box) {
        return isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    @ModifyVariable(
            method = "setupTerrain(Lnet/minecraft/client/Camera;Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;Lnet/caffeinemc/mods/sodium/client/util/FogParameters;ZZLorg/joml/Matrix4f;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private boolean combatant$sectionOcclusion(boolean useOcclusionCulling,
                                                @Local(argsOnly = true) Camera camera) {
        if (!useOcclusionCulling || CameraOcclusionPolicy.shouldDisableSectionOcclusion(camera)) return false;
        Freecam freecam = Modules.get(Freecam.class);
        return freecam == null || !freecam.isEnabled() || freecam.camEntity == null;
    }

    @Inject(method = "setupTerrain(Lnet/minecraft/client/Camera;Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;Lnet/caffeinemc/mods/sodium/client/util/FogParameters;ZZLorg/joml/Matrix4f;)V", at = @At("HEAD"), remap = false)
    private void combatant$primaryVisibilityPending(Camera camera, Viewport viewport, FogParameters fog,
                                                    boolean useOcclusionCulling, boolean updateChunksImmediately,
                                                    org.joml.Matrix4f projection, CallbackInfo ci) {
        CombatantRenderSystem.sodium().markPrimaryVisibilityPending();
    }

    @Inject(method = "setupTerrain(Lnet/minecraft/client/Camera;Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;Lnet/caffeinemc/mods/sodium/client/util/FogParameters;ZZLorg/joml/Matrix4f;)V", at = @At("RETURN"), remap = false)
    private void combatant$primaryVisibilityReady(Camera camera, Viewport viewport, FogParameters fog,
                                                  boolean useOcclusionCulling, boolean updateChunksImmediately,
                                                  org.joml.Matrix4f projection, CallbackInfo ci) {
        CombatantRenderSystem.sodium().markPrimaryVisibilityReady();
    }
    /**
     * Sodium snapshots the projection in its own GameRenderer mixin and later passes that
     * snapshot through ChunkRenderMatrices. Combatant jitters the RenderSystem projection at
     * the same call site, so relying on wrapper order can leave terrain and vanilla/entity
     * rendering in different projection spaces. Replace only the raster matrix here with the
     * exact effective projection already published to the vanilla path. Culling stays stable.
     */
    @ModifyVariable(
            method = "renderLayer(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;DDDLnet/caffeinemc/mods/sodium/client/util/FogParameters;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/commands/RenderPass;Lnet/minecraft/client/renderer/oit/OitStage;)V",
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

    @Inject(method = "renderLayer(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;DDDLnet/caffeinemc/mods/sodium/client/util/FogParameters;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/commands/RenderPass;Lnet/minecraft/client/renderer/oit/OitStage;)V", at = @At("HEAD"), remap = false)
    private void combatant$beforeTerrainLayer(ChunkRenderMatrices matrices,
                                              TerrainRenderPass pass,
                                              double cameraX,
                                              double cameraY,
                                              double cameraZ,
                                              FogParameters fog,
                                              GpuSampler sampler,
                                              RenderPass renderPass,
                                              OitStage oitStage,
                                              CallbackInfo ci) {
        CombatantRenderSystem.sodium().terrainInterop().beforeTerrainDraw(
                matrices, pass, cameraX, cameraY, cameraZ, fog, sampler);
    }

    @Inject(method = "renderLayer(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;DDDLnet/caffeinemc/mods/sodium/client/util/FogParameters;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/commands/RenderPass;Lnet/minecraft/client/renderer/oit/OitStage;)V", at = @At("RETURN"), remap = false)
    private void combatant$afterTerrainLayer(ChunkRenderMatrices matrices,
                                             TerrainRenderPass pass,
                                             double cameraX,
                                             double cameraY,
                                             double cameraZ,
                                             FogParameters fog,
                                             GpuSampler sampler,
                                             RenderPass renderPass,
                                             OitStage oitStage,
                                             CallbackInfo ci) {
        CombatantRenderSystem.sodium().terrainInterop().afterTerrainDraw(
                matrices, pass, cameraX, cameraY, cameraZ, fog, sampler);
    }
}
