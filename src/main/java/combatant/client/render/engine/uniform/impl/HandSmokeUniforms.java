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

public enum HandSmokeUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putFloat().putFloat().putFloat().putFloat() // u_Fill
            .putFloat().putFloat().putFloat().putFloat() // u_Glow
            .putFloat().putFloat().putFloat().putFloat() // u_Shadow
            .putFloat().putFloat().putFloat().putFloat() // u_Params0
            .putFloat().putFloat().putFloat().putFloat() // u_Params1
            .putFloat().putFloat().putFloat().putFloat() // u_Params2
            .get();

    private static final Data DATA = new Data();
    private static final String UNIFORM_NAME = "Combatant - Hand Smoke UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 16;

    public static void update(
            float fillR, float fillG, float fillB, float fillA,
            float glowR, float glowG, float glowB, float glowA,
            float shadowR, float shadowG, float shadowB, float shadowA,
            float edgeWidth, float quality, float octaves, float time,
            float resX, float resY, float scale, float contrast,
            float swirl, float glowStrength, float shadowStrength, float density
    ) {
        DATA.fillR = fillR;
        DATA.fillG = fillG;
        DATA.fillB = fillB;
        DATA.fillA = fillA;
        DATA.glowR = glowR;
        DATA.glowG = glowG;
        DATA.glowB = glowB;
        DATA.glowA = glowA;
        DATA.shadowR = shadowR;
        DATA.shadowG = shadowG;
        DATA.shadowB = shadowB;
        DATA.shadowA = shadowA;
        DATA.edgeWidth = edgeWidth;
        DATA.quality = quality;
        DATA.octaves = octaves;
        DATA.time = time;
        DATA.resX = resX;
        DATA.resY = resY;
        DATA.scale = scale;
        DATA.contrast = contrast;
        DATA.swirl = swirl;
        DATA.glowStrength = glowStrength;
        DATA.shadowStrength = shadowStrength;
        DATA.density = density;
        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private float fillR, fillG, fillB, fillA;
        private float glowR, glowG, glowB, glowA;
        private float shadowR, shadowG, shadowB, shadowA;
        private float edgeWidth, quality, octaves, time;
        private float resX, resY, scale, contrast;
        private float swirl, glowStrength, shadowStrength, density;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(fillR).putFloat(fillG).putFloat(fillB).putFloat(fillA)
                    .putFloat(glowR).putFloat(glowG).putFloat(glowB).putFloat(glowA)
                    .putFloat(shadowR).putFloat(shadowG).putFloat(shadowB).putFloat(shadowA)
                    .putFloat(edgeWidth).putFloat(quality).putFloat(octaves).putFloat(time)
                    .putFloat(resX).putFloat(resY).putFloat(scale).putFloat(contrast)
                    .putFloat(swirl).putFloat(glowStrength).putFloat(shadowStrength).putFloat(density);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }

}
