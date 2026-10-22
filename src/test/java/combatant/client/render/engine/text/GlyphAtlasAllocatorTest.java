/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.text;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlyphAtlasAllocatorTest {
    @Test
    void preservesExistingGlyphRowsAndPadding() {
        GlyphAtlasAllocator pack = new GlyphAtlasAllocator(64, 64, 32);
        GlyphAtlasAllocator.Rect a = pack.place(20, 10);
        GlyphAtlasAllocator.Rect b = pack.place(20, 11);
        GlyphAtlasAllocator.Rect c = pack.place(20, 10);
        assertNotNull(a); assertNotNull(b); assertNotNull(c);
        assertEquals(1, a.x());
        assertEquals(33, a.y());
        assertEquals(23, b.x());
        assertEquals(1, c.x());
        assertTrue(c.y() >= 46);
        assertTrue(c.y() + 10 < 64);
    }

    @Test
    void rejectsOversizedGlyphWithoutCorruptingNextInsertion() {
        GlyphAtlasAllocator pack = new GlyphAtlasAllocator(64, 64, 30);
        assertNull(pack.place(63, 4));
        assertNull(pack.place(2, 62));
        var rect = pack.place(4, 4);
        assertNotNull(rect);
        assertEquals(1, rect.x());
        assertEquals(31, rect.y());
    }

    @Test
    void accumulatesOneUploadRectanglePerShapedRun() {
        GlyphAtlasAllocator pack = new GlyphAtlasAllocator(64, 64, 30);
        assertNull(pack.takeDirty());
        var a = pack.place(10, 10);
        var b = pack.place(12, 14);
        var dirty = pack.takeDirty();
        assertEquals(a.x(), dirty.x());
        assertEquals(a.y(), dirty.y());
        assertEquals(b.x() + 12 - a.x(), dirty.width());
        assertEquals(14, dirty.height());
        assertNull(pack.takeDirty());
        var c = pack.place(5, 5);
        assertEquals(c.x(), pack.takeDirty().x());
    }

    @Test
    void refusesInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new GlyphAtlasAllocator(0, 20, 10));
        assertThrows(IllegalArgumentException.class, () -> new GlyphAtlasAllocator(64, 64, 64));
        GlyphAtlasAllocator pack = new GlyphAtlasAllocator(16, 12, 3);
        assertNull(pack.place(16, 1));
        assertNull(pack.place(0, 2));
    }
}
