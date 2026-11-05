/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.FilterMode;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.deferred.DeferredWorldPipeline;
import combatant.client.render.engine.deferred.DevDeferredRuntime;
import combatant.client.render.sodium.SodiumSecondaryTerrainContext;
import combatant.client.render.sodium.SodiumWorldVisibilityView;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.AABB;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures Sodium's exact terrain pass boundary without cancelling or replacing the production draw.
 */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class DevDeferredSodiumWorldRendererMixin implements SodiumWorldVisibilityView {
    @Shadow(remap = false)
    public abstract boolean isBoxVisible(double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ);

    @Override
    public boolean combatant$isBoxVisible(AABB box) {
        return isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    @Inject(method = "setupTerrain(Lnet/minecraft/client/Camera;Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;Lnet/caffeinemc/mods/sodium/client/util/FogParameters;ZZLorg/joml/Matrix4f;)V", at = @At("HEAD"), remap = false)
    private void combatant$primaryVisibilityPending(Camera camera,
                                                    Viewport viewport,
                                                    FogParameters fog,
                                                    boolean useOcclusionCulling,
                                                    boolean updateChunksImmediately,
                                                    org.joml.Matrix4f projection,
                                                    CallbackInfo ci) {
        CombatantRenderSystem.sodium().markPrimaryVisibilityPending();
    }

    @Inject(method = "setupTerrain(Lnet/minecraft/client/Camera;Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;Lnet/caffeinemc/mods/sodium/client/util/FogParameters;ZZLorg/joml/Matrix4f;)V", at = @At("RETURN"), remap = false)
    private void combatant$primaryVisibilityReady(Camera camera,
                                                  Viewport viewport,
                                                  FogParameters fog,
                                                  boolean useOcclusionCulling,
                                                  boolean updateChunksImmediately,
                                                  org.joml.Matrix4f projection,
                                                  CallbackInfo ci) {
        CombatantRenderSystem.sodium().markPrimaryVisibilityReady();
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
        if (SodiumSecondaryTerrainContext.active()) return;
        if (pass == DefaultTerrainRenderPasses.TRANSLUCENT) {
            DevDeferredRuntime.world().beforeTranslucency(
                    pass.getTarget().getColorTextureView(),
                    pass.getTarget().getDepthTextureView()
            );
        } else {
            DevDeferredRuntime.world().beforeTerrainSubmission();
        }
    }

    /**
     * Own Sodium's once-per-frame terrain UBO at the exact update boundary.
     *
     * <p>Combatant can recursively submit arbitrary secondary cameras before any primary terrain
     * layer. The transaction is therefore keyed to logical view ownership, not to SOLID/shadow/
     * reflection ordering. DynamicUniformStorage keeps previously returned slices alive until
     * Sodium endFrame(), so re-arming update() allocates/reuses a value-correct slice without
     * overwriting an already submitted draw.</p>
     */
    @WrapOperation(
            method = "renderLayer(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;DDDLnet/caffeinemc/mods/sodium/client/util/FogParameters;Lcom/mojang/blaze3d/textures/GpuSampler;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/UniformBufferManager;update(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/util/FogParameters;)V"
            ),
            remap = false
    )
    private void combatant$terrainUniformTransaction(UniformBufferManager manager,
                                                      ChunkRenderMatrices matrices,
                                                      FogParameters fog,
                                                      Operation<Void> original) {
        SodiumSecondaryTerrainContext.State secondary = SodiumSecondaryTerrainContext.current();
        boolean reload = secondary != null
                ? secondary.beginTerrainUniformUpdate()
                : SodiumSecondaryTerrainContext.primaryUniformsDirty();
        if (reload) manager.prepareFrame();

        boolean success = false;
        try {
            original.call(manager, matrices, fog);
            success = true;
        } finally {
            if (secondary != null) {
                secondary.finishTerrainUniformUpdate(reload, success);
            } else {
                SodiumSecondaryTerrainContext.finishPrimaryUniformUpdate(reload, success);
            }
        }
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
        if (SodiumSecondaryTerrainContext.active()) return;
        if (pass == DefaultTerrainRenderPasses.CUTOUT && DevDeferredRuntime.world().enabled()) {
            DeferredWorldPipeline.LightingState lighting = new DeferredWorldPipeline.LightingState(
                    fog.red(), fog.green(), fog.blue(), fog.alpha(),
                    fog.environmentalStart(), fog.environmentalEnd(),
                    fog.renderStart(), fog.renderEnd()
            );
            DevDeferredRuntime.world().resolveLighting(
                    pass.getTarget().getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
                    lighting
            );
        }
    }
    @WrapOperation(
            method = "renderLayer(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;DDDLnet/caffeinemc/mods/sodium/client/util/FogParameters;Lcom/mojang/blaze3d/textures/GpuSampler;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;getRenderLists()Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/SortedRenderLists;"
            ),
            remap = false
    )
    private SortedRenderLists combatant$secondaryRenderLists(RenderSectionManager manager,
                                                              Operation<SortedRenderLists> original) {
        SodiumSecondaryTerrainContext.State secondary = SodiumSecondaryTerrainContext.current();
        return secondary != null ? secondary.renderLists(manager) : original.call(manager);
    }

}
