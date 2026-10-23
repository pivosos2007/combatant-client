/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.schematic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-baked ghost geometry of one block state: the real model quads (position, uv, shade, biome tint, cull face)
 * in block-local space plus the collision-shape boxes used for outlines and the flat fallback.
 *
 * <p>Built once per state and reused by every box that shows it, so a frame only copies floats into the mesh.</p>
 */
final class SchematicGhostModel {

    /** Direction ordinals: DOWN 0, UP 1, NORTH 2, SOUTH 3, WEST 4, EAST 5. */
    static final SchematicGhostModel UNIT_CUBE = new SchematicGhostModel(
            0, new float[0], new float[0], new int[0], new Identifier[0], new byte[0],
            new float[]{0f, 0f, 0f, 1f, 1f, 1f}, true);

    private static final float EPS = 1.0E-4f;
    private static final Map<BlockState, SchematicGhostModel> CACHE = new IdentityHashMap<>();

    final int quadCount;
    /** 12 floats per quad: four xyz corners. */
    final float[] pos;
    /** 8 floats per quad: four uv pairs. */
    final float[] uv;
    /** Per-quad multiplicative RGB (biome tint x directional shade), 0xRRGGBB. */
    final int[] rgb;
    final Identifier[] atlas;
    /** Direction ordinal when the quad lies flat on a block boundary, else -1. */
    final byte[] cullFace;
    /** 6 floats per box: min xyz, max xyz. */
    final float[] shape;
    final boolean fullCube;

    private SchematicGhostModel(int quadCount, float[] pos, float[] uv, int[] rgb, Identifier[] atlas,
                                byte[] cullFace, float[] shape, boolean fullCube) {
        this.quadCount = quadCount;
        this.pos = pos;
        this.uv = uv;
        this.rgb = rgb;
        this.atlas = atlas;
        this.cullFace = cullFace;
        this.shape = shape;
        this.fullCube = fullCube;
    }

    static void clearCache() {
        CACHE.clear();
    }

    static SchematicGhostModel of(BlockState state, BlockPos pos) {
        SchematicGhostModel cached = CACHE.get(state);
        if (cached != null) return cached;
        SchematicGhostModel built = build(state, pos);
        CACHE.put(state, built);
        return built;
    }

