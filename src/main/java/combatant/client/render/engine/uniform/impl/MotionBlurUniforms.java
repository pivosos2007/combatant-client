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

public enum MotionBlurUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putFloat().putFloat().putFloat().putFloat()
            .putFloat().putFloat().putFloat().putFloat()
            .putFloat().putFloat().putFloat().putFloat()
            .putFloat().putFloat().putFloat().putFloat()
            .get();

    private static final Data DATA = new Data();
    private static final String UNIFORM_NAME = "Combatant - MotionBlur UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 4;

    public static void update(int width,
                              int height,
                              float velocityXPixels,
                              float velocityYPixels,
                              float historyXPixels,
                              float historyYPixels,
                              float maxBlurPixels,
                              float minMotionPixels,
                              int taps,
                              float historyBlend,
                              boolean historyValid,
                              float historyClamp) {
        DATA.width = Math.max(1, width);
        DATA.height = Math.max(1, height);
        DATA.velocityXPixels = finiteOrZero(velocityXPixels);
        DATA.velocityYPixels = finiteOrZero(velocityYPixels);
        DATA.historyXPixels = finiteOrZero(historyXPixels);
        DATA.historyYPixels = finiteOrZero(historyYPixels);
        DATA.maxBlurPixels = Math.max(0.0f, maxBlurPixels);
        DATA.minMotionPixels = Math.max(0.0f, minMotionPixels);
        DATA.taps = Math.max(1, taps);
        DATA.historyBlend = clamp01(historyBlend);
        DATA.historyValid = historyValid ? 1.0f : 0.0f;
        DATA.historyClamp = clamp01(historyClamp);
        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.0f;
        if (value < 0.0f) return 0.0f;
        return Math.min(value, 1.0f);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private int width = 1;
        private int height = 1;
        private float velocityXPixels;
        private float velocityYPixels;
        private float historyXPixels;
        private float historyYPixels;
        private float maxBlurPixels;
        private float minMotionPixels;
        private int taps;
        private float historyBlend;
        private float historyValid;
        private float historyClamp;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(width > 0 ? 1.0f / width : 0.0f)
                    .putFloat(height > 0 ? 1.0f / height : 0.0f)
                    .putFloat(width)
                    .putFloat(height)
                    .putFloat(velocityXPixels)
                    .putFloat(velocityYPixels)
                    .putFloat(maxBlurPixels)
                    .putFloat(minMotionPixels)
                    .putFloat(historyXPixels)
                    .putFloat(historyYPixels)
                    .putFloat(historyBlend)
                    .putFloat(historyValid)
                    .putFloat(taps)
                    .putFloat(historyClamp)
                    .putFloat(0.0f)
                    .putFloat(0.0f);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }

}
