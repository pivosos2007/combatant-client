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

/**
 * Uniform block shared by the hand ghost history/update and composite passes.
 *
 * <pre>
 * vec4 u_Screen; // xy = framebuffer size, z = frame delta seconds, w = time seconds
 * vec4 u_Color;  // rgba = resolved AnimatedRenderColors output
 * vec4 u_Params; // x = temporal decay, y = strength, z = blur radius px, w = current-mask rejection
 * </pre>
 */
public enum HandGhostingUniforms {
    ;

    public static final int SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static final String UNIFORM_NAME = "Combatant - Hand Ghosting UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 8;
    private static final Data DATA = new Data();

    public static void update(
            float screenW, float screenH, float deltaSeconds, float time,
            float colorR, float colorG, float colorB, float colorA,
            float decay, float strength, float blurPx, float currentReject
    ) {
        DATA.screenW = screenW;
        DATA.screenH = screenH;
        DATA.deltaSeconds = deltaSeconds;
        DATA.time = time;
        DATA.colorR = colorR;
        DATA.colorG = colorG;
        DATA.colorB = colorB;
        DATA.colorA = colorA;
        DATA.decay = decay;
        DATA.strength = strength;
        DATA.blurPx = blurPx;
        DATA.currentReject = currentReject;
        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private float screenW, screenH, deltaSeconds, time;
        private float colorR, colorG, colorB, colorA;
        private float decay, strength, blurPx, currentReject;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(screenW).putFloat(screenH).putFloat(deltaSeconds).putFloat(time)
                    .putFloat(colorR).putFloat(colorG).putFloat(colorB).putFloat(colorA)
                    .putFloat(decay).putFloat(strength).putFloat(blurPx).putFloat(currentReject);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }
}
