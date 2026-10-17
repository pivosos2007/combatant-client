/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import com.llamalad7.mixinextras.sugar.Local;
import combatant.client.render.engine.material.MaterialClassification;
import combatant.client.render.engine.material.MaterialClassifier;
import combatant.client.render.engine.material.MaterialDomain;
import combatant.client.render.engine.material.MaterialRegistry;
import combatant.client.render.engine.material.MaterialSurfaceDescriptor;
import combatant.client.render.helpers.SodiumSurfaceFlagContext;
import combatant.client.util.block.BlockObservationHub;
import combatant.client.render.sodium.fluid.FluidSurfaceMetadataCapture;
import combatant.client.render.sodium.terrain.CombatantChunkVertexExtension;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteFinderCache;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer")
public abstract class SodiumBlockRendererMixin {

    @Unique
    private static final ThreadLocal<ArrayDeque<RenderedBlock>> COMBATANT_BLOCK_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);
    @Unique
    private static final ThreadLocal<ArrayDeque<MetadataEntry>> COMBATANT_METADATA_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);


    @Inject(method = "renderModel", at = @At("HEAD"), remap = false)
    private void combatant$pushSurfaceState(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        SodiumSurfaceFlagContext.pushForState(state, pos, origin);
        COMBATANT_BLOCK_STACK.get().addLast(new RenderedBlock(pos, state));
        COMBATANT_METADATA_STACK.get().addLast(new MetadataEntry(
                state,
                pos == null ? null : pos.immutable(),
                origin == null ? null : origin.immutable()
        ));
    }

    @Inject(method = "renderModel", at = @At("RETURN"), remap = false)
    private void combatant$popSurfaceState(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        SodiumSurfaceFlagContext.pop();
        ArrayDeque<RenderedBlock> stack = COMBATANT_BLOCK_STACK.get();
        if (!stack.isEmpty()) stack.removeLast();
        if (stack.isEmpty()) COMBATANT_BLOCK_STACK.remove();
        ArrayDeque<MetadataEntry> metadataStack = COMBATANT_METADATA_STACK.get();
        if (!metadataStack.isEmpty()) metadataStack.removeLast();
        if (metadataStack.isEmpty()) COMBATANT_METADATA_STACK.remove();
    }

    @Inject(
            method = "bufferQuad",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;light:I",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER),
            remap = false
    )
    private void combatant$writeSurfaceContract(MutableQuadViewImpl quad,
                                                 float[] brightness,
                                                 Material material,
                                                 CallbackInfo ci,
                                                 @Local(ordinal = 0) ChunkVertexEncoder.Vertex vertex) {
        ArrayDeque<RenderedBlock> stack = COMBATANT_BLOCK_STACK.get();
        if (!stack.isEmpty()) {
            RenderedBlock rendered = stack.peekLast();
            BlockObservationHub.observeSodiumRenderedBlock(rendered.pos(), rendered.state());
        }
        CombatantChunkVertexExtension extension = (CombatantChunkVertexExtension) vertex;
        extension.combatant$setSurfaceFlags(SodiumSurfaceFlagContext.getSurfaceFlags(vertex.y));

        TextureAtlasSprite sprite = quad.sprite(SpriteFinderCache.forBlockAtlas());
        MaterialClassification classification = SodiumSurfaceFlagContext.materialClassification(combatant$domain(material));
        MaterialSurfaceDescriptor descriptor = MaterialRegistry.global().resolve(sprite, classification);
        extension.combatant$setMaterialData(
                descriptor.stableId(),
                MaterialRegistry.global().gpuPresenceMask(descriptor),
                descriptor.gpuFeatureMask16(),
                descriptor.packScalarSurface()
        );
    }


    private static MaterialDomain combatant$domain(Material material) {
        if (material == null || material.pass == null) return MaterialDomain.UNKNOWN;
        if (material.pass == DefaultTerrainRenderPasses.SOLID) return MaterialDomain.OPAQUE;
        if (material.pass == DefaultTerrainRenderPasses.CUTOUT) return MaterialDomain.CUTOUT;
        if (material.pass == DefaultTerrainRenderPasses.TRANSLUCENT) return MaterialDomain.TRANSLUCENT;
        return MaterialDomain.UNKNOWN;
    }

    @Inject(
            method = "bufferQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;isTranslucent()Z",
                    shift = At.Shift.BEFORE
            ),
            remap = false
    )
    private void combatant$captureHeightMetadata(MutableQuadViewImpl quad,
                                                  float[] brightness,
                                                  Material material,
                                                  CallbackInfo ci) {
        ArrayDeque<MetadataEntry> stack = COMBATANT_METADATA_STACK.get();
        MetadataEntry entry = stack.isEmpty() ? null : stack.peekLast();
        if (entry == null || entry.worldPos() == null || quad == null) return;
        TextureAtlasSprite sprite = quad.sprite(SpriteFinderCache.forBlockAtlas());
        MaterialClassification classification = MaterialClassifier.classifyBlock(entry.state(), combatant$domain(material));
        MaterialSurfaceDescriptor descriptor = MaterialRegistry.global().resolve(sprite, classification);
        FluidSurfaceMetadataCapture.captureHeight(entry.worldPos(), descriptor);
    }

    @Unique
    private record RenderedBlock(BlockPos pos, BlockState state) {
    }

    @Unique
    private record MetadataEntry(BlockState state, BlockPos worldPos, BlockPos renderOrigin) {
    }
}
