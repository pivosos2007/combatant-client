/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.text;

/** Append-only packing for lazy rasterized glyph IDs within one reserved atlas region. */
final class GlyphAtlasAllocator {
    private final int width;
    private final int height;
    private int x = 1;
    private int y;
    private int rowHeight;
    private int dirtyLeft = Integer.MAX_VALUE;
    private int dirtyTop = Integer.MAX_VALUE;
    private int dirtyRight;
    private int dirtyBottom;

    GlyphAtlasAllocator(int width, int height, int firstRow) {
        if (width < 4 || height < 4 || firstRow < 0 || firstRow >= height)
            throw new IllegalArgumentException("Invalid glyph atlas dimensions");
        this.width = width;
        this.height = height;
        this.y = firstRow + 1;
    }

    Rect place(int glyphWidth, int glyphHeight) {
        if (glyphWidth <= 0 || glyphHeight <= 0 || glyphWidth + 2 > width || glyphHeight + 2 > height)
            return null;
        int nextX = x;
        int nextY = y;
        int nextRowHeight = rowHeight;
        if (nextX + glyphWidth + 2 > width) {
            nextX = 1;
            nextY += nextRowHeight + 2;
            nextRowHeight = 0;
        }
        if (nextY + glyphHeight + 2 > height) return null;
        x = nextX + glyphWidth + 2;
        y = nextY;
        rowHeight = Math.max(nextRowHeight, glyphHeight);
        dirtyLeft = Math.min(dirtyLeft, nextX);
        dirtyTop = Math.min(dirtyTop, nextY);
        dirtyRight = Math.max(dirtyRight, nextX + glyphWidth);
        dirtyBottom = Math.max(dirtyBottom, nextY + glyphHeight);
        return new Rect(nextX, nextY);
    }

    Region takeDirty() {
        if (dirtyLeft == Integer.MAX_VALUE) return null;
        Region result = new Region(dirtyLeft, dirtyTop,
                dirtyRight - dirtyLeft, dirtyBottom - dirtyTop);
        dirtyLeft = dirtyTop = Integer.MAX_VALUE;
        dirtyRight = dirtyBottom = 0;
        return result;
    }

    record Rect(int x, int y) { }
    record Region(int x, int y, int width, int height) { }
}
