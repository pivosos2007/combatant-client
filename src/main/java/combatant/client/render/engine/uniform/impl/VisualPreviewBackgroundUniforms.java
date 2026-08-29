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

public enum VisualPreviewBackgroundUniforms {
    ;

    public static final int SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static final Data DATA = new Data();
    private static final String NAME = "Combatant - VisualPreviewBackground UBO";

    public static void update(float width,
                              float height,
                              float time,
                              float cameraX,
                              float cameraY,
                              float cameraZ,
                              float yaw,
                              float pitch,
                              int accentArgb,
                              int backgroundArgb) {
        DATA.viewport[0] = width;
        DATA.viewport[1] = height;
        DATA.viewport[2] = time;
        DATA.viewport[3] = 0.0f;

        DATA.camera[0] = cameraX;
        DATA.camera[1] = cameraY;
        DATA.camera[2] = cameraZ;
        DATA.camera[3] = 1.0f;

        DATA.rotation[0] = yaw;
        DATA.rotation[1] = pitch;
        DATA.rotation[2] = 0.0f;
        DATA.rotation[3] = 0.0f;

        putColor(DATA.accent, accentArgb);
        putColor(DATA.background, backgroundArgb);
        CombatantRenderSystem.uniforms().write(NAME, SIZE, 8, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(NAME);
    }

    private static void putColor(float[] target, int argb) {
        target[0] = ((argb >>> 16) & 0xFF) / 255.0f;
        target[1] = ((argb >>> 8) & 0xFF) / 255.0f;
        target[2] = (argb & 0xFF) / 255.0f;
        target[3] = 1.0f;
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private final float[] viewport = new float[4];
        private final float[] camera = new float[4];
        private final float[] rotation = new float[4];
        private final float[] accent = new float[4];
        private final float[] background = new float[4];

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(viewport[0]).putFloat(viewport[1]).putFloat(viewport[2]).putFloat(viewport[3])
                    .putFloat(camera[0]).putFloat(camera[1]).putFloat(camera[2]).putFloat(camera[3])
                    .putFloat(rotation[0]).putFloat(rotation[1]).putFloat(rotation[2]).putFloat(rotation[3])
                    .putFloat(accent[0]).putFloat(accent[1]).putFloat(accent[2]).putFloat(accent[3])
                    .putFloat(background[0]).putFloat(background[1]).putFloat(background[2]).putFloat(background[3]);
        }

        @Override
        public boolean equals(Object other) {
            return false;
        }
    }
}
