/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

/** Base alignment and occupied byte size for the supported GLSL std430 value types. */
public enum Std430Type {
    FLOAT(4, 4),
    INT(4, 4),
    UINT(4, 4),
    BOOL(4, 4),
    VEC2(8, 8),
    IVEC2(8, 8),
    UVEC2(8, 8),
    VEC3(16, 12),
    IVEC3(16, 12),
    UVEC3(16, 12),
    VEC4(16, 16),
    IVEC4(16, 16),
    UVEC4(16, 16),
    MAT2(8, 16),
    MAT3(16, 48),
    MAT4(16, 64);

    private final int alignment;
    private final int size;

    Std430Type(int alignment, int size) {
        this.alignment = alignment;
        this.size = size;
    }

    public int alignment() {
        return alignment;
    }

    public int size() {
        return size;
    }

    public int arrayStride() {
        return align(size, alignment);
    }

    static int align(int value, int alignment) {
        return (value + alignment - 1) / alignment * alignment;
    }
}
