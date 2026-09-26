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
import combatant.client.render.sodium.fluid.FluidSurfaceMetadataCapture;
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

/**
 * Non-invasive Sodium fluid observer. Captures producer-known fluid/material metadata without
 * cancelling quads or depending on Combatant's standalone terrain vertex format.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer")
public abstract class SodiumFluidMetadataCaptureMixin {
    @Shadow(remap = false)
    @Final
    private ChunkVertexEncoder.Vertex[] vertices;

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void combatant$pushFluidMetadata(LevelSlice level,
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
    private void combatant$popFluidMetadata(LevelSlice level,
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
            remap = false
    )
    private void combatant$captureFluidMetadata(ChunkModelBuilder builder,
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
        FluidSurfaceData surface = FluidSurfaceData.fromCurrent(
                quad, facing, reversed, descriptor, vertices, mapMask, featureMask, packedSurface
        );
        FluidSurfaceMetadataCapture.capture(surface);
    }
}
