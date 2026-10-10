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

/** Parameters for the silhouette-driven first-person spatial-rift material. */
public enum HandRiftUniforms {
    ;

    public static final int SIZE = new Std140SizeCalculator()
            .putVec4() // fill
            .putVec4() // caustic
            .putVec4() // shadow
            .putVec4() // screen
            .putVec4() // warp
            .putVec4() // effects
            .get();

    private static final String UNIFORM_NAME = "Combatant - Hand Rift UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 16;
    private static final Data DATA = new Data();

    public static void update(
            float fillR, float fillG, float fillB, float fillA,
            float causticR, float causticG, float causticB, float causticA,
            float shadowR, float shadowG, float shadowB, float shadowA,
            float screenW, float screenH, float time, float fieldWidth,
            float distortionPx, float flowSpeed, float caustics, float quality,
            float glowStrength, float shadowStrength, float reserved0, float reserved1
    ) {
        DATA.fillR = fillR;
        DATA.fillG = fillG;
        DATA.fillB = fillB;
        DATA.fillA = fillA;
        DATA.causticR = causticR;
        DATA.causticG = causticG;
        DATA.causticB = causticB;
        DATA.causticA = causticA;
        DATA.shadowR = shadowR;
        DATA.shadowG = shadowG;
        DATA.shadowB = shadowB;
        DATA.shadowA = shadowA;
        DATA.screenW = screenW;
        DATA.screenH = screenH;
        DATA.time = time;
        DATA.fieldWidth = fieldWidth;
        DATA.distortionPx = distortionPx;
        DATA.flowSpeed = flowSpeed;
        DATA.caustics = caustics;
        DATA.quality = quality;
        DATA.glowStrength = glowStrength;
        DATA.shadowStrength = shadowStrength;
        DATA.reserved0 = reserved0;
        DATA.reserved1 = reserved1;
        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private float fillR, fillG, fillB, fillA;
        private float causticR, causticG, causticB, causticA;
        private float shadowR, shadowG, shadowB, shadowA;
        private float screenW, screenH, time, fieldWidth;
        private float distortionPx, flowSpeed, caustics, quality;
        private float glowStrength, shadowStrength, reserved0, reserved1;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(fillR).putFloat(fillG).putFloat(fillB).putFloat(fillA)
                    .putFloat(causticR).putFloat(causticG).putFloat(causticB).putFloat(causticA)
                    .putFloat(shadowR).putFloat(shadowG).putFloat(shadowB).putFloat(shadowA)
                    .putFloat(screenW).putFloat(screenH).putFloat(time).putFloat(fieldWidth)
                    .putFloat(distortionPx).putFloat(flowSpeed).putFloat(caustics).putFloat(quality)
                    .putFloat(glowStrength).putFloat(shadowStrength).putFloat(reserved0).putFloat(reserved1);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }
}
