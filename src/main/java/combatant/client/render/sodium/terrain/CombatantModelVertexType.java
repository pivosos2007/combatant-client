/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium.terrain;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;

/**
 * Sodium 26.2 terrain vertex format extension.
 * <p>
 * Sodium now feeds terrain through Mojang RenderPipeline, so the old
 * GlVertexFormat/ChunkShaderBindingPoints path is gone. Attribute names must
 * match the GLSL input names exactly for Vulkan SPIR-V reflection.
 */
public final class CombatantModelVertexType implements ChunkVertexType {
    public static final int STRIDE = 24;
    public static final int SURFACE_FLAGS_OFFSET = 20;

    public static final VertexFormat VERTEX_FORMAT = VertexFormat.builder(0)
            .addAttribute("a_Position", GpuFormat.RG32_UINT)
            .addAttribute("a_Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("a_TexCoord", GpuFormat.RG16_UINT)
            .addAttribute("a_LightAndData", GpuFormat.RGBA8_UINT)
            .addAttribute(CombatantChunkMeshAttributes.SURFACE_FLAGS, GpuFormat.R32_UINT)
            .build();

    @Override
    public VertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    @Override
    public ChunkVertexEncoder getEncoder() {
        return new CombatantTerrainVertex(STRIDE, SURFACE_FLAGS_OFFSET);
    }
}
