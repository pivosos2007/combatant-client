/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic.view3d;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

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
    /** Large coplanar spans remain a single GPU instance; sprite repetition happens in the fragment shader. */
    private static final int MAX_GREEDY_AXIS = 128;
    /** Avoid allocating one full-volume int + byte + boolean array for large schematics. */
    private static final int MAX_SEGMENT_CELLS = 1_048_576;
    private static final float[] DIRECTION_SHADE = {1.0f, 0.52f, 0.80f, 0.80f, 0.68f, 0.68f};

    private FaceMesher() {
    }

    /**
     * @param shapes per palette index; null entries (and the whole array being null) mean a plain opaque cube
     */
    public static FaceList build(VoxelVolume volume, int[] paletteColors, ViewMode mode, int selected, float gap, int maxFaces) {
        return build(volume, null, paletteColors, mode, selected, gap, maxFaces, null);
    }

    /** Per-block faces, no merging: every face maps to exactly one voxel. */
    public static FaceList build(VoxelVolume volume, BlockShape[] shapes, int[] paletteColors, ViewMode mode, int selected, float gap, int maxFaces) {
        return build(volume, shapes, paletteColors, mode, selected, gap, maxFaces, null);
    }

    /**
     * @param mergeGroup null for per-block faces; otherwise, at full detail, coplanar cube faces of the same palette
     *                   entry, class and group (the function maps a schematic voxel index to a group in 0..7) join
     *                   into one larger face; such a face reports only its first voxel.
     */
    public static FaceList build(VoxelVolume volume, BlockShape[] shapes, int[] paletteColors, ViewMode mode, int selected, float gap,
                                 int maxFaces, IntUnaryOperator mergeGroup) {
        return buildAtLeastStep(volume, shapes, paletteColors, mode, selected, gap, maxFaces, mergeGroup, 1);
    }

    /** The same bounded mesher, starting at a coarser resolution for GPU distance LODs. */
    public static FaceList buildAtLeastStep(VoxelVolume volume, BlockShape[] shapes, int[] paletteColors,
                                            ViewMode mode, int selected, float gap, int maxFaces,
                                            IntUnaryOperator mergeGroup, int minimumStep) {
        int firstStep = 1;
        while (firstStep < minimumStep && firstStep < MAX_STEP) firstStep *= 2;
        FaceList out = new FaceList();
        int maxSize = Math.max(volume.sizeX(), Math.max(volume.sizeY(), volume.sizeZ()));
        for (int step = firstStep; ; step *= 2) {
            boolean last = step >= MAX_STEP || step >= maxSize;
            out.reset(step);
            if (emitAll(volume, shapes, paletteColors, mode, selected, gap, step, maxFaces, mergeGroup, out)) return out;
            if (last) {
                out.markTruncated();
                return out;
            }
        }
    }

    private static boolean emitAll(VoxelVolume volume, BlockShape[] shapes, int[] colors, ViewMode mode, int selected, float gap,
                                   int step, int maxFaces, IntUnaryOperator merge, FaceList out) {
        int sizeY = volume.sizeY();
        if (step == 1) return emitSegment(volume, shapes, colors, mode, selected, gap, 0, sizeY, 1, maxFaces, merge, out);
        int low = Math.max(0, Math.min(sizeY, selected - DETAIL_BAND));
        int high = Math.max(low, Math.min(sizeY, selected + DETAIL_BAND + 1));
        // Even the detail band goes coarse on very large footprints, just less so than the rest.
        int bandStep = Math.max(1, step / 8);
        return emitSegment(volume, shapes, colors, mode, selected, gap, 0, low, step, maxFaces, merge, out)
                && emitSegment(volume, shapes, colors, mode, selected, gap, low, high, bandStep, maxFaces, merge, out)
                && emitSegment(volume, shapes, colors, mode, selected, gap, high, sizeY, step, maxFaces, merge, out);
    }

    /** Emits the faces of layers [y0, y1) at block size {@code step}; false once the budget is exceeded. */
    private static boolean emitSegment(VoxelVolume volume, BlockShape[] shapes, int[] colors, ViewMode mode, int selected, float gap,
                                       int y0, int y1, int step, int maxFaces, IntUnaryOperator merge, FaceList out) {
        return emitRegion(volume, shapes, colors, mode, selected, gap, 0, volume.sizeX(), 0, volume.sizeZ(),
                y0, y1, step, maxFaces, merge, out);
    }

    /** Bounded X/Z/Y chunks prevent volume-sized allocation even for giant single-layer footprints. */
    private static boolean emitRegion(VoxelVolume volume, BlockShape[] shapes, int[] colors, ViewMode mode, int selected,
                                      float gap, int x0, int x1, int z0, int z1, int y0, int y1, int step,
                                      int maxFaces, IntUnaryOperator merge, FaceList out) {
        if (y1 <= y0 || x1 <= x0 || z1 <= z0) return true;
        int cx = (x1 - x0 + step - 1) / step;
        int cz = (z1 - z0 + step - 1) / step;
        int height = mode.exploded() ? 1 : step;
        int cy = (y1 - y0 + height - 1) / height;
        long footprint = (long) cx * cz;
        // Prefer spatial chunks when a whole Y-slab would still be enormous;
        // keep coordinates aligned to the global LOD grid at every split.
        if (footprint * Math.min(cy, 16) > MAX_SEGMENT_CELLS && (cx > 1 || cz > 1)) {
            boolean splitX = cx >= cz && cx > 1 || cz <= 1;
            if (splitX) {
                int mid = x0 + (cx / 2) * step;
                return emitRegion(volume, shapes, colors, mode, selected, gap, x0, mid, z0, z1,
                        y0, y1, step, maxFaces, merge, out)
                        && emitRegion(volume, shapes, colors, mode, selected, gap, mid, x1, z0, z1,
                        y0, y1, step, maxFaces, merge, out);
            }
            int mid = z0 + (cz / 2) * step;
            return emitRegion(volume, shapes, colors, mode, selected, gap, x0, x1, z0, mid,
                    y0, y1, step, maxFaces, merge, out)
                    && emitRegion(volume, shapes, colors, mode, selected, gap, x0, x1, mid, z1,
                    y0, y1, step, maxFaces, merge, out);
        }
        if (footprint * cy > MAX_SEGMENT_CELLS) {
            int slabLayers = Math.max(1, (int) (MAX_SEGMENT_CELLS / footprint));
            for (int start = y0; start < y1;) {
                int end = (int) Math.min(y1, (long) start + (long) slabLayers * height);
                if (!emitRegion(volume, shapes, colors, mode, selected, gap, x0, x1, z0, z1,
                        start, end, step, maxFaces, merge, out)) return false;
                start = end;
            }
            return true;
        }
        Segment seg = new Segment(volume, shapes, mode, selected, x0, x1, z0, z1, y0, y1, step);
        seg.fill();
        // Merge at every resolution: without this a simplified wall can contain more
        // individual faces than its full-resolution greedy counterpart.
        boolean greedy = merge != null;
        for (int k = 0; k < seg.cy; k++) {
            for (int j = 0; j < seg.cz; j++) {
                for (int i = 0; i < seg.cx; i++) {
                    int voxel = seg.voxel[seg.cellIndex(i, j, k)];
                    if (voxel < 0) continue;
                    if (!emitBlock(volume, shapes, colors, mode, gap, seg, i, j, k, voxel, maxFaces, greedy, out)) return false;
                }
            }
        }
        return !greedy || mergeCubes(volume, colors, mode, gap, seg, maxFaces, merge, out);
    }

    private static boolean emitBlock(VoxelVolume volume, BlockShape[] shapes, int[] colors, ViewMode mode, float gap, Segment seg,
                                     int i, int j, int k, int voxel, int maxFaces, boolean cubesMerged, FaceList out) {
        int sizeX = volume.sizeX(), sizeZ = volume.sizeZ();
        int x0 = seg.x0 + i * seg.step, z0 = seg.z0 + j * seg.step, yLow = seg.y0 + k * seg.height;
        int x1 = Math.min(seg.x1, x0 + seg.step), z1 = Math.min(seg.z1, z0 + seg.step);
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
        if (cubesMerged) return true;
        float maxY = mode.layerBase(yHigh - 1, gap) + 1f;
        for (int d = 0; d < 6; d++) {
            if (!exposed(seg, mode, i, j, k, d, cls)) continue;
            if (out.count() >= maxFaces) return false;
            out.add(d, cls, shade(base, d, cls, voxel), shade(0xFFFFFFFF, d, cls, voxel), voxel, x0, minY, z0, x1, maxY, z1,
                    seg.occludes[seg.cellIndex(i, j, k)]);
        }
        return true;
    }

    /**
     * Greedy meshing of cube faces at the selected resolution: per direction and slice, faces sharing a palette entry
     * and class grow into the largest rectangles. Exploded layers never merge vertically, they sit apart.
     */
    private static boolean mergeCubes(VoxelVolume volume, int[] colors, ViewMode mode, float gap, Segment seg, int maxFaces,
                                      IntUnaryOperator group, FaceList out) {
        BlockShape[] shapes = seg.shapes;
        // Only the active face slice is needed, not a maxDimension^2 square.
        int[] mask = new int[0];
        int[] cell = new int[3];
        for (int d = 0; d < 6; d++) {
            // Slice axis n, then u and v across it; axes are 0 = x (i), 1 = y (k), 2 = z (j).
            int na = d <= FaceList.DOWN ? 1 : d <= FaceList.WEST ? 0 : 2;
            int ua = na == 1 ? 0 : na == 0 ? 2 : 0;
            int va = na == 1 ? 2 : 1;
            int nSize = size(seg, na), uSize = size(seg, ua), vSize = size(seg, va);
            if (mask.length < uSize * vSize) mask = new int[uSize * vSize];
            boolean vertical = va == 1 && mode.exploded();
            for (int n = 0; n < nSize; n++) {
                for (int v = 0; v < vSize; v++) {
                    for (int u = 0; u < uSize; u++) {
                        place(cell, na, n, ua, u, va, v);
                        int ci = seg.cellIndex(cell[0], cell[2], cell[1]);
                        int voxel = seg.voxel[ci];
                        int key = -1;
                        if (voxel >= 0) {
                            int palette = volume.cell(voxel);
                            BlockShape shape = seg.step == 1 && shapes != null ? shapes[palette] : null;
                            if ((shape == null || shape.cube) && exposed(seg, mode, cell[0], cell[2], cell[1], d, seg.cls[ci])) {
                                key = (palette << 3 | seg.cls[ci]) << 3 | (group.applyAsInt(voxel) & 7);
                            }
                        }
                        mask[v * uSize + u] = key;
                    }
                }
                for (int v = 0; v < vSize; v++) {
                    for (int u = 0; u < uSize; u++) {
                        int key = mask[v * uSize + u];
                        if (key < 0) continue;
                        // Keep every merged face within the same 128-block GPU LOD tile.
                        // Otherwise a single greedy rectangle could cross two distance tiers.
                        int uPos = axisOrigin(seg, ua) + u * axisStep(seg, ua);
                        int vPos = axisOrigin(seg, va) + v * axisStep(seg, va);
                        int maxU = Math.min(MAX_GREEDY_AXIS / axisStep(seg, ua),
                                (MAX_GREEDY_AXIS - Math.floorMod(uPos, MAX_GREEDY_AXIS)) / axisStep(seg, ua));
                        int maxV = Math.min(MAX_GREEDY_AXIS / axisStep(seg, va),
                                (MAX_GREEDY_AXIS - Math.floorMod(vPos, MAX_GREEDY_AXIS)) / axisStep(seg, va));
                        int w = 1;
                        while (w < maxU && u + w < uSize && mask[v * uSize + u + w] == key) w++;
                        int h = 1;
                        while (!vertical && h < maxV && v + h < vSize && rowMatches(mask, v + h, u, w, uSize, key)) h++;
                        for (int dv = 0; dv < h; dv++) Arrays.fill(mask, (v + dv) * uSize + u, (v + dv) * uSize + u + w, -1);
                        if (out.count() >= maxFaces) return false;
                        place(cell, na, n, ua, u, va, v);
                        int first = seg.voxel[seg.cellIndex(cell[0], cell[2], cell[1])];
                        byte cls = (byte) ((key >> 3) & 7);
                        int base = colors[key >> 6];
                        int x0 = seg.x0 + cell[0] * seg.step, z0 = seg.z0 + cell[2] * seg.step;
                        int x1 = Math.min(seg.x1, x0 + (ua == 0 ? w : va == 0 ? h : 1) * seg.step);
                        int z1 = Math.min(seg.z1, z0 + (ua == 2 ? w : va == 2 ? h : 1) * seg.step);
                        int yFirst = seg.y0 + cell[1] * seg.height;
                        int yLast = Math.min(seg.y1 - 1, yFirst + (va == 1 ? h : 1) * seg.height - 1);
                        out.add(d, cls, shade(base, d, cls, first), shade(0xFFFFFFFF, d, cls, first), first,
                                x0, mode.layerBase(yFirst, gap), z0, x1, mode.layerBase(yLast, gap) + 1f, z1,
                                seg.occludes[seg.cellIndex(cell[0], cell[2], cell[1])]);
                    }
                }
            }
        }
        return true;
    }

    private static int axisStep(Segment seg, int axis) {
        return axis == 1 ? seg.height : seg.step;
    }

    private static int axisOrigin(Segment seg, int axis) {
        return axis == 0 ? seg.x0 : axis == 1 ? seg.y0 : seg.z0;
    }

    private static int size(Segment seg, int axis) {
        return axis == 0 ? seg.cx : axis == 1 ? seg.cy : seg.cz;
    }

    /** Writes the cell (x, y, z) holding slice {@code n} and in-slice position (u, v) on the given axes. */
    private static void place(int[] cell, int na, int n, int ua, int u, int va, int v) {
        cell[na] = n;
        cell[ua] = u;
        cell[va] = v;
    }

    private static boolean rowMatches(int[] mask, int row, int u, int w, int uSize, int key) {
        for (int c = 0; c < w; c++) {
            if (mask[row * uSize + u + c] != key) return false;
        }
        return true;
    }

    private static boolean exposed(Segment seg, ViewMode mode, int i, int j, int k, int d, byte cls) {
        int ni = i + FaceList.normal(d, 0);
        int nk = k + FaceList.normal(d, 1);
        int nj = j + FaceList.normal(d, 2);
        if (nk != k && mode.exploded()) return true;
        if (ni < 0 || ni >= seg.cx || nj < 0 || nj >= seg.cz || nk < 0 || nk >= seg.cy)
            return outsideExposed(seg, mode, ni, nj, nk, cls,
                    seg.volume.cell(seg.voxel[seg.cellIndex(i, j, k)]));
        int neighbour = seg.cellIndex(ni, nj, nk);
        if (seg.voxel[neighbour] < 0 || seg.cls[neighbour] != cls) return true;
        if (seg.occludes[neighbour]) return false;
        // Partial coarse cells cannot hide each other: they may have holes even
        // when both sampled the same palette index. Avoid disappearing LOD faces.
        if (seg.step > 1) return true;
        // A see-through block still hides the shared face between two of its own kind, like vanilla glass.
        return seg.volume.cell(seg.voxel[neighbour]) != seg.volume.cell(seg.voxel[seg.cellIndex(i, j, k)]);
    }

    /** When a neighbour falls in another mesh chunk, query the world instead of inventing a face at its seam. */
    private static boolean outsideExposed(Segment seg, ViewMode mode, int i, int j, int k, byte cls, int original) {
        int xLow = seg.x0 + i * seg.step, yLow = seg.y0 + k * seg.height, zLow = seg.z0 + j * seg.step;
        if (xLow < 0 || yLow < 0 || zLow < 0
                || xLow >= seg.volume.sizeX() || yLow >= seg.volume.sizeY() || zLow >= seg.volume.sizeZ())
            return true;
        int xHigh = Math.min(seg.volume.sizeX(), xLow + seg.step);
        int zHigh = Math.min(seg.volume.sizeZ(), zLow + seg.step);
        int yHigh = Math.min(seg.volume.sizeY(), yLow + seg.height);
        int first = -1;
        int firstPalette = -1;
        for (int y = yHigh - 1; y >= yLow && first < 0; y--) {
            if (mode.classOf(y, seg.selected) == ViewMode.HIDDEN) continue;
            for (int z = zLow; z < zHigh && first < 0; z++) {
                int index = seg.volume.index(xLow, y, z);
                for (int x = xLow; x < xHigh; x++, index++) {
                    int palette = seg.volume.cell(index);
                    if (palette == 0) continue;
                    first = y;
                    firstPalette = palette;
                    break;
                }
            }
        }
        if (first < 0 || mode.classOf(first, seg.selected) != cls) return true;
        BlockShape neighbourShape = seg.shapes == null ? null : seg.shapes[firstPalette];
        if (seg.step == 1) return neighbourShape != null && !neighbourShape.occludes && firstPalette != original;
        // A coarse neighbour can only hide the current surface if it is fully solid.
        boolean solid = true;
        for (int y = yLow; y < yHigh && solid; y++) {
            if (mode.classOf(y, seg.selected) != cls) { solid = false; break; }
            for (int z = zLow; z < zHigh && solid; z++) {
                int index = seg.volume.index(xLow, y, z);
                for (int x = xLow; x < xHigh; x++, index++) {
                    int palette = seg.volume.cell(index);
                    BlockShape shape = palette == 0 || seg.shapes == null ? null : seg.shapes[palette];
                    if (palette == 0 || shape != null && !shape.occludes) { solid = false; break; }
                }
            }
        }
        return !solid;
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
        final int x0, x1, z0, z1, y0, y1, step, height;
        final int cx, cy, cz;
        final int[] voxel;
        final byte[] cls;
        /** Whether the block hides the faces of its neighbours; see-through shapes (glass) never do, at any detail. */
        final boolean[] occludes;

        Segment(VoxelVolume volume, BlockShape[] shapes, ViewMode mode, int selected,
                int x0, int x1, int z0, int z1, int y0, int y1, int step) {
            this.volume = volume;
            this.shapes = shapes;
            this.mode = mode;
            this.selected = selected;
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.y0 = y0;
            this.y1 = y1;
            this.step = step;
            // Exploded layers sit apart, so a block can never span two of them.
            this.height = mode.exploded() ? 1 : step;
            this.cx = (x1 - x0 + step - 1) / step;
            this.cz = (z1 - z0 + step - 1) / step;
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
                        occludes[cell] = voxel[cell] >= 0 && (shape == null || shape.occludes)
                                && (step == 1 || fullyOccludes(i, j, k));
                    }
                }
            }
        }

        /** An occupied LOD cell is not necessarily solid. Only fully opaque cells may hide adjacent faces. */
        private boolean fullyOccludes(int i, int j, int k) {
            int yLow = y0 + k * height, yHigh = Math.min(y1, yLow + height);
            int xLow = x0 + i * step, xHigh = Math.min(x1, xLow + step);
            int zLow = z0 + j * step, zHigh = Math.min(z1, zLow + step);
            for (int y = yLow; y < yHigh; y++) {
                if (mode.classOf(y, selected) != cls[cellIndex(i, j, k)]) return false;
                for (int z = zLow; z < zHigh; z++) {
                    int row = volume.index(xLow, y, z);
                    for (int x = xLow; x < xHigh; x++) {
                        int palette = volume.cell(row + x - xLow);
                        if (palette == 0) return false;
                        BlockShape shape = shapes == null ? null : shapes[palette];
                        if (shape != null && !shape.occludes) return false;
                    }
                }
            }
            return true;
        }

        private int firstVisible(int i, int j, int k) {
            int yLow = y0 + k * height, yHigh = Math.min(y1, yLow + height);
            int xLow = x0 + i * step, xHigh = Math.min(x1, xLow + step);
            int zLow = z0 + j * step, zHigh = Math.min(z1, zLow + step);
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
