/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.info;

import java.util.HashMap;
import java.util.Map;

/**
 * Exact-chunk occupancy and source-aware tint aggregation for the map overlay.
 * Each source first averages its own detections in a chunk, then all participating
 * sources contribute equally to the chunk tint. Therefore a high-volume ESP cannot
 * overwhelm a second ESP that found a single target in the same chunk.
 *
 * A chunk exists only when at least one source supplied a positive match count.
 * This data is independent of the larger zoom-dependent panel aggregation cells.
 */
public final class MapInfoChunkColors {
    private final Map<Chunk, ChunkColors> chunks = new HashMap<>();

    public void clear() {
        chunks.clear();
    }

    public void add(int blockX, int blockZ, String source, int argb, int count) {
        if (source == null || count <= 0) return;
        Chunk key = new Chunk(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
        chunks.computeIfAbsent(key, ignored -> new ChunkColors()).add(source, argb, count);
    }

    /** Visits only chunks with actual detections. Returned colors are opaque RGB. */
    public void forEach(ChunkColorConsumer consumer) {
        chunks.forEach((chunk, colors) -> consumer.accept(chunk.x, chunk.z, colors.mixedColor()));
    }

    public int size() {
        return chunks.size();
    }

    @FunctionalInterface
    public interface ChunkColorConsumer {
        void accept(int chunkX, int chunkZ, int argb);
    }

    private record Chunk(int x, int z) {}

    private static final class ChunkColors {
        private final Map<String, SourceColors> sources = new HashMap<>();

        void add(String source, int argb, int count) {
            sources.computeIfAbsent(source, ignored -> new SourceColors()).add(argb, count);
        }

        int mixedColor() {
            double red = 0.0;
            double green = 0.0;
            double blue = 0.0;
            for (SourceColors colors : sources.values()) {
                if (colors.count == 0) continue;
                // RMS mixing retains the intensity of saturated ESP colors rather
                // than reducing two bright colors to a dim, muddy overlay.
                red += colors.red / colors.count;
                green += colors.green / colors.count;
                blue += colors.blue / colors.count;
            }
            int size = sources.size();
            if (size == 0) return 0xFFFFFFFF;
            return 0xFF000000 | (channel(red / size) << 16)
                    | (channel(green / size) << 8) | channel(blue / size);
        }

        private static int channel(double meanSquare) {
            return Math.max(0, Math.min(255, (int) Math.round(Math.sqrt(meanSquare))));
        }
    }

    private static final class SourceColors {
        private double red;
        private double green;
        private double blue;
        private long count;

        void add(int argb, int amount) {
            int r = (argb >>> 16) & 255;
            int g = (argb >>> 8) & 255;
            int b = argb & 255;
            red += (double) r * r * amount;
            green += (double) g * g * amount;
            blue += (double) b * b * amount;
            count += amount;
        }
    }
}
