/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import java.util.Arrays;
import java.util.function.LongPredicate;

/** A set of packed cells without boxing, usable wherever the walker takes a {@link LongPredicate}. */
public final class CellSet implements LongPredicate {

    private static final long FREE = Long.MIN_VALUE;

    private long[] keys = new long[64];
    private int count;

    public CellSet() {
        Arrays.fill(keys, FREE);
    }

    public boolean add(long key) {
        if ((count + 1) * 2 > keys.length) rehash();
        int mask = keys.length - 1;
        for (int i = slot(key, mask); ; i = (i + 1) & mask) {
            long k = keys[i];
            if (k == key) return false;
            if (k == FREE) {
                keys[i] = key;
                count++;
                return true;
            }
        }
    }

    public boolean contains(long key) {
        int mask = keys.length - 1;
        for (int i = slot(key, mask); ; i = (i + 1) & mask) {
            long k = keys[i];
            if (k == key) return true;
            if (k == FREE) return false;
        }
    }

    @Override
    public boolean test(long key) {
        return contains(key);
    }

    public int size() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public void clear() {
        if (count == 0) return;
        Arrays.fill(keys, FREE);
        count = 0;
    }

    private void rehash() {
        long[] old = keys;
        keys = new long[old.length * 2];
        Arrays.fill(keys, FREE);
        count = 0;
        for (long key : old) if (key != FREE) add(key);
    }

    private static int slot(long key, int mask) {
        long mixed = key * 0x9E3779B97F4A7C15L;
        return (int) (mixed ^ mixed >>> 32) & mask;
    }
}
