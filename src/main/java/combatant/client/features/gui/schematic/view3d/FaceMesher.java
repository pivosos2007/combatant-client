/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic.view3d;

/**
 * Turns a voxel volume into the list of faces worth drawing: a face survives only when the voxel next to it is
 * air, hidden, or in another visibility class (or, exploded, on another layer). When that is still more than the
 * budget, layers away from the selected one are merged into coarse blocks, doubling the block size until it fits.
 */
public final class FaceMesher {

    /** Layers either side of the selected one that keep full detail while the rest goes coarse. */
    static final int DETAIL_BAND = 2;
    private static final int MAX_STEP = 64;
    /** Blocks one segment may allocate (an int and a byte each); bigger volumes start at a coarser block size. */
    static final long MAX_SEGMENT_CELLS = 1L << 22;
    private static final float[] DIRECTION_SHADE = {1.0f, 0.52f, 0.80f, 0.80f, 0.68f, 0.68f};

    private FaceMesher() {
    }

    public static FaceList build(VoxelVolume volume, int[] paletteColors, ViewMode mode, int selected, float gap, int maxFaces) {
        FaceList out = new FaceList();
        int maxSize = Math.max(volume.sizeX(), Math.max(volume.sizeY(), volume.sizeZ()));
        // A 64M-cell volume at full detail would allocate 320 MB of segment arrays just to learn it is over budget.
        for (int step = coarsestNeeded(volume, mode, volume.sizeY(), 1); ; step *= 2) {
            boolean last = step >= MAX_STEP || step >= maxSize;
            out.reset(step);
            if (emitAll(volume, paletteColors, mode, selected, gap, step, maxFaces, out)) return out;
            if (last) {
                out.markTruncated();
                return out;
            }
        }
    }

    private static boolean emitAll(VoxelVolume volume, int[] colors, ViewMode mode, int selected, float gap,
                                   int step, int maxFaces, FaceList out) {
        int sizeY = volume.sizeY();
        if (step == 1) return emitSegment(volume, colors, mode, selected, gap, 0, sizeY, 1, maxFaces, out);
        int low = Math.max(0, Math.min(sizeY, selected - DETAIL_BAND));
        int high = Math.max(low, Math.min(sizeY, selected + DETAIL_BAND + 1));
        // Even the detail band goes coarse on very large footprints, just less so than the rest.
        int bandStep = Math.max(1, step / 8);
        return emitSegment(volume, colors, mode, selected, gap, 0, low, step, maxFaces, out)
                && emitSegment(volume, colors, mode, selected, gap, low, high, bandStep, maxFaces, out)
                && emitSegment(volume, colors, mode, selected, gap, high, sizeY, step, maxFaces, out);
    }

    /** Emits the faces of layers [y0, y1) at block size {@code step}; false once the budget is exceeded. */
    private static boolean emitSegment(VoxelVolume volume, int[] colors, ViewMode mode, int selected, float gap,
                                       int y0, int y1, int step, int maxFaces, FaceList out) {
        if (y1 <= y0) return true;
        Segment seg = new Segment(volume, mode, selected, y0, y1, coarsestNeeded(volume, mode, y1 - y0, step));
        seg.fill();
        for (int k = 0; k < seg.cy; k++) {
            for (int j = 0; j < seg.cz; j++) {
                for (int i = 0; i < seg.cx; i++) {
                    int voxel = seg.voxel[seg.cellIndex(i, j, k)];
                    if (voxel < 0) continue;
                    if (!emitBlock(volume, colors, mode, gap, seg, i, j, k, voxel, maxFaces, out)) return false;
                }
            }
        }
        return true;
    }

    /** The smallest power-of-two multiple of {@code step} whose segment over {@code layers} fits the cell cap. */
    static int coarsestNeeded(VoxelVolume volume, ViewMode mode, int layers, int step) {
        int widest = Math.max(volume.sizeX(), volume.sizeZ());
        while (step < widest && segmentCells(volume, mode, layers, step) > MAX_SEGMENT_CELLS) step *= 2;
        return step;
    }

    static long segmentCells(VoxelVolume volume, ViewMode mode, int layers, int step) {
        int height = mode.exploded() ? 1 : step;
        long cx = (volume.sizeX() + (long) step - 1) / step;
        long cz = (volume.sizeZ() + (long) step - 1) / step;
        long cy = (layers + (long) height - 1) / height;
        return cx * cy * cz;
    }

    private static boolean emitBlock(VoxelVolume volume, int[] colors, ViewMode mode, float gap, Segment seg,
                                     int i, int j, int k, int voxel, int maxFaces, FaceList out) {
        int sizeX = volume.sizeX(), sizeZ = volume.sizeZ();
        int x0 = i * seg.step, z0 = j * seg.step, yLow = seg.y0 + k * seg.height;
        int x1 = Math.min(sizeX, x0 + seg.step), z1 = Math.min(sizeZ, z0 + seg.step);
        int yHigh = Math.min(seg.y1, yLow + seg.height);
        byte cls = seg.cls[seg.cellIndex(i, j, k)];
        float minY = mode.layerBase(yLow, gap);
        float maxY = mode.layerBase(yHigh - 1, gap) + 1f;
        int base = colors[volume.cell(voxel)];
        for (int d = 0; d < 6; d++) {
            if (!exposed(seg, mode, i, j, k, d, cls)) continue;
            if (out.count() >= maxFaces) return false;
            out.add(d, cls, shade(base, d, cls, voxel), voxel, x0, minY, z0, x1, maxY, z1);
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
        return seg.voxel[neighbour] < 0 || seg.cls[neighbour] != cls;
    }

    /** Direction light, a stable per-voxel jitter so flat walls read as blocks, then the class treatment. */
    static int shade(int argb, int direction, byte cls, int voxel) {
        int hash = voxel * 0x9E3779B1;
        float jitter = 1f + (((hash >>> 24) & 0xFF) / 255f - 0.5f) * 0.10f;
        int lit = scale(argb, DIRECTION_SHADE[direction] * jitter);
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
        final ViewMode mode;
        final int selected;
        final int y0, y1, step, height;
        final int cx, cy, cz;
        final int[] voxel;
        final byte[] cls;

        Segment(VoxelVolume volume, ViewMode mode, int selected, int y0, int y1, int step) {
            this.volume = volume;
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
