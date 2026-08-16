/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.uniform.impl;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;

/** Std140 UBO dedicated to the independent low-health and hit-impact channels. */
public enum DamageTintUniforms {
    ;

    public static final int SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static final Data DATA = new Data();
    private static final String UNIFORM_NAME = "Combatant - DamageTint UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 4;

    public static void update(float lowHealthStrength,
                              float desaturation,
                              float contrast,
                              float lowPulse,
                              float impactStrength,
                              float redPressure,
                              float edgeFlash,
                              float chromatic,
                              float directionX,
                              float directionY,
                              boolean directional,
                              float distortion) {
        DATA.lowHealth[0] = lowHealthStrength;
        DATA.lowHealth[1] = desaturation;
        DATA.lowHealth[2] = contrast;
        DATA.lowHealth[3] = lowPulse;

        DATA.impact[0] = impactStrength;
        DATA.impact[1] = redPressure;
        DATA.impact[2] = edgeFlash;
        DATA.impact[3] = chromatic;

        DATA.direction[0] = directionX;
        DATA.direction[1] = directionY;
        DATA.direction[2] = directional ? 1.0f : 0.0f;
        DATA.direction[3] = distortion;

        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private final float[] lowHealth = new float[4];
        private final float[] impact = new float[4];
        private final float[] direction = new float[4];

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder builder = Std140Builder.intoBuffer(buffer);
            builder.putFloat(lowHealth[0]).putFloat(lowHealth[1]).putFloat(lowHealth[2]).putFloat(lowHealth[3]);
            builder.putFloat(impact[0]).putFloat(impact[1]).putFloat(impact[2]).putFloat(impact[3]);
            builder.putFloat(direction[0]).putFloat(direction[1]).putFloat(direction[2]).putFloat(direction[3]);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }
}
