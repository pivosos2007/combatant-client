/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import combatant.client.render.sodium.terrain.CombatantChunkMeshFormats;

@Mixin(RenderRegion.DeviceResources.class)
public abstract class SodiumRenderRegionArenasVertexFormatMixin {

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkMeshFormats;COMPACT:Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexType;",
                    opcode = Opcodes.GETSTATIC),
            remap = false
    )
    private ChunkVertexType combatant$useExtendedStride() {
        return CombatantChunkMeshFormats.SURFACE_FLAGS;
    }
}
