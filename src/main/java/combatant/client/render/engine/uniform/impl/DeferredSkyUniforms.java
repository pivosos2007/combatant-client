/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.uniform.impl;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;

import java.nio.ByteBuffer;

/** Stable sky-pass payload. No dependency on dev-deferred code or shaderpack uniforms. */
public enum DeferredSkyUniforms {
    ;
    private static final String ID = "Combatant - DeferredSky UBO";
    private static final int SIZE = new Std140SizeCalculator()
            .putVec4().putVec4().putVec4().putVec4().putVec4()
            .putVec4().putVec4().putVec4().putVec4().putVec4().putVec4()
            .putVec4().putVec4().putVec4().putVec4().putVec4().putVec4()
            .putVec4().putVec4().putVec4().putVec4()
            .putVec4().putVec4().putVec4().putVec4()
            .get();
    private static final Data DATA = new Data();

    public static void update(float[] view, float[] rotation, float[] sun, float[] climate,
                              float[] camera, float[] sky, float[] rayleigh, float[] mie,
                              float[] quality, float[] layer, float[] shape,
                              float[] lighting, float[] weather, float[] cirrus,
                              float[] previousCamera, float[] previousView, float[] temporal,
                              float[] moon, float[] optics, float[] ozone, float[] stratus,
                              float[] sunColor, float[] sunVisual, float[] moonColor, float[] moonVisual) {
        DATA.set(0, view);
        DATA.set(1, rotation);
        DATA.set(2, sun);
        DATA.set(3, climate);
        DATA.set(4, camera);
        DATA.set(5, sky);
        DATA.set(6, rayleigh);
        DATA.set(7, mie);
        DATA.set(8, quality);
        DATA.set(9, layer);
        DATA.set(10, shape);
        DATA.set(11, lighting);
        DATA.set(12, weather);
        DATA.set(13, cirrus);
        DATA.set(14, previousCamera);
        DATA.set(15, previousView);
        DATA.set(16, temporal);
        DATA.set(17, moon);
        DATA.set(18, optics);
        DATA.set(19, ozone);
        DATA.set(20, stratus);
        DATA.set(21, sunColor);
        DATA.set(22, sunVisual);
        DATA.set(23, moonColor);
        DATA.set(24, moonVisual);
        // Time and camera move on every frame: never reuse yesterday's atmosphere slice.
        CombatantRenderSystem.uniforms().writeCached(ID, SIZE, 8, 1, true, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(ID);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private final float[][] vectors = new float[25][4];

        private void set(int index, float[] value) {
            System.arraycopy(value, 0, vectors[index], 0, 4);
        }

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder builder = Std140Builder.intoBuffer(buffer);
            for (float[] vector : vectors) {
                for (float component : vector) builder.putFloat(component);
            }
        }
    }
}
