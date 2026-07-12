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

public enum ShaderEspGradientUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static final Data DATA = new Data();
    private static final String UNIFORM_NAME = "Combatant - ShaderESP Gradient UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 64;

    public static void update(float x, float y, float width, float height,
                              int color1, int color2, int color3, int color4) {
        float darkMultiplier = (((color2 >>> 16) & 0xFF) + ((color2 >>> 8) & 0xFF) + (color2 & 0xFF)) / (255.0f * 3.0f);
        update(x, y, width, height, color1, darkMultiplier, 0.0f, 1.0f);
    }

    public static void update(float x, float y, float width, float height,
                              int passColor, float darkMultiplier, float overrideColor, float intensity) {
        DATA.rect[0] = x;
        DATA.rect[1] = y;
        DATA.rect[2] = Math.max(1.0f, width);
        DATA.rect[3] = Math.max(1.0f, height);

        putColor(DATA.color1, passColor);
        DATA.color2[0] = Math.max(0.0f, Math.min(2.0f, darkMultiplier));
        DATA.color2[1] = 0.0f;
        DATA.color2[2] = 0.0f;
        DATA.color2[3] = 0.0f;
        DATA.color3[0] = Math.max(0.0f, Math.min(1.0f, overrideColor));
        DATA.color3[1] = Math.max(0.0f, Math.min(4.0f, intensity));
        DATA.color3[2] = 0.0f;
        DATA.color3[3] = 0.0f;
        DATA.color4[0] = 0.0f;
        DATA.color4[1] = 0.0f;
        DATA.color4[2] = 0.0f;
        DATA.color4[3] = 0.0f;

        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static void putColor(float[] dst, int argb) {
        dst[0] = ((argb >>> 16) & 0xFF) / 255.0f;
        dst[1] = ((argb >>> 8) & 0xFF) / 255.0f;
        dst[2] = (argb & 0xFF) / 255.0f;
        dst[3] = ((argb >>> 24) & 0xFF) / 255.0f;
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private final float[] rect = new float[4];
        private final float[] color1 = new float[4];
        private final float[] color2 = new float[4];
        private final float[] color3 = new float[4];
        private final float[] color4 = new float[4];

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(rect[0]).putFloat(rect[1]).putFloat(rect[2]).putFloat(rect[3])
                    .putFloat(color1[0]).putFloat(color1[1]).putFloat(color1[2]).putFloat(color1[3])
                    .putFloat(color2[0]).putFloat(color2[1]).putFloat(color2[2]).putFloat(color2[3])
                    .putFloat(color3[0]).putFloat(color3[1]).putFloat(color3[2]).putFloat(color3[3])
                    .putFloat(color4[0]).putFloat(color4[1]).putFloat(color4[2]).putFloat(color4[3]);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }

}
