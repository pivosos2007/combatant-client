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
    private static long lastFrameId = Long.MIN_VALUE;
    private static float lastFramebufferW = Float.NaN;
    private static float lastFramebufferH = Float.NaN;
    private static float lastLogicalW = Float.NaN;
    private static float lastLogicalH = Float.NaN;

    public static void update(float framebufferW, float framebufferH) {
        float safeW = framebufferW <= 0f ? 1f : framebufferW;
        float safeH = framebufferH <= 0f ? 1f : framebufferH;

        ViewportContext viewport = ViewportContext.current();
        float logicalW = viewport != null ? Math.max(1.0f, viewport.width()) : safeW;
        float logicalH = viewport != null ? Math.max(1.0f, viewport.height()) : safeH;

        CombatantUniformAllocator allocator = CombatantRenderSystem.uniforms();
        long frameId = allocator.frameId();
        if (allocator.hasCurrent(UNIFORM_NAME)
                && lastFrameId == frameId
                && Float.compare(lastFramebufferW, safeW) == 0
                && Float.compare(lastFramebufferH, safeH) == 0
                && Float.compare(lastLogicalW, logicalW) == 0
                && Float.compare(lastLogicalH, logicalH) == 0) {
            return;
        }

        DATA.screen[0] = safeW;
        DATA.screen[1] = safeH;
        DATA.screen[2] = logicalW;
        DATA.screen[3] = logicalH;

        allocator.write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
        lastFrameId = frameId;
        lastFramebufferW = safeW;
        lastFramebufferH = safeH;
        lastLogicalW = logicalW;
        lastLogicalH = logicalH;
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
    }

}
