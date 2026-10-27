/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Std430StructLayoutTest {
    @Test
    void runtimeScalarTailUsesItsOffsetWithoutTrailingBlockPadding() {
        Std430StructLayout layout = Std430StructLayout.builder()
                .member("header", Std430Type.VEC4)
                .member("count", Std430Type.UINT)
                .runtimeArray("values", Std430Type.FLOAT)
                .build();

        assertEquals(20, layout.size());
        assertEquals(20, layout.size(0));
        assertEquals(32, layout.size(3));
        assertEquals(28, layout.member("values").elementOffset(2, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> layout.member("values").elementOffset(3, 3));
        assertThrows(IllegalStateException.class, () -> layout.member("values").elementOffset(0));
        assertThrows(IllegalArgumentException.class, () -> layout.size(-1));
        assertThrows(ArithmeticException.class, () -> layout.size(Integer.MAX_VALUE));
        assertThrows(IllegalStateException.class, layout::arrayStride);
        assertThrows(IllegalArgumentException.class, () -> Std430StructLayout.builder().struct("nested", layout));
        assertThrows(IllegalArgumentException.class, () -> Std430StructLayout.builder()
                .runtimeArray("values", Std430Type.FLOAT).member("after", Std430Type.FLOAT));
    }

    @Test
    void runtimeStructArrayRetainsNestedLayoutAndStride() {
        Std430StructLayout drop = Std430StructLayout.builder()
                .member("center", Std430Type.VEC4).member("motion", Std430Type.VEC4).build();
        Std430StructLayout layout = Std430StructLayout.builder().runtimeStructArray("visible", drop).build();

        assertEquals(0, layout.size());
        assertEquals(96, layout.size(3));
        assertEquals(32, layout.member("visible").arrayStride());
        assertEquals(drop, layout.member("visible").structLayout());
        assertEquals(64, layout.member("visible").elementOffset(2, 3));
    }

    @Test
    void vec3CanShareItsFourthLaneWithScalar() {
        Std430StructLayout layout = Std430StructLayout.builder()
                .member("position", Std430Type.VEC3)
                .member("age", Std430Type.FLOAT)
                .build();

        assertEquals(0, layout.member("position").offset());
        assertEquals(12, layout.member("age").offset());
        assertEquals(16, layout.size());
        assertEquals(16, layout.arrayStride());
    }

    @Test
    void vec3ArrayUsesSixteenByteElementStride() {
        Std430StructLayout layout = Std430StructLayout.builder()
                .array("points", Std430Type.VEC3, 2)
                .member("flags", Std430Type.UINT)
                .build();

        assertEquals(16, layout.member("points").arrayStride());
        assertEquals(32, layout.member("flags").offset());
        assertEquals(48, layout.size());
    }

    @Test
    void writerUsesStructArrayStride() {
        Std430StructLayout layout = Std430StructLayout.builder()
                .member("position", Std430Type.VEC3)
                .member("life", Std430Type.FLOAT)
                .build();
        Std430Writer writer = new Std430Writer(layout, 2)
                .putVec3(1, "position", 1.0f, 2.0f, 3.0f)
                .putFloat(1, "life", 4.0f);

        ByteBuffer bytes = writer.buffer();
        assertEquals(1.0f, bytes.getFloat(16));
        assertEquals(2.0f, bytes.getFloat(20));
        assertEquals(3.0f, bytes.getFloat(24));
        assertEquals(4.0f, bytes.getFloat(28));
    }
}
