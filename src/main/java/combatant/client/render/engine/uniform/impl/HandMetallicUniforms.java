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

public enum HandMetallicUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putFloat().putFloat().putFloat().putFloat() // u_Base
            .putFloat().putFloat().putFloat().putFloat() // u_Highlight
            .putFloat().putFloat().putFloat().putFloat() // u_Glow
            .putFloat().putFloat().putFloat().putFloat() // u_Shadow
            .putFloat().putFloat().putFloat().putFloat() // u_Params0
            .putFloat().putFloat().putFloat().putFloat() // u_Params1
            .putFloat().putFloat().putFloat().putFloat() // u_Params2
            .get();

    private static final Data DATA = new Data();
    private static final String UNIFORM_NAME = "Combatant - Hand Metallic UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 16;

    public static void update(
            float baseR, float baseG, float baseB, float fillA,
            float highlightR, float highlightG, float highlightB, float highlightA,
            float glowR, float glowG, float glowB, float glowA,
            float shadowR, float shadowG, float shadowB, float shadowA,
            float intensity, float sharpness, float edgeStrength, float time,
            float sweepSpeed, float sweepScale, float brushedLines, float flakes,
            float glowStrength, float shadowStrength, float edgeWidth, float prism
    ) {
        DATA.baseR = baseR;
        DATA.baseG = baseG;
        DATA.baseB = baseB;
        DATA.fillA = fillA;
        DATA.highlightR = highlightR;
        DATA.highlightG = highlightG;
        DATA.highlightB = highlightB;
        DATA.highlightA = highlightA;
        DATA.glowR = glowR;
        DATA.glowG = glowG;
        DATA.glowB = glowB;
        DATA.glowA = glowA;
        DATA.shadowR = shadowR;
        DATA.shadowG = shadowG;
        DATA.shadowB = shadowB;
        DATA.shadowA = shadowA;
        DATA.intensity = intensity;
        DATA.sharpness = sharpness;
        DATA.edgeStrength = edgeStrength;
        DATA.time = time;
        DATA.sweepSpeed = sweepSpeed;
        DATA.sweepScale = sweepScale;
        DATA.brushedLines = brushedLines;
        DATA.flakes = flakes;
        DATA.glowStrength = glowStrength;
        DATA.shadowStrength = shadowStrength;
        DATA.edgeWidth = edgeWidth;
        DATA.prism = prism;
        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private float baseR, baseG, baseB, fillA;
        private float highlightR, highlightG, highlightB, highlightA;
        private float glowR, glowG, glowB, glowA;
        private float shadowR, shadowG, shadowB, shadowA;
        private float intensity, sharpness, edgeStrength, time;
        private float sweepSpeed, sweepScale, brushedLines, flakes;
        private float glowStrength, shadowStrength, edgeWidth, prism;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(baseR).putFloat(baseG).putFloat(baseB).putFloat(fillA)
                    .putFloat(highlightR).putFloat(highlightG).putFloat(highlightB).putFloat(highlightA)
                    .putFloat(glowR).putFloat(glowG).putFloat(glowB).putFloat(glowA)
                    .putFloat(shadowR).putFloat(shadowG).putFloat(shadowB).putFloat(shadowA)
                    .putFloat(intensity).putFloat(sharpness).putFloat(edgeStrength).putFloat(time)
                    .putFloat(sweepSpeed).putFloat(sweepScale).putFloat(brushedLines).putFloat(flakes)
                    .putFloat(glowStrength).putFloat(shadowStrength).putFloat(edgeWidth).putFloat(prism);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }

}
