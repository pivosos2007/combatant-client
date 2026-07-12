/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import combatant.client.render.sodium.terrain.CombatantChunkMeshFormats;

@Mixin(RenderSectionManager.class)
public abstract class SodiumRenderSectionManagerVertexFormatMixin {

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkMeshFormats;COMPACT:Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexType;",
                    ordinal = 0,
                    opcode = Opcodes.GETSTATIC),
            remap = false
    )
    private ChunkVertexType combatant$useExtendedVertexFormatForRenderer() {
        return CombatantChunkMeshFormats.SURFACE_FLAGS;
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkMeshFormats;COMPACT:Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexType;",
                    ordinal = 1,
                    opcode = Opcodes.GETSTATIC),
            remap = false
    )
    private ChunkVertexType combatant$useExtendedVertexFormatForBuilder() {
        return CombatantChunkMeshFormats.SURFACE_FLAGS;
    }
}