    private static SchematicGhostModel build(BlockState state, BlockPos at) {
        float[] shape = shapeOf(state);
        boolean full = shape.length == 6 && shape[0] <= EPS && shape[1] <= EPS && shape[2] <= EPS
                && shape[3] >= 1f - EPS && shape[4] >= 1f - EPS && shape[5] >= 1f - EPS;

        Minecraft mc = Minecraft.getInstance();
        List<float[]> posList = new ArrayList<>();
        List<float[]> uvList = new ArrayList<>();
        List<Integer> rgbList = new ArrayList<>();
        List<Identifier> atlasList = new ArrayList<>();
        List<Byte> cullList = new ArrayList<>();
        try {
            BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
            if (model != null) {
                List<BlockStateModelPart> parts = new ArrayList<>();
                model.collectParts(RandomSource.create(0L), parts);
                for (BlockStateModelPart part : parts) {
                    for (int face = 0; face <= 6; face++) {
                        Direction side = face < 6 ? Direction.values()[face] : null;
                        for (BakedQuad quad : part.getQuads(side)) {
                            float[] p = new float[12];
                            float[] t = new float[8];
                            for (int i = 0; i < 4; i++) {
                                Vector3fc v = quad.position(i);
                                p[i * 3] = v.x();
                                p[i * 3 + 1] = v.y();
                                p[i * 3 + 2] = v.z();
                                long packed = quad.packedUV(i);
                                t[i * 2] = UVPair.unpackU(packed);
                                t[i * 2 + 1] = UVPair.unpackV(packed);
                            }
                            posList.add(p);
                            uvList.add(t);
                            atlasList.add(quad.materialInfo().sprite().atlasLocation());
                            cullList.add((byte) boundaryFace(p));
                            rgbList.add(quadRgb(mc, state, at, quad, p));
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            posList.clear();
            uvList.clear();
            rgbList.clear();
            atlasList.clear();
            cullList.clear();
        }

        int n = posList.size();
        float[] pos = new float[n * 12];
        float[] uv = new float[n * 8];
        int[] rgb = new int[n];
        byte[] cull = new byte[n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(posList.get(i), 0, pos, i * 12, 12);
            System.arraycopy(uvList.get(i), 0, uv, i * 8, 8);
            rgb[i] = rgbList.get(i);
            cull[i] = cullList.get(i);
        }
        return new SchematicGhostModel(n, pos, uv, rgb, atlasList.toArray(new Identifier[0]), cull,
                shape, full);
    }

    private static float[] shapeOf(BlockState state) {
        try {
            List<AABB> boxes = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs();
            if (!boxes.isEmpty()) {
                float[] out = new float[boxes.size() * 6];
                for (int i = 0; i < boxes.size(); i++) {
                    AABB b = boxes.get(i);
                    out[i * 6] = (float) b.minX;
                    out[i * 6 + 1] = (float) b.minY;
                    out[i * 6 + 2] = (float) b.minZ;
                    out[i * 6 + 3] = (float) b.maxX;
                    out[i * 6 + 4] = (float) b.maxY;
                    out[i * 6 + 5] = (float) b.maxZ;
                }
                return out;
            }
        } catch (RuntimeException ignored) {
            // Shapes that need a real level fall back to a unit cube.
        }
        return new float[]{0f, 0f, 0f, 1f, 1f, 1f};
    }

    /** Direction ordinal of the block face this quad lies flat on, or -1. */
    private static int boundaryFace(float[] p) {
        for (int axis = 0; axis < 3; axis++) {
            float c = p[axis];
            if (Math.abs(p[3 + axis] - c) > EPS || Math.abs(p[6 + axis] - c) > EPS || Math.abs(p[9 + axis] - c) > EPS) continue;
            boolean high = Math.abs(c - 1f) <= EPS;
            if (!high && Math.abs(c) > EPS) continue;
            return switch (axis) {
                case 0 -> high ? Direction.EAST.ordinal() : Direction.WEST.ordinal();
                case 1 -> high ? Direction.UP.ordinal() : Direction.DOWN.ordinal();
                default -> high ? Direction.SOUTH.ordinal() : Direction.NORTH.ordinal();
            };
        }
        return -1;
    }

    private static int quadRgb(Minecraft mc, BlockState state, BlockPos at, BakedQuad quad, float[] p) {
        // Outward normal from the quad's first triangle, flipped to point away from the block centre.
        float ax = p[3] - p[0], ay = p[4] - p[1], az = p[5] - p[2];
        float bx = p[9] - p[0], by = p[10] - p[1], bz = p[11] - p[2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float cx = (p[0] + p[3] + p[6] + p[9]) * 0.25f - 0.5f;
        float cy = (p[1] + p[4] + p[7] + p[10]) * 0.25f - 0.5f;
        float cz = (p[2] + p[5] + p[8] + p[11]) * 0.25f - 0.5f;
        if (nx * cx + ny * cy + nz * cz < 0f) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        float shade = 1f;
        if (len > 1.0E-6f) {
            nx /= len;
            ny /= len;
            nz /= len;
            shade = ny > 0.5f ? 1f : ny < -0.5f ? 0.55f : Math.abs(nz) >= Math.abs(nx) ? 0.82f : 0.66f;
        }
        int r = 255, g = 255, b = 255;
        if (quad.materialInfo().isTinted() && mc.getBlockColors() != null && mc.level != null) {
            var source = mc.getBlockColors().getTintSource(state, quad.materialInfo().tintIndex());
            if (source != null) {
                int c = source.colorInWorld(state, mc.level, at);
                r = (c >>> 16) & 255;
                g = (c >>> 8) & 255;
                b = c & 255;
            }
        }
        return (Math.round(r * shade) << 16) | (Math.round(g * shade) << 8) | Math.round(b * shade);
    }
}
