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

/**
 * Std140 UBO for first-person hand liquid-glass overlay.
 * <p>
 * Layout:
 * vec4 uScreen;   // x = framebuffer width, y = framebuffer height, z = time, w = inner edge width in pixels
 * vec4 uTint;     // rgb = glass tint, a = overlay alpha
 * vec4 uMaterial; // x = strength, y = refraction pixels, z = edge haze strength, w = frost blur radius pixels
 * vec4 uReserved; // x = body frost strength, y = chromatic pixels, z = edge refraction multiplier, w = clarity
 */
public enum HandGlassUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static final Data DATA = new Data();
    private static final String UNIFORM_NAME = "Combatant - HandGlass UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 16;

    public static void update(
            float screenW, float screenH, float time, float edgeWidth,
            float tintR, float tintG, float tintB, float alpha,
            float strength, float refractionPx, float hazeStrength, float reserved0,
            float reserved1, float reserved2, float reserved3, float reserved4
    ) {
        DATA.screenW = screenW;
        DATA.screenH = screenH;
        DATA.time = time;
        DATA.edgeWidth = edgeWidth;

        DATA.tintR = tintR;
        DATA.tintG = tintG;
        DATA.tintB = tintB;
        DATA.alpha = alpha;

        DATA.strength = strength;
        DATA.refractionPx = refractionPx;
        DATA.hazeStrength = hazeStrength;
        DATA.reserved0 = reserved0;

        DATA.reserved1 = reserved1;
        DATA.reserved2 = reserved2;
        DATA.reserved3 = reserved3;
        DATA.reserved4 = reserved4;

        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private float screenW, screenH, time, edgeWidth;
        private float tintR, tintG, tintB, alpha;
        private float strength, refractionPx, hazeStrength, reserved0;
        private float reserved1, reserved2, reserved3, reserved4;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(screenW).putFloat(screenH).putFloat(time).putFloat(edgeWidth)
                    .putFloat(tintR).putFloat(tintG).putFloat(tintB).putFloat(alpha)
                    .putFloat(strength).putFloat(refractionPx).putFloat(hazeStrength).putFloat(reserved0)
                    .putFloat(reserved1).putFloat(reserved2).putFloat(reserved3).putFloat(reserved4);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }

}
