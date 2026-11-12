/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.uniform.impl;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;
import combatant.client.render.sky.DreamSkyPalette;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;

/** Dream sky matches the actual world projection rather than an Euler approximation. */
public enum SkyboxShaderUniforms {
    ;
    private static final String NAME = "Combatant - Dream Sky UBO";
    private static final int SIZE = new Std140SizeCalculator()
            .putMat4f().putMat4f().putVec4().putVec4().putVec4().putVec4()
            .putVec4().putVec4().putVec4().putVec4()
            .putVec4().putVec4().putVec4().putVec4().putVec4()
            .putVec4().putVec4().putVec4().putVec4().putVec4().get();
    private static final Data DATA = new Data();

    public static void update(Matrix4f inverseProjection, Matrix4f inverseView,
                              float skyR, float skyG, float skyB, float night,
                              float width, float height,
                              float sunAngle, float moonAngle, float starAngle, float time,
                              float rain, float fogStrength,
                              float cameraX, float cameraY, float cameraZ,
                              float baseHeight, float thickness, float coverage, float scale,
                              float starSeedX, float starSeedY, float cloudSeedX, float cloudSeedZ,
                              boolean distantClouds, boolean cirrus, float cloudDensity,
                              DreamSkyPalette palette, float thunder, float auroraIntensity, float auroraSpeed) {
        DATA.inverseProjection.set(inverseProjection);
        DATA.inverseView.set(inverseView);
        DATA.sky[0] = skyR;
        DATA.sky[1] = skyG;
        DATA.sky[2] = skyB;
        DATA.sky[3] = night;
        DATA.view[0] = width;
        DATA.view[1] = height;
        DATA.view[2] = DATA.view[3] = 0f;
        DATA.celestial[0] = sunAngle;
        DATA.celestial[1] = moonAngle;
        DATA.celestial[2] = starAngle;
        DATA.celestial[3] = time;
        DATA.weather[0] = rain;
        DATA.weather[1] = thunder;
        DATA.weather[2] = fogStrength;
        DATA.weather[3] = auroraIntensity;
        DATA.world[0] = cameraX;
        DATA.world[1] = cameraY;
        DATA.world[2] = cameraZ;
        DATA.world[3] = time;
        DATA.clouds[0] = baseHeight;
        DATA.clouds[1] = thickness;
        DATA.clouds[2] = coverage;
        DATA.clouds[3] = scale;
        DATA.layout[0] = starSeedX;
        DATA.layout[1] = starSeedY;
        DATA.layout[2] = cloudSeedX;
        DATA.layout[3] = cloudSeedZ;
        DATA.cloudOptions[0] = distantClouds ? 1f : 0f;
        DATA.cloudOptions[1] = cirrus ? 1f : 0f;
        DATA.cloudOptions[2] = cloudDensity;
        DATA.cloudOptions[3] = auroraSpeed;
        int[] colors = {palette.dayZenith(), palette.dayHorizon(), palette.nightZenith(),
                palette.nightHorizon(), palette.glow(), palette.stars(), palette.cloudLight(),
                palette.cloudShadow(), palette.auroraPrimary(), palette.auroraSecondary()};
        for (int i = 0; i < colors.length; i++) {
            DATA.palette[i * 4] = DreamSkyPalette.linearRed(colors[i]);
            DATA.palette[i * 4 + 1] = DreamSkyPalette.linearGreen(colors[i]);
            DATA.palette[i * 4 + 2] = DreamSkyPalette.linearBlue(colors[i]);
            DATA.palette[i * 4 + 3] = 1f;
        }
        CombatantRenderSystem.uniforms().writeCached(NAME, SIZE, 16, 1, true, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private final Matrix4f inverseProjection = new Matrix4f();
        private final Matrix4f inverseView = new Matrix4f();
        private final float[] sky = new float[4];
        private final float[] view = new float[4];
        private final float[] celestial = new float[4];
        private final float[] weather = new float[4];
        private final float[] world = new float[4];
        private final float[] clouds = new float[4];
        private final float[] layout = new float[4];
        private final float[] cloudOptions = new float[4];
        private final float[] palette = new float[40];

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder colors = Std140Builder.intoBuffer(buffer);
            colors.putMat4f(inverseProjection)
                    .putMat4f(inverseView)
                    .putFloat(sky[0]).putFloat(sky[1]).putFloat(sky[2]).putFloat(sky[3])
                    .putFloat(view[0]).putFloat(view[1]).putFloat(view[2]).putFloat(view[3])
                    .putFloat(celestial[0]).putFloat(celestial[1]).putFloat(celestial[2]).putFloat(celestial[3])
                    .putFloat(weather[0]).putFloat(weather[1]).putFloat(weather[2]).putFloat(weather[3])
                    .putFloat(world[0]).putFloat(world[1]).putFloat(world[2]).putFloat(world[3])
                    .putFloat(clouds[0]).putFloat(clouds[1]).putFloat(clouds[2]).putFloat(clouds[3])
                    .putFloat(layout[0]).putFloat(layout[1]).putFloat(layout[2]).putFloat(layout[3])
                    .putFloat(cloudOptions[0]).putFloat(cloudOptions[1]).putFloat(cloudOptions[2]).putFloat(cloudOptions[3]);
            for (float color : palette) colors.putFloat(color);
        }
    }
}
