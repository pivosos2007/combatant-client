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

public enum SkyboxShaderUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static final Data DATA = new Data();
    private static final String UNIFORM_NAME = "Combatant - SkyboxShader UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 16;
    private static final int DEFAULT_LAYER_MASK = (1 << 17) - 1;
    private static int lastShaderRgb;
    private static int lastSkyRgb;
    private static float lastAlpha;
    private static float lastSkyFogBlend;
    private static float lastSpeed;
    private static float lastScale;
    private static float lastIntensity;
    private static float lastResolutionX;
    private static float lastResolutionY;
    private static float lastYawRad;
    private static float lastPitchRad;
    private static float lastFov;
    private static float lastAuroraEnabled;
    private static float lastAuroraIntensity;
    private static float lastAuroraSpeed;
    private static float lastSmallStars;
    private static float lastDustStars;
    private static float lastMediumStars;
    private static float lastLargeStars;
    private static float lastStarBrightness;
    private static float lastTwinkleStrength;
    private static int lastLayerMask;
    private static boolean lastValid;

    public static void update(int shaderRgb,
                              int skyRgb,
                              float alpha,
                              float skyFogBlend,
                              float time,
                              float speed,
                              float scale,
                              float intensity,
                              float resolutionX,
                              float resolutionY,
                              float yawRad,
                              float pitchRad,
                              float fov) {
        update(shaderRgb, skyRgb, alpha, skyFogBlend, time, speed, scale, intensity,
                resolutionX, resolutionY, yawRad, pitchRad, fov, 0.0f, 0.0f, 0.0f,
                1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, DEFAULT_LAYER_MASK);
    }

    public static void update(int shaderRgb,
                              int skyRgb,
                              float alpha,
                              float skyFogBlend,
                              float time,
                              float speed,
                              float scale,
                              float intensity,
                              float resolutionX,
                              float resolutionY,
                              float yawRad,
                              float pitchRad,
                              float fov,
                              float auroraEnabled,
                              float auroraIntensity,
                              float auroraSpeed,
                              float smallStars,
                              float dustStars,
                              float mediumStars,
                              float largeStars,
                              float starBrightness,
                              float twinkleStrength,
                              int layerMask) {
        DATA.color[0] = ((shaderRgb >>> 16) & 0xFF) / 255f;
        DATA.color[1] = ((shaderRgb >>> 8) & 0xFF) / 255f;
        DATA.color[2] = (shaderRgb & 0xFF) / 255f;
        DATA.color[3] = alpha;

        DATA.sky[0] = ((skyRgb >>> 16) & 0xFF) / 255f;
        DATA.sky[1] = ((skyRgb >>> 8) & 0xFF) / 255f;
        DATA.sky[2] = (skyRgb & 0xFF) / 255f;
        DATA.sky[3] = Math.max(0.0f, Math.min(1.0f, skyFogBlend));

        DATA.params[0] = time;
        DATA.params[1] = speed;
        DATA.params[2] = scale;
        DATA.params[3] = intensity;

        DATA.view[0] = resolutionX;
        DATA.view[1] = resolutionY;
        DATA.view[2] = yawRad;
        DATA.view[3] = pitchRad;

        DATA.view2[0] = fov;
        DATA.view2[1] = auroraEnabled > 0.5f ? 1.0f : 0.0f;
        DATA.view2[2] = Math.max(0.0f, Math.min(1.5f, auroraIntensity));
        DATA.view2[3] = Math.max(0.0f, Math.min(2.0f, auroraSpeed));

        DATA.starCounts[0] = clampStarAmount(smallStars);
        DATA.starCounts[1] = clampStarAmount(dustStars);
        DATA.starCounts[2] = clampStarAmount(mediumStars);
        DATA.starCounts[3] = clampStarAmount(largeStars);

        DATA.starStyle[0] = Math.max(0.0f, Math.min(3.0f, starBrightness));
        DATA.starStyle[1] = Math.max(0.0f, Math.min(3.0f, twinkleStrength));
        DATA.starStyle[2] = 0.0f;
        DATA.starStyle[3] = 0.0f;

        int sanitizedLayerMask = layerMask & DEFAULT_LAYER_MASK;
        DATA.layers[0] = sanitizedLayerMask;
        DATA.layers[1] = 0.0f;
        DATA.layers[2] = 0.0f;
        DATA.layers[3] = 0.0f;

        boolean force = !lastValid
                || lastShaderRgb != shaderRgb
                || lastSkyRgb != skyRgb
                || changed(lastAlpha, alpha)
                || changed(lastSkyFogBlend, skyFogBlend)
                || changed(lastSpeed, speed)
                || changed(lastScale, scale)
                || changed(lastIntensity, intensity)
                || changed(lastResolutionX, resolutionX)
                || changed(lastResolutionY, resolutionY)
                || changed(lastYawRad, yawRad)
                || changed(lastPitchRad, pitchRad)
                || changed(lastFov, fov)
                || changed(lastAuroraEnabled, DATA.view2[1])
                || changed(lastAuroraIntensity, DATA.view2[2])
                || changed(lastAuroraSpeed, DATA.view2[3])
                || changed(lastSmallStars, DATA.starCounts[0])
                || changed(lastDustStars, DATA.starCounts[1])
                || changed(lastMediumStars, DATA.starCounts[2])
                || changed(lastLargeStars, DATA.starCounts[3])
                || changed(lastStarBrightness, DATA.starStyle[0])
                || changed(lastTwinkleStrength, DATA.starStyle[1])
                || lastLayerMask != sanitizedLayerMask;

        CombatantRenderSystem.uniforms().writeCached(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME,
                1, force, DATA);

        lastShaderRgb = shaderRgb;
        lastSkyRgb = skyRgb;
        lastAlpha = alpha;
        lastSkyFogBlend = skyFogBlend;
        lastSpeed = speed;
        lastScale = scale;
        lastIntensity = intensity;
        lastResolutionX = resolutionX;
        lastResolutionY = resolutionY;
        lastYawRad = yawRad;
        lastPitchRad = pitchRad;
        lastFov = fov;
        lastAuroraEnabled = DATA.view2[1];
        lastAuroraIntensity = DATA.view2[2];
        lastAuroraSpeed = DATA.view2[3];
        lastSmallStars = DATA.starCounts[0];
        lastDustStars = DATA.starCounts[1];
        lastMediumStars = DATA.starCounts[2];
        lastLargeStars = DATA.starCounts[3];
        lastStarBrightness = DATA.starStyle[0];
        lastTwinkleStrength = DATA.starStyle[1];
        lastLayerMask = sanitizedLayerMask;
        lastValid = true;
    }

    private static float clampStarAmount(float value) {
        return Math.max(0.0f, Math.min(10.0f, value));
    }

    private static boolean changed(float a, float b) {
        return Math.abs(a - b) > 0.0001f;
    }

    public static void update(int shaderRgb,
                              int skyRgb,
                              float alpha,
                              float skyFogBlend,
                              float time,
                              float speed,
                              float scale,
                              float intensity) {
        update(shaderRgb, skyRgb, alpha, skyFogBlend, time, speed, scale, intensity,
                1.0f, 1.0f, 0.0f, 0.0f, 70.0f);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private final float[] color = new float[4];
        private final float[] sky = new float[4];
        private final float[] params = new float[4];
        private final float[] view = new float[4];
        private final float[] view2 = new float[4];
        private final float[] starCounts = new float[4];
        private final float[] starStyle = new float[4];
        private final float[] layers = new float[4];

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(color[0]).putFloat(color[1]).putFloat(color[2]).putFloat(color[3])
                    .putFloat(sky[0]).putFloat(sky[1]).putFloat(sky[2]).putFloat(sky[3])
                    .putFloat(params[0]).putFloat(params[1]).putFloat(params[2]).putFloat(params[3])
                    .putFloat(view[0]).putFloat(view[1]).putFloat(view[2]).putFloat(view[3])
                    .putFloat(view2[0]).putFloat(view2[1]).putFloat(view2[2]).putFloat(view2[3])
                    .putFloat(starCounts[0]).putFloat(starCounts[1]).putFloat(starCounts[2]).putFloat(starCounts[3])
                    .putFloat(starStyle[0]).putFloat(starStyle[1]).putFloat(starStyle[2]).putFloat(starStyle[3])
                    .putFloat(layers[0]).putFloat(layers[1]).putFloat(layers[2]).putFloat(layers[3]);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }

}
