/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import java.util.Arrays;

/**
 * A {@link WalkTerrain} that asks its source once per cell and remembers the answer in 16x16x16 sections of bytes.
 *
 * <p>Planning reads the same cells many times (every node looks at its neighbours and their floors); with the
 * cache a flood over a few thousand cells costs one world lookup per cell instead of dozens. The owner drops
 * cells that changed ({@link #invalidate}) and the whole cache when it grows stale ({@link #clear}).</p>
 */
public final class TerrainCache implements WalkTerrain {

    /**
     * A source that can classify a whole 16x16x16 section in one go, far cheaper than cell by cell (one chunk
     * lookup, then palette reads). Cells are stored at {@code (y & 15) << 8 | (z & 15) << 4 | x & 15}.
     */
    public interface SectionSource extends WalkTerrain {
        /**
         * Fills {@code out}; returns {@code false} when part of the section was unknown only because it was not
         * loaded yet, so the cache asks again later ({@link #refillIncomplete}) instead of keeping the gap.
         */
        boolean fillSection(int sectionX, int sectionY, int sectionZ, byte[] out);
    }

    private static final byte UNSAMPLED = -1;
    private static final long FREE = Long.MIN_VALUE;

    private final WalkTerrain source;
    private long[] sectionKeys = new long[64];
    private byte[][] sections = new byte[64][];
    private int count;
    private long lookups;
    private long misses;
    /** Section coordinates (x, y, z triples) whose last fill was incomplete. */
    private int[] incomplete = new int[12];
    private int incompleteCount;

    public TerrainCache(WalkTerrain source) {
        this.source = source;
        Arrays.fill(sectionKeys, FREE);
    }

    @Override
    public byte cell(int x, int y, int z) {
        lookups++;
        byte[] section = section(x >> 4, y >> 4, z >> 4, true);
        int i = (y & 15) << 8 | (z & 15) << 4 | x & 15;
        byte value = section[i];
        if (value == UNSAMPLED) {
            misses++;
            value = source.cell(x, y, z);
            section[i] = value;
        }
        return value;
    }

    /** Forget one cell, so the next read asks the source again. */
    public void invalidate(int x, int y, int z) {
        byte[] section = section(x >> 4, y >> 4, z >> 4, false);
        if (section != null) section[(y & 15) << 8 | (z & 15) << 4 | x & 15] = UNSAMPLED;
    }

    /** Forget a cell and its six neighbours (a block change can turn a neighbour's floor into a wall). */
    public void invalidateAround(int x, int y, int z) {
        invalidate(x, y, z);
        invalidate(x + 1, y, z);
        invalidate(x - 1, y, z);
        invalidate(x, y + 1, z);
        invalidate(x, y - 1, z);
        invalidate(x, y, z + 1);
        invalidate(x, y, z - 1);
    }

    /** Forget everything. */
    public void clear() {
        Arrays.fill(sectionKeys, FREE);
        Arrays.fill(sections, null);
        count = 0;
        incompleteCount = 0;
    }

    private void fill(byte[] section, int sx, int sy, int sz) {
        if (source instanceof SectionSource bulk) {
            misses += 4096;
            if (!bulk.fillSection(sx, sy, sz, section)) {
                if (incompleteCount + 3 > incomplete.length) incomplete = Arrays.copyOf(incomplete, incomplete.length * 2);
                incomplete[incompleteCount++] = sx;
                incomplete[incompleteCount++] = sy;
                incomplete[incompleteCount++] = sz;
            }
        } else {
            Arrays.fill(section, UNSAMPLED);
        }
    }

    /**
     * Reads again the sections that were filled while some of their chunk was not loaded. Call between searches:
     * a search running over the cache would see cells change under it.
     */
    public void refillIncomplete() {
        if (incompleteCount == 0 || !(source instanceof SectionSource bulk)) return;
        int kept = 0;
        for (int i = 0; i < incompleteCount; i += 3) {
            int sx = incomplete[i], sy = incomplete[i + 1], sz = incomplete[i + 2];
            byte[] section = section(sx, sy, sz, false);
            if (section == null || bulk.fillSection(sx, sy, sz, section)) continue;
            incomplete[kept++] = sx;
            incomplete[kept++] = sy;
            incomplete[kept++] = sz;
        }
        incompleteCount = kept;
    }

    /** Sections held; the owner starts afresh once this grows past what one build area needs. */
    public int sectionCount() {
        return count;
    }

    /** Reads served, for diagnostics. */
    public long lookups() {
        return lookups;
    }

    /** Reads that had to ask the source. */
    public long misses() {
        return misses;
    }

    private byte[] section(int sx, int sy, int sz, boolean create) {
        long key = (long) sx << 42 ^ (long) (sy & 0x3FFFF) << 24 ^ (sz & 0xFFFFFFL);
        int mask = sectionKeys.length - 1;
        int i = slot(key, mask);
        while (true) {
            long k = sectionKeys[i];
            if (k == key) return sections[i];
            if (k == FREE) break;
            i = (i + 1) & mask;
        }
        if (!create) return null;
        if ((count + 1) * 2 > sectionKeys.length) {
            rehash();
            return section(sx, sy, sz, true);
        }
        byte[] section = new byte[4096];
        fill(section, sx, sy, sz);
        sectionKeys[i] = key;
        sections[i] = section;
        count++;
        return section;
    }

    private void rehash() {
        long[] oldKeys = sectionKeys;
        byte[][] oldSections = sections;
        sectionKeys = new long[oldKeys.length * 2];
        sections = new byte[oldKeys.length * 2][];
        Arrays.fill(sectionKeys, FREE);
        int mask = sectionKeys.length - 1;
        for (int j = 0; j < oldKeys.length; j++) {
            if (oldKeys[j] == FREE) continue;
            int i = slot(oldKeys[j], mask);
            while (sectionKeys[i] != FREE) i = (i + 1) & mask;
            sectionKeys[i] = oldKeys[j];
            sections[i] = oldSections[j];
        }
    }

    private static int slot(long key, int mask) {
        long mixed = key * 0x9E3779B97F4A7C15L;
        return (int) (mixed ^ mixed >>> 29) & mask;
    }
}
