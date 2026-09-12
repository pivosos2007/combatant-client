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

public enum ShaderEspSmokeUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static final Data DATA = new Data();
    private static final String UNIFORM_NAME = "Combatant - ShaderESP Smoke UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 16;

    public static void update(float x, float y, float width, float height,
                              float time, float scale, float speed, float alpha,
                              int octaves, float contrast, float overrideColor, float intensity,
                              int primaryColor, int secondaryColor,
                              int colorMode, float baseAngleDeg, float spatialSpreadDeg, float gradientAngleDeg) {
        DATA.rect[0] = x;
        DATA.rect[1] = y;
        DATA.rect[2] = Math.max(1.0f, width);
        DATA.rect[3] = Math.max(1.0f, height);

        DATA.params0[0] = time;
        DATA.params0[1] = Math.max(0.05f, scale);
        DATA.params0[2] = Math.max(0.0f, speed);
        DATA.params0[3] = Math.max(0.0f, Math.min(1.0f, alpha));

        DATA.params1[0] = Math.max(1.0f, Math.min(6.0f, octaves));
        DATA.params1[1] = Math.max(0.05f, Math.min(4.0f, contrast));
        DATA.params1[2] = Math.max(0.0f, Math.min(1.0f, overrideColor));
        DATA.params1[3] = Math.max(0.0f, Math.min(4.0f, intensity));

        putColor(DATA.primaryColor, primaryColor);
        putColor(DATA.secondaryColor, secondaryColor);

        DATA.colorParams[0] = colorMode;
        DATA.colorParams[1] = baseAngleDeg;
        DATA.colorParams[2] = spatialSpreadDeg;
        DATA.colorParams[3] = gradientAngleDeg;

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
        private final float[] params0 = new float[4];
        private final float[] params1 = new float[4];
        private final float[] primaryColor = new float[4];
        private final float[] secondaryColor = new float[4];
        private final float[] colorParams = new float[4];

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(rect[0]).putFloat(rect[1]).putFloat(rect[2]).putFloat(rect[3])
                    .putFloat(params0[0]).putFloat(params0[1]).putFloat(params0[2]).putFloat(params0[3])
                    .putFloat(params1[0]).putFloat(params1[1]).putFloat(params1[2]).putFloat(params1[3])
                    .putFloat(primaryColor[0]).putFloat(primaryColor[1]).putFloat(primaryColor[2]).putFloat(primaryColor[3])
                    .putFloat(secondaryColor[0]).putFloat(secondaryColor[1]).putFloat(secondaryColor[2]).putFloat(secondaryColor[3])
                    .putFloat(colorParams[0]).putFloat(colorParams[1]).putFloat(colorParams[2]).putFloat(colorParams[3]);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }
}
