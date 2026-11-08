/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.joml.Matrix4fc;

/** Bounds-checked writer for arrays of a declared std430 struct. */
public final class Std430Writer {
    private final Std430StructLayout layout;
    private final int elements;
    private final ByteBuffer buffer;

    public Std430Writer(Std430StructLayout layout, int elements) {
        if (layout == null) throw new IllegalArgumentException("layout");
        if (elements < 1) throw new IllegalArgumentException("elements");
        this.layout = layout;
        this.elements = elements;
        this.buffer = ByteBuffer.allocateDirect(Math.multiplyExact(layout.arrayStride(), elements))
                .order(ByteOrder.nativeOrder());
    }

    public Std430StructLayout layout() {
        return layout;
    }

    public int elements() {
        return elements;
    }

    public int byteSize() {
        return buffer.capacity();
    }

    public Std430Writer putFloat(int element, String member, float value) {
        buffer.putFloat(offset(element, member, 0), value);
        return this;
    }

    public Std430Writer putInt(int element, String member, int value) {
        buffer.putInt(offset(element, member, 0), value);
        return this;
    }

    public Std430Writer putFloat(int element, String member, int arrayIndex, float value) {
        buffer.putFloat(offset(element, member, arrayIndex), value);
        return this;
    }

    public Std430Writer putInt(int element, String member, int arrayIndex, int value) {
        buffer.putInt(offset(element, member, arrayIndex), value);
        return this;
    }

    public Std430Writer putVec2(int element, String member, float x, float y) {
        int offset = offset(element, member, 0);
        buffer.putFloat(offset, x).putFloat(offset + 4, y);
        return this;
    }

    public Std430Writer putVec3(int element, String member, float x, float y, float z) {
        int offset = offset(element, member, 0);
        buffer.putFloat(offset, x).putFloat(offset + 4, y).putFloat(offset + 8, z);
        return this;
    }

    public Std430Writer putVec4(int element, String member, float x, float y, float z, float w) {
        int offset = offset(element, member, 0);
        buffer.putFloat(offset, x).putFloat(offset + 4, y).putFloat(offset + 8, z).putFloat(offset + 12, w);
        return this;
    }

    public Std430Writer putVec4(int element, String member, int arrayIndex,
                                float x, float y, float z, float w) {
        int offset = offset(element, member, arrayIndex);
        buffer.putFloat(offset, x).putFloat(offset + 4, y).putFloat(offset + 8, z).putFloat(offset + 12, w);
        return this;
    }

    /** Writes one column-major GLSL mat4 into a std430 MAT4 member. */
    public Std430Writer putMat4(int element, String member, Matrix4fc value) {
        if (value == null) throw new IllegalArgumentException("value");
        int offset = offset(element, member, 0);
        buffer.putFloat(offset, value.m00());
        buffer.putFloat(offset + 4, value.m01());
        buffer.putFloat(offset + 8, value.m02());
        buffer.putFloat(offset + 12, value.m03());
        buffer.putFloat(offset + 16, value.m10());
        buffer.putFloat(offset + 20, value.m11());
        buffer.putFloat(offset + 24, value.m12());
        buffer.putFloat(offset + 28, value.m13());
        buffer.putFloat(offset + 32, value.m20());
        buffer.putFloat(offset + 36, value.m21());
        buffer.putFloat(offset + 40, value.m22());
        buffer.putFloat(offset + 44, value.m23());
        buffer.putFloat(offset + 48, value.m30());
        buffer.putFloat(offset + 52, value.m31());
        buffer.putFloat(offset + 56, value.m32());
        buffer.putFloat(offset + 60, value.m33());
        return this;
    }

    public ByteBuffer buffer() {
        return buffer.asReadOnlyBuffer().order(buffer.order()).position(0).limit(buffer.capacity());
    }

    private int offset(int element, String memberName, int arrayIndex) {
        if (element < 0 || element >= elements) {
            throw new IndexOutOfBoundsException("element=" + element + " count=" + elements);
        }
        Std430StructLayout.Member member = layout.member(memberName);
        return element * layout.arrayStride() + member.elementOffset(arrayIndex);
    }
}
