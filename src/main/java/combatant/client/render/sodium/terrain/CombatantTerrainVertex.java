/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * The terrain vertex layout was implemented with reference to Sodium's
 * CompactChunkVertex packing and Iris-style additional terrain attributes.
 * See THIRD_PARTY_NOTICES.md for the scope of this design reference.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium.terrain;

import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.minecraft.util.Mth;
import org.lwjgl.system.MemoryUtil;

// Credit: based on Sodium's CompactChunkVertex packing, with the Iris-style idea of appending extra terrain attributes.
public final class CombatantTerrainVertex implements ChunkVertexEncoder {
    private static final int POSITION_MAX_VALUE = 1 << 20;
    private static final int TEXTURE_MAX_VALUE = 1 << 15;
    private static final float MODEL_ORIGIN = 8.0f;
    private static final float MODEL_RANGE = 32.0f;

    private final int stride;
    private final int surfaceFlagsOffset;

    public CombatantTerrainVertex(int stride, int surfaceFlagsOffset) {
        this.stride = stride;
        this.surfaceFlagsOffset = surfaceFlagsOffset;
    }

    private static int packPositionHi(int x, int y, int z) {
        return (((x >>> 10) & 0x3FF) << 0) |
                (((y >>> 10) & 0x3FF) << 10) |
                (((z >>> 10) & 0x3FF) << 20);
    }

    private static int packPositionLo(int x, int y, int z) {
        return ((x & 0x3FF) << 0) |
                ((y & 0x3FF) << 10) |
                ((z & 0x3FF) << 20);
    }

    private static int quantizePosition(float position) {
        return ((int) (normalizePosition(position) * POSITION_MAX_VALUE)) & 0xFFFFF;
    }

    private static float normalizePosition(float v) {
        return (MODEL_ORIGIN + v) / MODEL_RANGE;
    }

    private static int packTexture(int u, int v) {
        return ((u & 0xFFFF) << 0) | ((v & 0xFFFF) << 16);
    }

    private static int encodeTexture(float center, float x) {
        int bias = (x < center) ? 1 : -1;
        int quantized = Math.round(x * TEXTURE_MAX_VALUE) + bias;
        return (quantized & 0x7FFF) | (sign(bias) << 15);
    }

    private static int encodeLight(int light) {
        int sky = Mth.clamp(((light >>> 16) & 0xFF) + 8, 8, 248);
        int block = Mth.clamp(((light >>> 0) & 0xFF) + 8, 8, 248);
        return (block << 0) | (sky << 8);
    }

    private static int packLightAndData(int light, int material, int section) {
        return ((light & 0xFFFF) << 0) |
                ((material & 0xFF) << 16) |
                ((section & 0xFF) << 24);
    }

    private static int sign(int x) {
        return (x >>> 31);
    }

    @Override
    public long write(long ptr, int materialBits, Vertex[] vertices, int sectionIndex) {
        float texCentroidU = 0.0f;
        float texCentroidV = 0.0f;

        for (var vertex : vertices) {
            texCentroidU += vertex.u;
            texCentroidV += vertex.v;
        }

        texCentroidU *= 0.25f;
        texCentroidV *= 0.25f;

        for (int i = 0; i < 4; i++) {
            var vertex = vertices[i];

            int x = quantizePosition(vertex.x);
            int y = quantizePosition(vertex.y);
            int z = quantizePosition(vertex.z);

            int u = encodeTexture(texCentroidU, vertex.u);
            int v = encodeTexture(texCentroidV, vertex.v);
            int light = encodeLight(vertex.light);

            MemoryUtil.memPutInt(ptr, packPositionHi(x, y, z));
            MemoryUtil.memPutInt(ptr + 4L, packPositionLo(x, y, z));
            MemoryUtil.memPutInt(ptr + 8L, ColorARGB.mulRGB(vertex.color, vertex.ao));
            MemoryUtil.memPutInt(ptr + 12L, packTexture(u, v));
            MemoryUtil.memPutInt(ptr + 16L, packLightAndData(light, materialBits, sectionIndex));
            MemoryUtil.memPutInt(ptr + this.surfaceFlagsOffset, ((CombatantChunkVertexExtension) vertex).combatant$getSurfaceFlags());

            ptr += this.stride;
        }

        return ptr;
    }
}
