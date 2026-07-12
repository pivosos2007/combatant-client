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

public enum ShaderEspBlurUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .get();

    private static final Data DATA = new Data();
    private static final String UNIFORM_NAME = "Combatant - ShaderESP Blur UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 64;

    public static void update(float screenW, float screenH, float radius, float directionX, float directionY) {
        float safeW = Math.max(1.0f, screenW);
        float safeH = Math.max(1.0f, screenH);
        float safeRadius = Math.max(0.0f, Math.min(63.0f, radius));

        DATA.texelRadius[0] = 1.0f / safeW;
        DATA.texelRadius[1] = 1.0f / safeH;
        DATA.texelRadius[2] = safeRadius;
        DATA.texelRadius[3] = Math.max(0.5f, safeRadius * 0.5f);

        DATA.direction[0] = directionX;
        DATA.direction[1] = directionY;
        DATA.direction[2] = 0.0f;
        DATA.direction[3] = 0.0f;

        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private final float[] texelRadius = new float[4];
        private final float[] direction = new float[4];

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(texelRadius[0]).putFloat(texelRadius[1]).putFloat(texelRadius[2]).putFloat(texelRadius[3])
                    .putFloat(direction[0]).putFloat(direction[1]).putFloat(direction[2]).putFloat(direction[3]);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }

}
