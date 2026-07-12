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

public enum SkySunUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putFloat()
            .putFloat()
            .putFloat()
            .putFloat()
            .get();

    private static final Data DATA = new Data();
    private static final String UNIFORM_NAME = "Combatant - SkySun UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 16;

    public static void update(float innerRatio, float haloStrength, float coreStrength, float rayStrength) {
        DATA.innerRatio = innerRatio;
        DATA.haloStrength = haloStrength;
        DATA.coreStrength = coreStrength;
        DATA.rayStrength = rayStrength;
        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private float innerRatio;
        private float haloStrength;
        private float coreStrength;
        private float rayStrength;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(innerRatio)
                    .putFloat(haloStrength)
                    .putFloat(coreStrength)
                    .putFloat(rayStrength);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }

}
