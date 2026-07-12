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
import org.joml.Matrix4f;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;

public enum DepthOfFieldUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putMat4f()
            .putFloat().putFloat().putFloat().putFloat()
            .putFloat().putFloat().putFloat().putFloat()
            .putFloat().putFloat().putFloat().putFloat()
            .putFloat().putFloat().putFloat().putFloat()
            .putFloat().putFloat().putFloat().putFloat()
            .putFloat().putFloat().putFloat().putFloat()
            .get();

    private static final Data DATA = new Data();
    private static final String UNIFORM_NAME = "Combatant - DepthOfField UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 8;

    public static void update(Matrix4f projection,
                              int width,
                              int height,
                              float focusMode,
                              float focusDistance,
                              float farStart,
                              float farTransition,
                              float strength,
                              float maxRadius,
                              int taps,
                              float edgeProtection,
                              float msaaResolveFactor,
                              boolean debugCoc,
                              boolean mainDepth,
                              boolean translucentDepth,
                              boolean itemEntityDepth,
                              boolean particlesDepth,
                              boolean weatherDepth,
                              boolean cloudsDepth) {
        DATA.projection.set(projection);
        DATA.width = width;
        DATA.height = height;
        DATA.focusMode = focusMode;
        DATA.focusDistance = focusDistance;
        DATA.farStart = farStart;
        DATA.farTransition = farTransition;
        DATA.strength = strength;
        DATA.maxRadius = maxRadius;
        DATA.taps = taps;
        DATA.edgeProtection = edgeProtection;
        DATA.msaaResolveFactor = msaaResolveFactor;
        DATA.debugCoc = debugCoc ? 1.0f : 0.0f;
        DATA.mainDepth = mainDepth ? 1.0f : 0.0f;
        DATA.translucentDepth = translucentDepth ? 1.0f : 0.0f;
        DATA.itemEntityDepth = itemEntityDepth ? 1.0f : 0.0f;
        DATA.particlesDepth = particlesDepth ? 1.0f : 0.0f;
        DATA.weatherDepth = weatherDepth ? 1.0f : 0.0f;
        DATA.cloudsDepth = cloudsDepth ? 1.0f : 0.0f;
        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private final Matrix4f projection = new Matrix4f();
        private int width;
        private int height;
        private float focusMode;
        private float focusDistance;
        private float farStart;
        private float farTransition;
        private float strength;
        private float maxRadius;
        private int taps;
        private float edgeProtection;
        private float msaaResolveFactor;
        private float debugCoc;
        private float mainDepth;
        private float translucentDepth;
        private float itemEntityDepth;
        private float particlesDepth;
        private float weatherDepth;
        private float cloudsDepth;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putMat4f(projection)
                    .putFloat(width > 0 ? 1.0f / width : 0.0f)
                    .putFloat(height > 0 ? 1.0f / height : 0.0f)
                    .putFloat(width)
                    .putFloat(height)
                    .putFloat(focusMode)
                    .putFloat(focusDistance)
                    .putFloat(farStart)
                    .putFloat(farTransition)
                    .putFloat(strength)
                    .putFloat(maxRadius)
                    .putFloat(taps)
                    .putFloat(edgeProtection)
                    .putFloat(debugCoc)
                    .putFloat(msaaResolveFactor)
                    .putFloat(0.0f)
                    .putFloat(0.0f)
                    .putFloat(mainDepth)
                    .putFloat(translucentDepth)
                    .putFloat(itemEntityDepth)
                    .putFloat(particlesDepth)
                    .putFloat(weatherDepth)
                    .putFloat(cloudsDepth)
                    .putFloat(0.0f)
                    .putFloat(0.0f);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }
}
