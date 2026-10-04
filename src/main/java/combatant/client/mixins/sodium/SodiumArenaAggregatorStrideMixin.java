/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import com.mojang.blaze3d.vertex.VertexFormat;
import combatant.client.render.sodium.terrain.CombatantChunkMeshFormats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sodium 0.9.2 sizes its geometry arena from {@code ChunkMeshFormats.COMPACT} instead of
 * {@code getCurrent()}. With Combatant's wider terrain vertex the region upload then dies with
 * "Unsupported stride: 44", so the arena has to learn the extended stride. Sodium 0.9.1 has no
 * ArenaAggregator, hence {@link Pseudo}.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gpu.arena.ArenaAggregator", remap = false)
public abstract class SodiumArenaAggregatorStrideMixin {
    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexFormat;getVertexSize()I"),
            remap = false
    )
    private int combatant$extendedGeometryStride(VertexFormat compactFormat) {
        return CombatantChunkMeshFormats.SURFACE_FLAGS.getVertexFormat().getVertexSize();
    }
}
