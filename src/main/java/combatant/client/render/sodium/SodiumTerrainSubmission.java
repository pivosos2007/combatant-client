/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium;

import com.mojang.blaze3d.textures.GpuSampler;
import combatant.client.render.engine.material.MaterialDomain;
import combatant.client.render.engine.scene.SceneDrawClass;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;

import java.util.Objects;

/** Exact producer-side terrain draw contract captured at SodiumWorldRenderer.renderLayer. */
public record SodiumTerrainSubmission(SceneDrawClass drawClass,
                                      MaterialDomain materialDomain,
                                      ChunkRenderMatrices matrices,
                                      TerrainRenderPass terrainPass,
                                      double cameraX,
                                      double cameraY,
                                      double cameraZ,
                                      FogParameters fog,
                                      GpuSampler sampler) {
    public SodiumTerrainSubmission {
        drawClass = Objects.requireNonNull(drawClass, "drawClass");
        materialDomain = Objects.requireNonNull(materialDomain, "materialDomain");
        matrices = Objects.requireNonNull(matrices, "matrices");
        terrainPass = Objects.requireNonNull(terrainPass, "terrainPass");
        fog = Objects.requireNonNull(fog, "fog");
        sampler = Objects.requireNonNull(sampler, "sampler");
    }
}
