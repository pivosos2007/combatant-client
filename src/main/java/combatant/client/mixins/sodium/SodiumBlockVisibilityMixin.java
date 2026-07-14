/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.util.block.BlockObservationHub;

import java.util.ArrayDeque;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer")
public abstract class SodiumBlockVisibilityMixin {
    @Unique
    private static final ThreadLocal<ArrayDeque<RenderedBlock>> COMBATANT_BLOCK_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "renderModel", at = @At("HEAD"), remap = false)
    private void combatant$pushRenderedBlock(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        COMBATANT_BLOCK_STACK.get().addLast(new RenderedBlock(pos, state));
    }

    @Inject(method = "renderModel", at = @At("RETURN"), remap = false)
    private void combatant$popRenderedBlock(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        ArrayDeque<RenderedBlock> stack = COMBATANT_BLOCK_STACK.get();
        if (!stack.isEmpty()) {
            stack.removeLast();
        }
        if (stack.isEmpty()) {
            COMBATANT_BLOCK_STACK.remove();
        }
    }

    @Inject(method = "bufferQuad", at = @At("HEAD"), remap = false)
    private void combatant$observeBufferedQuad(MutableQuadViewImpl quad,
                                               float[] brightness,
                                               Material material,
                                               CallbackInfo ci) {
        ArrayDeque<RenderedBlock> stack = COMBATANT_BLOCK_STACK.get();
        if (stack.isEmpty()) {
            return;
        }

        RenderedBlock rendered = stack.peekLast();
        BlockObservationHub.observeSodiumRenderedBlock(rendered.pos(), rendered.state());
    }

    private record RenderedBlock(BlockPos pos, BlockState state) {
    }
}
