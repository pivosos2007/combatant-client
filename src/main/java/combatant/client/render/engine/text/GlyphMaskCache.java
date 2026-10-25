/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.text;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

final class GlyphMaskCache {
    private static final int MAX_ENTRIES = 8192;
    private final int size;
    private final ByteBuffer pixels;
    private final Map<GlyphMaskRasterizer.Key, Entry> entries = new HashMap<>();
    private GlyphAtlasAllocator allocator;
    private boolean full;
    private long frame = Long.MIN_VALUE;

    GlyphMaskCache(int size, ByteBuffer pixels) {
        this.size = size;
        this.pixels = pixels;
        allocator = new GlyphAtlasAllocator(size, size, 0);
    }

    void beginFrame(long currentFrame) {
        // Deferred draws retain UVs until frame submission; never reuse their texels mid-frame.
        if (frame != currentFrame && full) {
            entries.clear();
            allocator = new GlyphAtlasAllocator(size, size, 0);
            full = false;
            for (int i = 0; i < pixels.capacity(); i++) pixels.put(i, (byte) 0);
        }
        frame = currentFrame;
    }

    Entry get(GlyphMaskRasterizer rasterizer, GlyphMaskRasterizer.Key key) {
        Entry cached = entries.get(key);
        if (cached != null) return cached;
        if (full) return null;
        if (entries.size() >= MAX_ENTRIES) {
            full = true;
            return null;
        }
        var mask = rasterizer.rasterize(key);
        GlyphAtlasAllocator.Rect rect = mask.width() == 0 ? null : allocator.place(mask.width(), mask.height());
        if (mask.width() != 0 && rect == null) {
            full = true;
            return null;
        }
        int x = rect == null ? 0 : rect.x(), y = rect == null ? 0 : rect.y();
        // Clear the border as well: recycled GPU pages may contain a previous generation here.
        if (rect != null) {
            for (int row = -1; row <= mask.height(); row++) {
                for (int col = -1; col <= mask.width(); col++) {
                    byte value = row >= 0 && row < mask.height() && col >= 0 && col < mask.width()
                            ? mask.pixels()[row * mask.width() + col] : 0;
                    pixels.put((y + row) * size + x + col, value);
                }
            }
        }
        Entry result = new Entry(x, y, mask.left(), mask.top(), mask.width(), mask.height());
        entries.put(key, result);
        return result;
    }

    GlyphAtlasAllocator.Region takeDirty() {
        var region = allocator.takeDirty();
        return region == null ? null : new GlyphAtlasAllocator.Region(region.x() - 1, region.y() - 1,
                region.width() + 2, region.height() + 2);
    }

    int size() { return entries.size(); }

    record Entry(int x, int y, int left, int top, int width, int height) { }
}
