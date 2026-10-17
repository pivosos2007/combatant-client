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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public enum JumpShockwaveUniforms {
    ;

    public static final int SIZE = new Std140SizeCalculator()
            .putMat4f()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static final String UNIFORM_NAME = "Combatant - JumpShockwave UBO";
    private static final Data DATA = new Data();

    public static void update(Matrix4f inverseViewProjection,
                              Vec3 centerRelative,
                              float radius,
                              float thickness,
                              float strength,
                              int argb,
                              boolean depthTest,
                              boolean zeroToOneDepth) {
        DATA.inverseViewProjection.set(inverseViewProjection);
        DATA.centerX = (float) centerRelative.x;
        DATA.centerY = (float) centerRelative.y;
        DATA.centerZ = (float) centerRelative.z;
        DATA.radius = radius;
        DATA.thickness = thickness;
        DATA.strength = strength;
        DATA.depthTest = depthTest ? 1.0f : 0.0f;
        DATA.red = ((argb >>> 16) & 255) / 255.0f;
        DATA.green = ((argb >>> 8) & 255) / 255.0f;
        DATA.blue = (argb & 255) / 255.0f;
        DATA.alpha = ((argb >>> 24) & 255) / 255.0f;
        DATA.depthScale = zeroToOneDepth ? 1.0f : 2.0f;
        DATA.depthBias = zeroToOneDepth ? 0.0f : -1.0f;
        DATA.farNdc = zeroToOneDepth ? 0.0f : -1.0f;
        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, 4, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private final Matrix4f inverseViewProjection = new Matrix4f();
        private float centerX;
        private float centerY;
        private float centerZ;
        private float radius;
        private float thickness;
        private float strength;
        private float depthTest;
        private float red;
        private float green;
        private float blue;
        private float alpha;
        private float depthScale;
        private float depthBias;
        private float farNdc;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putMat4f(inverseViewProjection)
                    .putVec4(centerX, centerY, centerZ, radius)
                    .putVec4(thickness, strength, depthTest, 0.0f)
                    .putVec4(red, green, blue, alpha)
                    .putVec4(depthScale, depthBias, farNdc, 0.0f);
        }

        @Override
        public boolean equals(Object other) {
            return false;
        }
    }
}
