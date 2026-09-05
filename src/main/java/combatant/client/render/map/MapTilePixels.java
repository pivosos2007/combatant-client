/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Immutable tightly packed RGBA8 tile payload. */
public final class MapTilePixels {
    private final int width;
    private final int height;
    private final byte[] rgba;

    public MapTilePixels(int width, int height, byte[] rgba) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Tile dimensions must be positive.");
        int expected = Math.multiplyExact(Math.multiplyExact(width, height), 4);
        if (rgba == null || rgba.length != expected) {
            throw new IllegalArgumentException("Expected " + expected + " RGBA bytes.");
        }
        this.width = width;
        this.height = height;
        this.rgba = rgba.clone();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int byteSize() {
        return rgba.length;
    }

    public byte[] copyBytes() {
        return rgba.clone();
    }

    public ByteBuffer readOnlyBuffer() {
        return ByteBuffer.wrap(rgba).asReadOnlyBuffer();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof MapTilePixels other
                && width == other.width
                && height == other.height
                && Arrays.equals(rgba, other.rgba);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * width + height) + Arrays.hashCode(rgba);
    }
}
