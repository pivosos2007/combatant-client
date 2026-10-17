/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium.terrain;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;

/** Sodium terrain vertex extension used by Combatant deferred materials. */
public final class CombatantModelVertexType implements ChunkVertexType {
    public static final int STRIDE = 44;
    public static final int SURFACE_FLAGS_OFFSET = 20;
    public static final int MATERIAL_DATA_OFFSET = 24;
    public static final int MATERIAL_META_OFFSET = 28;
    public static final int TANGENT_OFFSET = 32;
    public static final int MATERIAL_SURFACE_OFFSET = 36;
    public static final int BASE_COLOR_OFFSET = 40;

    public static final VertexFormat VERTEX_FORMAT = VertexFormat.builder(0)
            .addAttribute("a_Position", GpuFormat.RG32_UINT)
            .addAttribute("a_Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("a_TexCoord", GpuFormat.RG16_UINT)
            .addAttribute("a_LightAndData", GpuFormat.RGBA8_UINT)
            .addAttribute(CombatantChunkMeshAttributes.SURFACE_FLAGS, GpuFormat.R32_UINT)
            .addAttribute(CombatantChunkMeshAttributes.MATERIAL_DATA, GpuFormat.R32_UINT)
            .addAttribute(CombatantChunkMeshAttributes.MATERIAL_META, GpuFormat.R32_UINT)
            .addAttribute(CombatantChunkMeshAttributes.TANGENT, GpuFormat.RGBA8_SNORM)
            .addAttribute(CombatantChunkMeshAttributes.MATERIAL_SURFACE, GpuFormat.R32_UINT)
            .addAttribute(CombatantChunkMeshAttributes.BASE_COLOR, GpuFormat.RGBA8_UNORM)
            .build();

    @Override
    public VertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    @Override
    public ChunkVertexEncoder getEncoder() {
        return new CombatantTerrainVertex(
                STRIDE,
                SURFACE_FLAGS_OFFSET,
                MATERIAL_DATA_OFFSET,
                MATERIAL_META_OFFSET,
                TANGENT_OFFSET,
                MATERIAL_SURFACE_OFFSET,
                BASE_COLOR_OFFSET
        );
    }
}
