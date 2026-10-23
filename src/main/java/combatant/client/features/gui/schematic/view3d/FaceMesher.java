/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic.view3d;

/**
 * Turns a voxel volume into the list of faces worth drawing. Plain cubes contribute one box face per exposed side
 * (a face survives only when the voxel next to it is air, hidden, in another visibility class, or not opaque);
 * every other block contributes the quads of its real model, minus the ones lying against an opaque neighbour.
 * When that is still more than the budget, layers away from the selected one are merged into coarse cube blocks,
 * doubling the block size until it fits.
 */
public final class FaceMesher {

    /** Layers either side of the selected one that keep full detail while the rest goes coarse. */
    static final int DETAIL_BAND = 2;
    private static final int MAX_STEP = 64;
    private static final float[] DIRECTION_SHADE = {1.0f, 0.52f, 0.80f, 0.80f, 0.68f, 0.68f};

    private FaceMesher() {
    }

    /**
     * @param shapes per palette index; null entries (and the whole array being null) mean a plain opaque cube
     */
    public static FaceList build(VoxelVolume volume, int[] paletteColors, ViewMode mode, int selected, float gap, int maxFaces) {
        return build(volume, null, paletteColors, mode, selected, gap, maxFaces);
    }

    public static FaceList build(VoxelVolume volume, BlockShape[] shapes, int[] paletteColors, ViewMode mode, int selected, float gap, int maxFaces) {
        FaceList out = new FaceList();
        int maxSize = Math.max(volume.sizeX(), Math.max(volume.sizeY(), volume.sizeZ()));
        for (int step = 1; ; step *= 2) {
            boolean last = step >= MAX_STEP || step >= maxSize;
            out.reset(step);
            if (emitAll(volume, shapes, paletteColors, mode, selected, gap, step, maxFaces, out)) return out;
            if (last) {
                out.markTruncated();
                return out;
            }
        }
    }

    private static boolean emitAll(VoxelVolume volume, BlockShape[] shapes, int[] colors, ViewMode mode, int selected, float gap,
                                   int step, int maxFaces, FaceList out) {
        int sizeY = volume.sizeY();
        if (step == 1) return emitSegment(volume, shapes, colors, mode, selected, gap, 0, sizeY, 1, maxFaces, out);
        int low = Math.max(0, Math.min(sizeY, selected - DETAIL_BAND));
        int high = Math.max(low, Math.min(sizeY, selected + DETAIL_BAND + 1));
        // Even the detail band goes coarse on very large footprints, just less so than the rest.
        int bandStep = Math.max(1, step / 8);
        return emitSegment(volume, shapes, colors, mode, selected, gap, 0, low, step, maxFaces, out)
                && emitSegment(volume, shapes, colors, mode, selected, gap, low, high, bandStep, maxFaces, out)
                && emitSegment(volume, shapes, colors, mode, selected, gap, high, sizeY, step, maxFaces, out);
    }

    /** Emits the faces of layers [y0, y1) at block size {@code step}; false once the budget is exceeded. */
    private static boolean emitSegment(VoxelVolume volume, BlockShape[] shapes, int[] colors, ViewMode mode, int selected, float gap,
                                       int y0, int y1, int step, int maxFaces, FaceList out) {
        if (y1 <= y0) return true;
        Segment seg = new Segment(volume, shapes, mode, selected, y0, y1, step);
        seg.fill();
        for (int k = 0; k < seg.cy; k++) {
            for (int j = 0; j < seg.cz; j++) {
                for (int i = 0; i < seg.cx; i++) {
                    int voxel = seg.voxel[seg.cellIndex(i, j, k)];
                    if (voxel < 0) continue;
                    if (!emitBlock(volume, shapes, colors, mode, gap, seg, i, j, k, voxel, maxFaces, out)) return false;
                }
            }
        }
        return true;
    }

    private static boolean emitBlock(VoxelVolume volume, BlockShape[] shapes, int[] colors, ViewMode mode, float gap, Segment seg,
                                     int i, int j, int k, int voxel, int maxFaces, FaceList out) {
        int sizeX = volume.sizeX(), sizeZ = volume.sizeZ();
        int x0 = i * seg.step, z0 = j * seg.step, yLow = seg.y0 + k * seg.height;
        int x1 = Math.min(sizeX, x0 + seg.step), z1 = Math.min(sizeZ, z0 + seg.step);
        int yHigh = Math.min(seg.y1, yLow + seg.height);
        byte cls = seg.cls[seg.cellIndex(i, j, k)];
        float minY = mode.layerBase(yLow, gap);
        int palette = volume.cell(voxel);
        int base = colors[palette];
        BlockShape shape = seg.step == 1 && shapes != null ? shapes[palette] : null;
        if (shape != null && !shape.cube) {
            for (int q = 0; q < shape.quadCount; q++) {
                int side = shape.cull[q];
                if (side >= 0 && !exposed(seg, mode, i, j, k, side, cls)) continue;
                if (out.count() >= maxFaces) return false;
                float nx = shape.normal[q * 3], ny = shape.normal[q * 3 + 1], nz = shape.normal[q * 3 + 2];
                float factor = lightFactor(nx, ny, nz);
                out.addQuad(shape, q, cls, shade(base, factor, cls, voxel), shade(0xFFFFFFFF, factor, cls, voxel), voxel, x0, minY, z0);
            }
            return true;
        }
        float maxY = mode.layerBase(yHigh - 1, gap) + 1f;
        for (int d = 0; d < 6; d++) {
            if (!exposed(seg, mode, i, j, k, d, cls)) continue;
            if (out.count() >= maxFaces) return false;
            out.add(d, cls, shade(base, d, cls, voxel), shade(0xFFFFFFFF, d, cls, voxel), voxel, x0, minY, z0, x1, maxY, z1);
        }
        return true;
    }

