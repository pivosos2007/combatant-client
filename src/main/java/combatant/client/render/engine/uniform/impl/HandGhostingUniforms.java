/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.uniform.impl;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
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
 * vec4 u_Noise0; // x = quality, y = octaves, z = flow speed, w = noise scale
 * vec4 u_Noise1; // x = swirl, y = contrast, z = density, w = history resolution scale
 * vec4 u_PaletteEnd;     // secondary RGBA color
 * vec4 u_PaletteOptions; // mode, cycles/sec, spatial span, angle in degrees
 * vec4 u_GhostDetails;   // smoke amount, ring intensity, ring frequency, ring speed
 * </pre>
 */
public enum HandGhostingUniforms {
    ;

    public static final int SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4() // palette secondary color
            .putVec4() // palette mode, speed, span, angle
            .putVec4() // smoke, ring intensity, frequency, speed
            .get();

    private static final String UNIFORM_NAME = "Combatant - Hand Ghosting UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 8;
    private static final Data DATA = new Data();

    public static void update(
            float screenW, float screenH, float deltaSeconds, float time,
            float colorR, float colorG, float colorB, float colorA,
            float decay, float strength, float blurPx, float currentReject,
            float quality, float octaves, float speed, float scale,
            float swirl, float contrast, float density, float historyScale,
            float paletteEndR, float paletteEndG, float paletteEndB, float paletteEndA,
            float paletteMode, float paletteSpeed, float paletteSpan, float paletteAngle,
            float smokeAmount, float ringAmount, float ringFrequency, float ringSpeed
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
        DATA.quality = quality;
        DATA.octaves = octaves;
        DATA.speed = speed;
        DATA.scale = scale;
        DATA.swirl = swirl;
        DATA.contrast = contrast;
        DATA.density = density;
        DATA.historyScale = historyScale;
        DATA.paletteEndR = paletteEndR; DATA.paletteEndG = paletteEndG;
        DATA.paletteEndB = paletteEndB; DATA.paletteEndA = paletteEndA;
        DATA.paletteMode = paletteMode; DATA.paletteSpeed = paletteSpeed;
        DATA.paletteSpan = paletteSpan; DATA.paletteAngle = paletteAngle;
        DATA.smokeAmount = smokeAmount; DATA.ringAmount = ringAmount;
        DATA.ringFrequency = ringFrequency; DATA.ringSpeed = ringSpeed;
        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private float screenW, screenH, deltaSeconds, time;
        private float colorR, colorG, colorB, colorA;
        private float decay, strength, blurPx, currentReject;
        private float quality, octaves, speed, scale;
        private float swirl, contrast, density, historyScale;
        private float paletteEndR, paletteEndG, paletteEndB, paletteEndA;
        private float paletteMode, paletteSpeed, paletteSpan, paletteAngle;
        private float smokeAmount, ringAmount, ringFrequency, ringSpeed;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(screenW).putFloat(screenH).putFloat(deltaSeconds).putFloat(time)
                    .putFloat(colorR).putFloat(colorG).putFloat(colorB).putFloat(colorA)
                    .putFloat(decay).putFloat(strength).putFloat(blurPx).putFloat(currentReject)
                    .putFloat(quality).putFloat(octaves).putFloat(speed).putFloat(scale)
                    .putFloat(swirl).putFloat(contrast).putFloat(density).putFloat(historyScale)
                    .putFloat(paletteEndR).putFloat(paletteEndG).putFloat(paletteEndB).putFloat(paletteEndA)
                    .putFloat(paletteMode).putFloat(paletteSpeed).putFloat(paletteSpan).putFloat(paletteAngle)
                    .putFloat(smokeAmount).putFloat(ringAmount).putFloat(ringFrequency).putFloat(ringSpeed);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }
}
