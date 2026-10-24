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
public final class SchematicGhostModel {

    /** Fixed, world-independent tint of a tinted quad for previews that have no level to sample ({@code 0xAARRGGBB}). */
    @FunctionalInterface
    public interface QuadTint {
        int tint(BlockState state, BakedQuad quad);
    }

    /** Direction ordinals: DOWN 0, UP 1, NORTH 2, SOUTH 3, WEST 4, EAST 5. */
    static final SchematicGhostModel UNIT_CUBE = new SchematicGhostModel(
            0, new float[0], new float[0], new int[0], new Identifier[0], new byte[0],
            new float[0], new int[0], new float[]{0f, 0f, 0f, 1f, 1f, 1f}, true, (byte) 0);

    private static final float EPS = 1.0E-4f;
    /** Directional shade per direction ordinal (down, up, north, south, west, east). */
    static final float[] FACE_SHADE = {0.55f, 1.0f, 0.82f, 0.82f, 0.66f, 0.66f};
    private static final Map<BlockState, SchematicGhostModel> CACHE = new IdentityHashMap<>();

    public final int quadCount;
    /** 12 floats per quad: four xyz corners. */
    public final float[] pos;
    /** 8 floats per quad: four uv pairs (absolute atlas coordinates). */
    public final float[] uv;
    /** Per-quad multiplicative RGB (biome tint x directional shade), 0xRRGGBB. */
    final int[] rgb;
    public final Identifier[] atlas;
    /** Direction ordinal when the quad lies flat on a block boundary, else -1. */
    public final byte[] cullFace;
    /** 3 floats per quad: unit normal pointing out of the quad's front side. */
    public final float[] normal;
    /** Per-quad {@code 0xAARRGGBB} tint without any directional shade; filled only by {@link #bake}, white otherwise. */
    public final int[] tint;
    /** 6 floats per box: min xyz, max xyz. */
    final float[] shape;
    public final boolean fullCube;
    /** Bit per direction ordinal: touching faces of a neighbouring block of this same model are hidden by it. */
    final byte flush;

    private SchematicGhostModel(int quadCount, float[] pos, float[] uv, int[] rgb, Identifier[] atlas,
                                byte[] cullFace, float[] normal, int[] tint, float[] shape, boolean fullCube,
                                byte flush) {
        this.quadCount = quadCount;
        this.pos = pos;
        this.uv = uv;
        this.rgb = rgb;
        this.atlas = atlas;
        this.cullFace = cullFace;
        this.normal = normal;
        this.tint = tint;
        this.shape = shape;
        this.fullCube = fullCube;
        this.flush = flush;
    }

    static void clearCache() {
        CACHE.clear();
    }

    static SchematicGhostModel of(BlockState state, BlockPos pos) {
        SchematicGhostModel cached = CACHE.get(state);
        if (cached != null) return cached;
        SchematicGhostModel built = build(state, pos, null);
        CACHE.put(state, built);
        return built;
    }

    /**
     * Geometry of a state for screens without a level: the same baked quads the projection draws, with tints
     * resolved by {@code tint} instead of the world. Not cached here; the caller owns the result and drops it when
     * the model set changes.
     */
    public static SchematicGhostModel bake(BlockState state, QuadTint tint) {
        return build(state, null, tint);
    }

    private static SchematicGhostModel build(BlockState state, BlockPos at, QuadTint tintOf) {
        float[] shape = shapeOf(state);
        boolean full = shape.length == 6 && shape[0] <= EPS && shape[1] <= EPS && shape[2] <= EPS
                && shape[3] >= 1f - EPS && shape[4] >= 1f - EPS && shape[5] >= 1f - EPS;

        Minecraft mc = Minecraft.getInstance();
        List<float[]> posList = new ArrayList<>();
        List<float[]> uvList = new ArrayList<>();
        List<Integer> rgbList = new ArrayList<>();
        List<Identifier> atlasList = new ArrayList<>();
        List<Byte> cullList = new ArrayList<>();
        List<float[]> normalList = new ArrayList<>();
        List<Integer> tintList = new ArrayList<>();
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
                            float[] faceNormal = frontNormal(p, quad.direction());
                            normalList.add(faceNormal);
                            rgbList.add(quadRgb(mc, state, at, quad, faceNormal));
                            tintList.add(tintOf != null && quad.materialInfo().isTinted() ? tintOf.tint(state, quad) : 0xFFFFFFFF);
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
            normalList.clear();
            tintList.clear();
        }

