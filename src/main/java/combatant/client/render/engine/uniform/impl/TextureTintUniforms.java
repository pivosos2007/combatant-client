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
import net.minecraft.util.Mth;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;

public enum TextureTintUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .get();

    private static final Data DATA = new Data();
    private static final String UNIFORM_NAME = "Combatant - TextureTint UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 16;

    public static void update(int tintRgb, float strength, int mode) {
        DATA.tint[0] = ((tintRgb >>> 16) & 0xFF) / 255.0f;
        DATA.tint[1] = ((tintRgb >>> 8) & 0xFF) / 255.0f;
        DATA.tint[2] = (tintRgb & 0xFF) / 255.0f;
        DATA.tint[3] = Mth.clamp(strength, 0.0f, 1.0f);

        DATA.params[0] = mode;
        DATA.params[1] = 0.0f;
        DATA.params[2] = 0.0f;
        DATA.params[3] = 0.0f;

        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private final float[] tint = new float[4];
        private final float[] params = new float[4];

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(tint[0]).putFloat(tint[1]).putFloat(tint[2]).putFloat(tint[3])
                    .putFloat(params[0]).putFloat(params[1]).putFloat(params[2]).putFloat(params[3]);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }

}
