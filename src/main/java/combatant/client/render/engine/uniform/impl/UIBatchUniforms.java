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
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;

/**
 * Std140 UBO for batched 2D UI shaders.
 * <p>
 * Layout (std140):
 * vec4 screen; // xy = framebuffer size, zw = logical size
 */
public enum UIBatchUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putVec4()
            .get();

    private static final Data DATA = new Data();
    private static final String UNIFORM_NAME = "Combatant - UI Batch UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 16;

    public static void update(float framebufferW, float framebufferH) {
        float safeW = framebufferW <= 0f ? 1f : framebufferW;
        float safeH = framebufferH <= 0f ? 1f : framebufferH;

        float uiScale = ViewportContext.getUiScale();
        if (uiScale <= 0f) uiScale = 1f;

        float logicalW = safeW / uiScale;
        float logicalH = safeH / uiScale;

        DATA.screen[0] = framebufferW;
        DATA.screen[1] = framebufferH;
        DATA.screen[2] = logicalW;
        DATA.screen[3] = logicalH;

        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private final float[] screen = new float[4];

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(screen[0]).putFloat(screen[1]).putFloat(screen[2]).putFloat(screen[3]);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }

}
