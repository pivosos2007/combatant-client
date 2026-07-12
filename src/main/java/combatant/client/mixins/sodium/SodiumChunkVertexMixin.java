/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.render.sodium.terrain.CombatantChunkVertexExtension;

@Mixin(ChunkVertexEncoder.Vertex.class)
public abstract class SodiumChunkVertexMixin implements CombatantChunkVertexExtension {
    @Unique
    private int combatant$surfaceFlags;

    @Inject(method = "copyVertexTo", at = @At("HEAD"))
    private static void combatant$copyVertexData(ChunkVertexEncoder.Vertex from, ChunkVertexEncoder.Vertex to, CallbackInfo ci) {
        ((CombatantChunkVertexExtension) from).combatant$copyData((CombatantChunkVertexExtension) to);
    }

    @Override
    public void combatant$setSurfaceFlags(int surfaceFlags) {
        this.combatant$surfaceFlags = surfaceFlags;
    }

    @Override
    public int combatant$getSurfaceFlags() {
        return this.combatant$surfaceFlags;
    }

    @Override
    public void combatant$copyData(CombatantChunkVertexExtension dest) {
        dest.combatant$setSurfaceFlags(this.combatant$surfaceFlags);
    }
}
