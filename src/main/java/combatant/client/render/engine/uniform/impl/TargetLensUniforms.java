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

public enum TargetLensUniforms {
    ;

    public static final int MAX_LENSES = 18;
    public static final int SIZE = size();
    private static final String UNIFORM_NAME = "Combatant - TargetLens UBO";
    private static final Data DATA = new Data();

    public static void update(float aspect,
                              float strength,
                              boolean depthAvailable,
                              int count,
                              float[] lenses,
                              float[] colors,
                              float[] shapes,
                              float[] meta) {
        DATA.aspect = aspect;
        DATA.strength = strength;
        DATA.depthAvailable = depthAvailable ? 1.0f : 0.0f;
        DATA.count = Math.min(MAX_LENSES, Math.max(0, count));
        int values = DATA.count * 4;
        for (int i = 0; i < values; i++) {
            DATA.lenses[i] = lenses[i];
        }
        for (int i = values; i < DATA.lenses.length; i++) {
            DATA.lenses[i] = 0.0f;
        }
        for (int i = 0; i < values; i++) {
            DATA.colors[i] = colors[i];
        }
        for (int i = values; i < DATA.colors.length; i++) {
            DATA.colors[i] = 0.0f;
        }
        for (int i = 0; i < values; i++) {
            DATA.shapes[i] = shapes[i];
        }
        for (int i = values; i < DATA.shapes.length; i++) {
            DATA.shapes[i] = 0.0f;
        }
        for (int i = 0; i < values; i++) {
            DATA.meta[i] = meta[i];
        }
        for (int i = values; i < DATA.meta.length; i++) {
            DATA.meta[i] = 0.0f;
        }
        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, 4, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static int size() {
        Std140SizeCalculator calculator = new Std140SizeCalculator().putVec4();
        for (int i = 0; i < MAX_LENSES; i++) {
            calculator.putVec4();
        }
        for (int i = 0; i < MAX_LENSES; i++) {
            calculator.putVec4();
        }
        for (int i = 0; i < MAX_LENSES; i++) {
            calculator.putVec4();
        }
        for (int i = 0; i < MAX_LENSES; i++) {
            calculator.putVec4();
        }
        return calculator.get();
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private final float[] lenses = new float[MAX_LENSES * 4];
        private final float[] colors = new float[MAX_LENSES * 4];
        private final float[] shapes = new float[MAX_LENSES * 4];
        private final float[] meta = new float[MAX_LENSES * 4];
        private int count;
        private float aspect;
        private float strength;
        private float depthAvailable;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder builder = Std140Builder.intoBuffer(buffer)
                    .putVec4(count, aspect, strength, depthAvailable);
            for (int i = 0; i < MAX_LENSES; i++) {
                int base = i * 4;
                builder.putVec4(lenses[base], lenses[base + 1], lenses[base + 2], lenses[base + 3]);
            }
            for (int i = 0; i < MAX_LENSES; i++) {
                int base = i * 4;
                builder.putVec4(colors[base], colors[base + 1], colors[base + 2], colors[base + 3]);
            }
            for (int i = 0; i < MAX_LENSES; i++) {
                int base = i * 4;
                builder.putVec4(shapes[base], shapes[base + 1], shapes[base + 2], shapes[base + 3]);
            }
            for (int i = 0; i < MAX_LENSES; i++) {
                int base = i * 4;
                builder.putVec4(meta[base], meta[base + 1], meta[base + 2], meta[base + 3]);
            }
        }

        @Override
        public boolean equals(Object other) {
            return false;
        }
    }
}