    private static boolean exposed(Segment seg, ViewMode mode, int i, int j, int k, int d, byte cls) {
        int ni = i + FaceList.normal(d, 0);
        int nk = k + FaceList.normal(d, 1);
        int nj = j + FaceList.normal(d, 2);
        if (ni < 0 || nj < 0 || nk < 0 || ni >= seg.cx || nj >= seg.cz || nk >= seg.cy) return true;
        if (nk != k && mode.exploded()) return true;
        int neighbour = seg.cellIndex(ni, nj, nk);
        return seg.voxel[neighbour] < 0 || seg.cls[neighbour] != cls || !seg.occludes[neighbour];
    }

    /** Light of a model quad from its normal: the box-side shades blended by how far the quad leans up or down. */
    static float lightFactor(float nx, float ny, float nz) {
        float ax = Math.abs(nx), az = Math.abs(nz), h = ax + az;
        float side = h < 1.0e-4f ? DIRECTION_SHADE[FaceList.EAST]
                : (DIRECTION_SHADE[FaceList.EAST] * ax + DIRECTION_SHADE[FaceList.SOUTH] * az) / h;
        float vertical = DIRECTION_SHADE[ny >= 0f ? FaceList.UP : FaceList.DOWN];
        return side + (vertical - side) * Math.min(1f, Math.abs(ny));
    }

    /** Direction light, a stable per-voxel jitter so flat walls read as blocks, then the class treatment. */
    static int shade(int argb, int direction, byte cls, int voxel) {
        return shade(argb, DIRECTION_SHADE[direction], cls, voxel);
    }

    static int shade(int argb, float factor, byte cls, int voxel) {
        int hash = voxel * 0x9E3779B1;
        float jitter = 1f + (((hash >>> 24) & 0xFF) / 255f - 0.5f) * 0.06f;
        int lit = scale(argb, factor * jitter);
        return switch (cls) {
            case ViewMode.SELECTED -> mix(lit, 0xFFFFFFFF, 0.10f);
            case ViewMode.BELOW -> mix(lit, 0xFF0B0E14, 0.45f);
            case ViewMode.GHOST -> (mix(lit, 0xFF9FB3C8, 0.55f) & 0x00FFFFFF) | (46 << 24);
            case ViewMode.OTHER -> mix(lit, 0xFF0B0E14, 0.18f);
            default -> lit;
        };
    }

    static int scale(int argb, float factor) {
        int r = Math.min(255, Math.round(((argb >>> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((argb >>> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((argb & 0xFF) * factor));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static int mix(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int oa = Math.round(aa + (ba - aa) * t);
        int or = Math.round(ar + (br - ar) * t);
        int og = Math.round(ag + (bg - ag) * t);
        int ob = Math.round(ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    /**
     * One run of layers sampled at a fixed block size. Each block remembers the first visible voxel it covers
     * (or -1) and that voxel's class, so neighbour tests are array reads instead of volume scans.
     */
    private static final class Segment {
        final VoxelVolume volume;
        final BlockShape[] shapes;
        final ViewMode mode;
        final int selected;
        final int y0, y1, step, height;
        final int cx, cy, cz;
        final int[] voxel;
        final byte[] cls;
        /** Whether the block hides the faces of its neighbours; model shapes only count at full detail. */
        final boolean[] occludes;

        Segment(VoxelVolume volume, BlockShape[] shapes, ViewMode mode, int selected, int y0, int y1, int step) {
            this.volume = volume;
            this.shapes = step == 1 ? shapes : null;
            this.mode = mode;
            this.selected = selected;
            this.y0 = y0;
            this.y1 = y1;
            this.step = step;
            // Exploded layers sit apart, so a block can never span two of them.
            this.height = mode.exploded() ? 1 : step;
            this.cx = (volume.sizeX() + step - 1) / step;
            this.cz = (volume.sizeZ() + step - 1) / step;
            this.cy = (y1 - y0 + height - 1) / height;
            this.voxel = new int[cx * cy * cz];
            this.cls = new byte[voxel.length];
            this.occludes = new boolean[voxel.length];
        }

        int cellIndex(int i, int j, int k) {
            return (k * cz + j) * cx + i;
        }

        void fill() {
            for (int k = 0; k < cy; k++) {
                for (int j = 0; j < cz; j++) {
                    for (int i = 0; i < cx; i++) {
                        int cell = cellIndex(i, j, k);
                        voxel[cell] = firstVisible(i, j, k);
                        cls[cell] = voxel[cell] < 0 ? ViewMode.HIDDEN
                                : mode.classOf(voxel[cell] / (volume.sizeX() * volume.sizeZ()), selected);
                        BlockShape shape = voxel[cell] < 0 || shapes == null ? null : shapes[volume.cell(voxel[cell])];
                        occludes[cell] = voxel[cell] >= 0 && (shape == null || shape.occludes);
                    }
                }
            }
        }

        private int firstVisible(int i, int j, int k) {
            int yLow = y0 + k * height, yHigh = Math.min(y1, yLow + height);
            int xLow = i * step, xHigh = Math.min(volume.sizeX(), xLow + step);
            int zLow = j * step, zHigh = Math.min(volume.sizeZ(), zLow + step);
            for (int y = yHigh - 1; y >= yLow; y--) {
                if (mode.classOf(y, selected) == ViewMode.HIDDEN) continue;
                for (int z = zLow; z < zHigh; z++) {
                    int row = volume.index(0, y, z);
                    for (int x = xLow; x < xHigh; x++) {
                        if (volume.cell(row + x) != 0) return row + x;
                    }
                }
            }
            return -1;
        }
    }
}