        int n = posList.size();
        float[] pos = new float[n * 12];
        float[] uv = new float[n * 8];
        int[] rgb = new int[n];
        byte[] cull = new byte[n];
        float[] normal = new float[n * 3];
        int[] tint = new int[n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(posList.get(i), 0, pos, i * 12, 12);
            System.arraycopy(uvList.get(i), 0, uv, i * 8, 8);
            System.arraycopy(normalList.get(i), 0, normal, i * 3, 3);
            rgb[i] = rgbList.get(i);
            cull[i] = cullList.get(i);
            tint[i] = tintList.get(i);
        }
        return new SchematicGhostModel(n, pos, uv, rgb, atlasList.toArray(new Identifier[0]), cull, normal, tint,
                shape, full, flushMask(pos, cull, n));
    }

    /** Unit normal of a quad from its diagonals, signed to agree with the quad's lighting direction. */
    private static float[] frontNormal(float[] p, Direction facing) {
        float ax = p[6] - p[0], ay = p[7] - p[1], az = p[8] - p[2];
        float bx = p[9] - p[3], by = p[10] - p[4], bz = p[11] - p[5];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0E-8f) return new float[]{facing.getStepX(), facing.getStepY(), facing.getStepZ()};
        nx /= len;
        ny /= len;
        nz /= len;
        if (nx * facing.getStepX() + ny * facing.getStepY() + nz * facing.getStepZ() < 0f) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        return new float[]{nx, ny, nz};
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

    /**
     * Directional shade of a quad: {@link #FACE_SHADE} of the three axis faces its unit normal points towards,
     * weighted by the squared normal component, so slanted and diagonal quads (stairs, kelp, dust) light smoothly.
     */
    static float shadeOf(float[] normal) {
        float nx = normal[0], ny = normal[1], nz = normal[2];
        return nx * nx * FACE_SHADE[nx > 0f ? 5 : 4]
                + ny * ny * FACE_SHADE[ny > 0f ? 1 : 0]
                + nz * nz * FACE_SHADE[nz > 0f ? 3 : 2];
    }

    private static int quadRgb(Minecraft mc, BlockState state, BlockPos at, BakedQuad quad, float[] normal) {
        float shade = shadeOf(normal);
        int r = 255, g = 255, b = 255;
        if (at != null && quad.materialInfo().isTinted() && mc.getBlockColors() != null && mc.level != null) {
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

    /**
     * Bit per direction: the boundary quads on that face, rasterised to a 16x16 grid, lie inside the quads on the
     * opposite face. Two blocks of this same model side by side then hide each other's touching faces exactly
     * (glass, panes, slabs and stairs stacked flush), while asymmetric faces never match.
     */
    private static byte flushMask(float[] pos, byte[] cull, int n) {
        long[][] cover = new long[6][4];
        for (int q = 0; q < n; q++) {
            int d = cull[q];
            if (d < 0) continue;
            int axis = d < 2 ? 1 : d < 4 ? 2 : 0;
            int ua = (axis + 1) % 3, va = (axis + 2) % 3;
            float uMin = Float.MAX_VALUE, uMax = -Float.MAX_VALUE, vMin = Float.MAX_VALUE, vMax = -Float.MAX_VALUE;
            for (int c = 0; c < 4; c++) {
                float u = pos[q * 12 + c * 3 + ua], v = pos[q * 12 + c * 3 + va];
                uMin = Math.min(uMin, u);
                uMax = Math.max(uMax, u);
                vMin = Math.min(vMin, v);
                vMax = Math.max(vMax, v);
            }
            int u0 = Math.max(0, (int) Math.floor(uMin * 16f + 0.01f)), u1 = Math.min(16, (int) Math.ceil(uMax * 16f - 0.01f));
            int v0 = Math.max(0, (int) Math.floor(vMin * 16f + 0.01f)), v1 = Math.min(16, (int) Math.ceil(vMax * 16f - 0.01f));
            for (int v = v0; v < v1; v++) {
                for (int u = u0; u < u1; u++) {
                    int bit = v * 16 + u;
                    cover[d][bit >> 6] |= 1L << (bit & 63);
                }
            }
        }
        int mask = 0;
        for (int d = 0; d < 6; d++) {
            long[] mine = cover[d], other = cover[d ^ 1];
            boolean any = false, inside = true;
            for (int w = 0; w < 4; w++) {
                any |= mine[w] != 0L;
                inside &= (mine[w] & ~other[w]) == 0L;
            }
            if (any && inside) mask |= 1 << d;
        }
        return (byte) mask;
    }
}
