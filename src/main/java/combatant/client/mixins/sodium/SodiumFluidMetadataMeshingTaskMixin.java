/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.sodium;

import combatant.client.render.sodium.fluid.FluidSurfaceMetadataCapture;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Publishes a metadata capture transaction for each Sodium section build; never changes meshing. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask")
public abstract class SodiumFluidMetadataMeshingTaskMixin {
    @Inject(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At("HEAD"),
            remap = false
    )
    private void combatant$beginFluidMetadata(ChunkBuildContext context,
                                               CancellationToken cancellationToken,
                                               CallbackInfoReturnable<ChunkBuildOutput> cir) {
        ChunkBuilderTask<?> task = (ChunkBuilderTask<?>)(Object)this;
        FluidSurfaceMetadataCapture.beginSection(task.getRenderSection().getPosition().asLong());
    }

    @Inject(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At("RETURN"),
            remap = false
    )
    private void combatant$finishFluidMetadata(ChunkBuildContext context,
                                                CancellationToken cancellationToken,
                                                CallbackInfoReturnable<ChunkBuildOutput> cir) {
        FluidSurfaceMetadataCapture.finishSection(cir.getReturnValue());
    }
}
