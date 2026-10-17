package combatant.client.features.module.modules.visuals.schematic;

import combatant.client.util.schematic.SchematicDiff;
import java.util.Arrays;

/**
 * Computes which cube faces need drawing. A face shared by two neighbouring cells of the same full-size status
 * (CORRECT/WRONG) is interior to a solid region of one colour class, so drawing it only double-blends the translucent
 * fill. MISSING cells are drawn inset and are never culled. Pure and allocation-free
 * after warm-up (the open-addressing table is reused between calls).
 */
final class SchematicFaceCulling {

    static final int FACE_DOWN = 1, FACE_UP = 2, FACE_NORTH = 4, FACE_SOUTH = 8, FACE_WEST = 16, FACE_EAST = 32;
    static final int ALL_FACES = 63;

    private static final long EMPTY = Long.MIN_VALUE;

    private long[] keys = new long[0];
    private byte[] values = new byte[0];
    private int mask;

    /** Fills {@code outMask[i]} with the visible-face bit set of box i (bits above). */
    void compute(int[] x, int[] y, int[] z, byte[] status, int count, byte[] outMask) {
        int capacity = Integer.highestOneBit(Math.max(4, count * 2 - 1)) << 1;
        if (keys.length < capacity) {
            keys = new long[capacity];
            values = new byte[capacity];
        }
        mask = capacity - 1;
        Arrays.fill(keys, 0, capacity, EMPTY);
        // MISSING cells are drawn shrunk (SchematicProjection.GHOST_INSET), so their shared faces are never interior:
        // they stay out of the table, which keeps every MISSING face and every face touching one.
        for (int i = 0; i < count; i++) if (status[i] != SchematicDiff.MISSING) put(pack(x[i], y[i], z[i]), status[i]);
        for (int i = 0; i < count; i++) {
            int xi = x[i], yi = y[i], zi = z[i];
            byte s = status[i];
            int faces = 0;
            if (!same(xi, yi - 1, zi, s)) faces |= FACE_DOWN;
            if (!same(xi, yi + 1, zi, s)) faces |= FACE_UP;
            if (!same(xi, yi, zi - 1, s)) faces |= FACE_NORTH;
            if (!same(xi, yi, zi + 1, s)) faces |= FACE_SOUTH;
            if (!same(xi - 1, yi, zi, s)) faces |= FACE_WEST;
            if (!same(xi + 1, yi, zi, s)) faces |= FACE_EAST;
            outMask[i] = (byte) faces;
        }
    }

    private boolean same(int x, int y, int z, byte status) {
        long key = pack(x, y, z);
        for (int slot = hash(key) & mask; keys[slot] != EMPTY; slot = (slot + 1) & mask) {
            if (keys[slot] == key) return values[slot] == status;
        }
        return false;
    }

    private void put(long key, byte status) {
        int slot = hash(key) & mask;
        while (keys[slot] != EMPTY && keys[slot] != key) slot = (slot + 1) & mask;
        keys[slot] = key;
        values[slot] = status;
    }

    private static int hash(long key) {
        key *= 0x9E3779B97F4A7C15L;
        return (int) (key >>> 32);
    }

    // 21 bits per axis, enough for any world coordinate; never equals EMPTY because the top bit stays clear.
    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }
}
