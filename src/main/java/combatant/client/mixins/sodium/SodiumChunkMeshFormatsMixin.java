/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import combatant.client.render.sodium.terrain.CombatantChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium routes the vertex format used by the renderer and builder through this
 * method. Replacing it here keeps both on Combatant's extended terrain layout.
 * Region arenas size themselves from {@code COMPACT} directly in 0.9.2;
 * {@link SodiumArenaAggregatorStrideMixin} covers that.
 */
@Mixin(ChunkMeshFormats.class)
public abstract class SodiumChunkMeshFormatsMixin {
    @Inject(method = "getCurrent", at = @At("HEAD"), cancellable = true, remap = false)
    private static void combatant$useExtendedVertexFormat(CallbackInfoReturnable<ChunkVertexType> cir) {
        cir.setReturnValue(CombatantChunkMeshFormats.SURFACE_FLAGS);
    }
}
