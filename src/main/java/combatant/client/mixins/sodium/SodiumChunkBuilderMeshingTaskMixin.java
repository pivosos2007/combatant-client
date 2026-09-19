/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.block.state.BlockState;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.util.block.BlockObservationHub;
import combatant.client.render.sodium.fluid.WaterSurfaceExtractor;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask")
public abstract class SodiumChunkBuilderMeshingTaskMixin {

    @Inject(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At("HEAD"),
            remap = false
    )
    private void combatant$beginSurfaceExtraction(ChunkBuildContext context,
                                                   CancellationToken cancellationToken,
                                                   CallbackInfoReturnable<ChunkBuildOutput> cir) {
        ChunkBuilderTask<?> task = (ChunkBuilderTask<?>)(Object)this;
        WaterSurfaceExtractor.beginSection(task.getRenderSection().getPosition().asLong());
    }

    @Inject(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At("RETURN"),
            remap = false
    )
    private void combatant$finishSurfaceExtraction(ChunkBuildContext context,
                                                    CancellationToken cancellationToken,
                                                    CallbackInfoReturnable<ChunkBuildOutput> cir) {
        WaterSurfaceExtractor.finishSection(cir.getReturnValue());
    }

    @ModifyVariable(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At("STORE"),
            ordinal = 0,
            remap = false
    )
    private BlockState combatant$captureSodiumSectionBlock(BlockState state,
                                                           @Local(index = 22) int x,
                                                           @Local(index = 20) int y,
                                                           @Local(index = 21) int z) {
        BlockObservationHub.observeSodiumBlock(x, y, z, state);
        return state;
    }
}
