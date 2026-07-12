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
 * Std140 UBO for the fullscreen Dual Kawase UI blur pass.
 * <p>
 * Layout (std140):
 * vec4 inputResolution; // xy = source size hint
 * vec4 size;            // xy = target size hint
 * vec4 location;        // reserved
 * vec4 params;          // x = Kawase offset px, y = pass mode (0 down, 1 up), z = brightness
 * vec4 color1;          // reserved
 */
public enum UIBlurUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static final Data DATA = new Data();
    private static final String UNIFORM_NAME = "Combatant - UIBlur UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 64;

    public static void update(float screenW, float screenH,
                              float w, float h,
                              float x, float y,
                              float radius, float quality, float brightness, float alpha,
                              int rgb) {
        DATA.inputResolution[0] = screenW;
        DATA.inputResolution[1] = screenH;
        DATA.inputResolution[2] = 0f;
        DATA.inputResolution[3] = 0f;

        DATA.size[0] = w;
        DATA.size[1] = h;
        DATA.size[2] = 0f;
        DATA.size[3] = 0f;

        DATA.location[0] = x;
        DATA.location[1] = y;
        DATA.location[2] = 0f;
        DATA.location[3] = 0f;

        DATA.params[0] = radius;
        DATA.params[1] = quality;
        DATA.params[2] = brightness;
        DATA.params[3] = alpha;

        DATA.color1[0] = ((rgb >>> 16) & 0xFF) / 255f;
        DATA.color1[1] = ((rgb >>> 8) & 0xFF) / 255f;
        DATA.color1[2] = (rgb & 0xFF) / 255f;
        DATA.color1[3] = 1f;

        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private final float[] inputResolution = new float[4];
        private final float[] size = new float[4];
        private final float[] location = new float[4];
        private final float[] params = new float[4];
        private final float[] color1 = new float[4];

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder b = Std140Builder.intoBuffer(buffer);
            b.putFloat(inputResolution[0]).putFloat(inputResolution[1]).putFloat(inputResolution[2]).putFloat(inputResolution[3]);
            b.putFloat(size[0]).putFloat(size[1]).putFloat(size[2]).putFloat(size[3]);
            b.putFloat(location[0]).putFloat(location[1]).putFloat(location[2]).putFloat(location[3]);
            b.putFloat(params[0]).putFloat(params[1]).putFloat(params[2]).putFloat(params[3]);
            b.putFloat(color1[0]).putFloat(color1[1]).putFloat(color1[2]).putFloat(color1[3]);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }

}
