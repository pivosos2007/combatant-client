/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

/**
 * Block counts per layer and per palette entry. Whole-build numbers are computed once at load; a layer's
 * palette histogram is computed the first time someone asks for it and then kept.
 */
public final class LayerStats {

    private final Schematic schematic;
    private final int[] blocksPerLayer;
    private final int[] paletteTotals;
    private final int[][] layerHistograms;
    private final int maxLayerBlocks;

    private LayerStats(Schematic schematic, int[] blocksPerLayer, int[] paletteTotals) {
        this.schematic = schematic;
        this.blocksPerLayer = blocksPerLayer;
        this.paletteTotals = paletteTotals;
        this.layerHistograms = new int[schematic.sizeY()][];
        int max = 0;
        for (int count : blocksPerLayer) max = Math.max(max, count);
        this.maxLayerBlocks = max;
    }

    public static LayerStats compute(Schematic schematic) {
        int[] perLayer = new int[schematic.sizeY()];
        int[] totals = new int[schematic.paletteSize()];
        int area = schematic.layerArea();
        int volume = schematic.volume();
        for (int i = 0; i < volume; i++) {
            int palette = schematic.paletteIndexAt(i);
            if (palette == 0) continue;
            perLayer[i / area]++;
            totals[palette]++;
        }
        return new LayerStats(schematic, perLayer, totals);
    }

    public int layerCount() {
        return blocksPerLayer.length;
    }

    public int blocksInLayer(int y) {
        return y < 0 || y >= blocksPerLayer.length ? 0 : blocksPerLayer[y];
    }

    public int maxLayerBlocks() {
        return maxLayerBlocks;
    }

    public int[] paletteTotals() {
        return paletteTotals;
    }

    public synchronized int[] layerHistogram(int y) {
        if (y < 0 || y >= layerHistograms.length) return new int[schematic.paletteSize()];
        int[] cached = layerHistograms[y];
        if (cached != null) return cached;
        int[] histogram = new int[schematic.paletteSize()];
        int area = schematic.layerArea();
        int start = y * area;
        for (int i = start; i < start + area; i++) {
            int palette = schematic.paletteIndexAt(i);
            if (palette != 0) histogram[palette]++;
        }
        layerHistograms[y] = histogram;
        return histogram;
    }
}
