/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.seedcracker;

import combatant.client.compat.seedcracker.SeedCrackerRuntime;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostic hook: observes the original finder's actual chunk submissions. */
@Pseudo
@Mixin(targets = "kaptainwutax.seedcrackerX.finder.FinderQueue", remap = false)
public abstract class SeedCrackerFinderQueueMixin {
    @Inject(method = "onChunkData", at = @At("HEAD"), require = 0, remap = false)
    private void combatant$chunkReceived(Level world, ChunkPos pos, CallbackInfo ci) {
        SeedCrackerRuntime.onUpstreamChunk(pos);
    }
}
