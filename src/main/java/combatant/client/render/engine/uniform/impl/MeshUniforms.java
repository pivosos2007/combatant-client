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
    private static final Matrix4f LAST_PROJ = new Matrix4f();
    private static final Matrix4f LAST_MODEL_VIEW = new Matrix4f();
    private static long lastFrameId = Long.MIN_VALUE;
    private static float lastViewportWidth = Float.NaN;
    private static float lastViewportHeight = Float.NaN;

    public static void update(Matrix4f proj, Matrix4f modelView) {
        update(proj, modelView, 1.0f, 1.0f);
    }

    public static void update(Matrix4f proj, Matrix4f modelView, float viewportWidth, float viewportHeight) {
        float safeWidth = Math.max(1.0f, viewportWidth);
        float safeHeight = Math.max(1.0f, viewportHeight);
        CombatantUniformAllocator allocator = CombatantRenderSystem.uniforms();
        long frameId = allocator.frameId();

        // Consecutive draws in a compiled UI/world pass commonly share the exact same
        // projection and model-view. Rebinding the current slice is sufficient; mapping and
        // rewriting another std140 block only burns ring space and driver calls.
        if (allocator.hasCurrent(UNIFORM_NAME)
                && lastFrameId == frameId
                && Float.compare(lastViewportWidth, safeWidth) == 0
                && Float.compare(lastViewportHeight, safeHeight) == 0
                && LAST_PROJ.equals(proj, 0.0f)
                && LAST_MODEL_VIEW.equals(modelView, 0.0f)) {
            return;
        }

        DATA.proj = proj;
        DATA.modelView = modelView;
        DATA.viewportWidth = safeWidth;
        DATA.viewportHeight = safeHeight;
        allocator.write(UNIFORM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);

        LAST_PROJ.set(proj);
        LAST_MODEL_VIEW.set(modelView);
        lastFrameId = frameId;
        lastViewportWidth = safeWidth;
        lastViewportHeight = safeHeight;
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
