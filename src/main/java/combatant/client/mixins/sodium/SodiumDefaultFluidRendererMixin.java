/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.sodium;

import combatant.client.render.engine.material.MaterialClassification;
import combatant.client.render.engine.material.MaterialRegistry;
import combatant.client.render.engine.material.MaterialSurfaceDescriptor;
import combatant.client.render.sodium.terrain.CombatantChunkVertexExtension;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
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

    @Inject(
            method = "writeQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;isTranslucent()Z",
                    shift = At.Shift.BEFORE
            ),
            remap = false
    )
    private void combatant$stampFluidMaterial(ChunkModelBuilder builder,
                                              TranslucentGeometryCollector collector,
                                              Material sodiumMaterial,
                                              BlockPos renderOrigin,
                                              ModelQuadView quad,
                                              ModelQuadFacing facing,
                                              boolean reversed,
                                              CallbackInfo ci) {
        combatant.client.render.sodium.fluid.FluidSurfaceContext.Entry context = combatant.client.render.sodium.fluid.FluidSurfaceContext.current();
        if (context == null || quad == null) return;

        // The material producer and Combatant's vertex encoder form one contract. Iris owns the
        // Sodium terrain format while its pipeline is active, so ordinary Sodium vertices do not
        // carry this extension. Keep this boundary fail-safe in case another renderer changes the
        // mixin composition independently of CombatantMixinPlugin.
        for (ChunkVertexEncoder.Vertex vertex : vertices) {
            if (!(vertex instanceof CombatantChunkVertexExtension)) return;
        }

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

    }
}
