/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.uniform.MeshBuilder;

import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryUtil.memAddress0;
import static org.lwjgl.system.MemoryUtil.memGetByte;
import static org.lwjgl.system.MemoryUtil.memGetFloat;
import static org.lwjgl.system.MemoryUtil.memGetInt;

/**
 * Converts legacy DEBUG_LINES world meshes into screen-space wide-line triangle meshes.
 *
 * <p>Modern GL core profiles do not guarantee line widths above 1px. The renderer therefore keeps native line
 * primitives for thin 1px lines only and expands configurable thick outlines into quads before the mesh is uploaded.
 * The generated vertex format is consumed by {@code shaders/wide_line.vert}, which performs the final clip-space
 * offset using the active projection/model-view matrices and framebuffer size.</p>
 */
public enum WorldWideLineMeshCompiler {
    ;

    private static final float WIDE_LINE_EPSILON = 1.01f;
    private static final int POSITION_OFFSET = 0;
    private static final int COLOR_OFFSET = 12;
    private static final int MIN_POSITION_COLOR_STRIDE = 16;

    public static boolean shouldExpand(WorldDrawCommand command) {
        return command != null
                && command.lineMode()
                && command.lineWidth() >= WIDE_LINE_EPSILON
                && widePipeline(command.pipeline()) != null;
    }

    public static RenderPipeline widePipeline(RenderPipeline source) {
        if (source == CombatantRenderPipelines.WORLD_COLORED_LINES) {
            return CombatantRenderPipelines.WORLD_WIDE_COLORED_LINES;
        }
        if (source == CombatantRenderPipelines.WORLD_COLORED_LINES_DEPTH) {
            return CombatantRenderPipelines.WORLD_WIDE_COLORED_LINES_DEPTH;
        }
        if (source == CombatantRenderPipelines.WORLD_COLORED_LINES_LIQUID_BLEND) {
            return CombatantRenderPipelines.WORLD_WIDE_COLORED_LINES_LIQUID_BLEND;
        }
        if (source == CombatantRenderPipelines.WORLD_COLORED_LINES_LIQUID_IGNORE) {
            return CombatantRenderPipelines.WORLD_WIDE_COLORED_LINES_LIQUID_IGNORE;
        }
        return null;
    }

    public static MeshBuilder compile(WorldDrawCommand command) {
        MeshBuilder source = command.mesh();
        if (source.isBuilding()) source.end();

        RenderPipeline pipeline = widePipeline(command.pipeline());
        if (pipeline == null) return null;

        int lineCount = source.getIndicesCount() / 2;
        MeshBuilder out = new MeshBuilder(pipeline);
        out.beginWorld(new net.minecraft.world.phys.Vec3(source.cameraAnchorX(), 0.0, source.cameraAnchorZ()));
        out.reserve(Math.max(4, lineCount * 4), Math.max(6, lineCount * 6));

        ByteBuffer vertices = source.vertexBufferView();
        ByteBuffer indices = source.indexBufferView();
        int stride = source.getVertexStride();
        if (stride < MIN_POSITION_COLOR_STRIDE) {
            out.close();
            return null;
        }

        long vertexBase = memAddress0(vertices);
        long indexBase = memAddress0(indices);
        int vertexCount = source.getVertexCount();
        float width = Math.max(1.0f, command.lineWidth());
        int validLines = 0;
        for (int i = 0; i + 1 < source.getIndicesCount(); i += 2) {
            int a = memGetInt(indexBase + (long) i * Integer.BYTES);
            int b = memGetInt(indexBase + (long) (i + 1) * Integer.BYTES);
            if (!validIndex(a, vertexCount) || !validIndex(b, vertexCount)) {
                continue;
            }
            appendLine(out, vertexBase, stride, source.cameraAnchorX(), source.cameraAnchorZ(), a, b, width);
            validLines++;
        }

        if (validLines <= 0) {
            out.close();
            return null;
        }

        out.end();
        return out;
    }

    private static void appendLine(MeshBuilder out,
                                   long vertices,
                                   int stride,
                                   double cameraX,
                                   double cameraZ,
                                   int a,
                                   int b,
                                   float width) {
        long aBase = vertices + (long) a * stride;
        long bBase = vertices + (long) b * stride;

        float ax = memGetFloat(aBase + POSITION_OFFSET);
        float ay = memGetFloat(aBase + POSITION_OFFSET + Float.BYTES);
        float az = memGetFloat(aBase + POSITION_OFFSET + Float.BYTES * 2L);
        float bx = memGetFloat(bBase + POSITION_OFFSET);
        float by = memGetFloat(bBase + POSITION_OFFSET + Float.BYTES);
        float bz = memGetFloat(bBase + POSITION_OFFSET + Float.BYTES * 2L);

        int ar = unsigned(memGetByte(aBase + COLOR_OFFSET));
        int ag = unsigned(memGetByte(aBase + COLOR_OFFSET + 1L));
        int ab = unsigned(memGetByte(aBase + COLOR_OFFSET + 2L));
        int aa = unsigned(memGetByte(aBase + COLOR_OFFSET + 3L));
        int br = unsigned(memGetByte(bBase + COLOR_OFFSET));
        int bg = unsigned(memGetByte(bBase + COLOR_OFFSET + 1L));
        int bb = unsigned(memGetByte(bBase + COLOR_OFFSET + 2L));
        int ba = unsigned(memGetByte(bBase + COLOR_OFFSET + 3L));

        out.ensureCapacity(4, 6);

        int aNeg = endpoint(out, ax + cameraX, ay, az + cameraZ, ar, ag, ab, aa, bx, by, bz, -width);
        int aPos = endpoint(out, ax + cameraX, ay, az + cameraZ, ar, ag, ab, aa, bx, by, bz, width);
        int bPos = endpoint(out, bx + cameraX, by, bz + cameraZ, br, bg, bb, ba, ax, ay, az, -width);
        int bNeg = endpoint(out, bx + cameraX, by, bz + cameraZ, br, bg, bb, ba, ax, ay, az, width);

        out.quad(aNeg, bNeg, bPos, aPos);
    }

    private static int endpoint(MeshBuilder out,
                                double x,
                                double y,
                                double z,
                                int r,
                                int g,
                                int b,
                                int a,
                                float otherX,
                                float otherY,
                                float otherZ,
                                float signedWidth) {
        return out.vec3(x, y, z)
                .color(r, g, b, a)
                .vec4(otherX, otherY, otherZ, signedWidth)
                .next();
    }

    private static boolean validIndex(int index, int vertexCount) {
        return index >= 0 && index < vertexCount;
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }
}
