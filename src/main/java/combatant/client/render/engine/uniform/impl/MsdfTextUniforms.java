/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.uniform.impl;

import combatant.client.render.engine.core.CombatantRenderSystem;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;

/**
 * Std140 UBO for MSDF text rendering.
 * <p>
 * Layout (std140):
 * vec4 data; // x = pxRange, y = atlasWidth, z = atlasHeight, w = unused
 */
public enum MsdfTextUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putVec4()
            .get();

    private static final Data DATA = new Data();
    private static final String UNIFORM_NAME = "Combatant - MSDF Text UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 16;

    public static void update(float pxRange, int atlasWidth, int atlasHeight) {
        float safeW = atlasWidth <= 0 ? 1f : atlasWidth;
        float safeH = atlasHeight <= 0 ? 1f : atlasHeight;

        DATA.values[0] = pxRange;
        DATA.values[1] = safeW;
        DATA.values[2] = safeH;
        DATA.values[3] = 0f;

        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private final float[] values = new float[4];

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(values[0])
                    .putFloat(values[1])
                    .putFloat(values[2])
                    .putFloat(values[3]);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }

}
