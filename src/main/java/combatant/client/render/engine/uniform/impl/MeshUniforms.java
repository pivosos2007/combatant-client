/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.render.engine.uniform.impl;

import combatant.client.render.engine.core.CombatantRenderSystem;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import org.joml.Matrix4f;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;

public enum MeshUniforms {
    ;

    public static final int SIZE = new Std140SizeCalculator()
            .putMat4f()
            .putMat4f()
            .putVec4()
            .get();

    private static final Data DATA = new Data();
    private static final String UNIFORM_NAME = "Combatant - Mesh UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 16;

    public static void update(Matrix4f proj, Matrix4f modelView) {
        update(proj, modelView, 1.0f, 1.0f);
    }

    public static void update(Matrix4f proj, Matrix4f modelView, float viewportWidth, float viewportHeight) {
        DATA.proj = proj;
        DATA.modelView = modelView;
        DATA.viewportWidth = Math.max(1.0f, viewportWidth);
        DATA.viewportHeight = Math.max(1.0f, viewportHeight);
        CombatantRenderSystem.uniforms().write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(UNIFORM_NAME);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private Matrix4f proj;
        private Matrix4f modelView;
        private float viewportWidth = 1.0f;
        private float viewportHeight = 1.0f;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putMat4f(proj)
                    .putMat4f(modelView)
                    .putFloat(viewportWidth)
                    .putFloat(viewportHeight)
                    .putFloat(1.0f / Math.max(1.0f, viewportWidth))
                    .putFloat(1.0f / Math.max(1.0f, viewportHeight));
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }

}
