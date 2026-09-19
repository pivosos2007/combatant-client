/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.sodium;

import combatant.client.render.engine.material.MaterialClassification;
import combatant.client.render.engine.material.MaterialRegistry;
import combatant.client.render.engine.material.MaterialSurfaceDescriptor;
import combatant.client.render.sodium.fluid.FluidSurfaceContext;
import combatant.client.render.sodium.fluid.FluidSurfaceData;
import combatant.client.render.sodium.fluid.WaterSurfaceExtractor;
import combatant.client.render.sodium.terrain.CombatantChunkVertexExtension;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds Combatant's exact fluid/material contract to Sodium's default quad producer. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer")
public abstract class SodiumDefaultFluidRendererMixin {
    @Shadow(remap = false)
    @Final
    private ChunkVertexEncoder.Vertex[] vertices;

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void combatant$pushFluidSurface(LevelSlice level,
                                            BlockState blockState,
                                            FluidState fluidState,
                                            BlockPos worldPos,
                                            BlockPos renderOrigin,
                                            TranslucentGeometryCollector collector,
                                            ChunkModelBuilder builder,
                                            Material material,
                                            ColorProvider<FluidState> colorProvider,
                                            FluidModel model,
                                            CallbackInfo ci) {
        FluidSurfaceContext.push(level, blockState, fluidState, worldPos, renderOrigin);
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void combatant$popFluidSurface(LevelSlice level,
                                           BlockState blockState,
                                           FluidState fluidState,
                                           BlockPos worldPos,
                                           BlockPos renderOrigin,
                                           TranslucentGeometryCollector collector,
                                           ChunkModelBuilder builder,
                                           Material material,
                                           ColorProvider<FluidState> colorProvider,
                                           FluidModel model,
                                           CallbackInfo ci) {
        FluidSurfaceContext.pop();
    }

    @Inject(
            method = "writeQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;isTranslucent()Z",
                    shift = At.Shift.BEFORE
            ),
            remap = false,
            cancellable = true
    )
    private void combatant$stampFluidMaterial(ChunkModelBuilder builder,
                                              TranslucentGeometryCollector collector,
                                              Material sodiumMaterial,
                                              BlockPos renderOrigin,
                                              ModelQuadView quad,
                                              ModelQuadFacing facing,
                                              boolean reversed,
                                              CallbackInfo ci) {
        FluidSurfaceContext.Entry context = FluidSurfaceContext.current();
        if (context == null || quad == null) return;

        TextureAtlasSprite sprite = quad.getSprite();
        MaterialClassification classification = context.classification();
        MaterialSurfaceDescriptor descriptor = MaterialRegistry.global().resolve(sprite, classification);
        int mapMask = MaterialRegistry.global().gpuPresenceMask(descriptor);
        int featureMask = descriptor.gpuFeatureMask16();
        int packedSurface = descriptor.packScalarSurface();
        for (ChunkVertexEncoder.Vertex vertex : vertices) {
            CombatantChunkVertexExtension extension = (CombatantChunkVertexExtension) vertex;
            extension.combatant$setSurfaceFlags(0);
            extension.combatant$setMaterialData(descriptor.stableId(), mapMask, featureMask, packedSurface);
        }

        FluidSurfaceData surface = FluidSurfaceData.fromCurrent(
                quad, facing, reversed, descriptor, vertices, mapMask, featureMask, packedSurface
        );
        WaterSurfaceExtractor.capture(surface);
        if (WaterSurfaceExtractor.replacementEligible(surface)
                && WaterSurfaceExtractor.currentBuildOwnsWaterReplacement()) {
            // Replacement ownership is captured once at section-build start, so every water quad
            // from this build is routed to exactly one renderer even if the global toggle changes.
            ci.cancel();
        }
    }
}
