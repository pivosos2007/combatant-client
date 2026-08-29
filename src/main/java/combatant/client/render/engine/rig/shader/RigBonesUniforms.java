/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.shader;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;
import combatant.client.render.engine.rig.core.RigInstance;
import org.joml.Matrix4f;

/** std140 writer for the fixed-size rig skin-matrix palette. */
public final class RigBonesUniforms {
    public static final String BLOCK_NAME = "RigBones";
    public static final int SIZE = calculateSize();

    private static final String STREAM_NAME = "Combatant - RigBones UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 32;
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Data DATA = new Data();

    private RigBonesUniforms() {
    }

    public static GpuBufferSlice upload(RigInstance instance) {
        if (instance == null) throw new IllegalArgumentException("Rig instance must not be null");
        int boneCount = instance.definition().boneCount();
        if (boneCount > RigShaderLimits.MAX_BONES) {
            throw new IllegalArgumentException("Rig has " + boneCount + " bones, shader capacity is " + RigShaderLimits.MAX_BONES);
        }
        instance.solve();
        DATA.instance = instance;
        DATA.boneCount = boneCount;
        return CombatantRenderSystem.uniforms().write(STREAM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    private static int calculateSize() {
        Std140SizeCalculator size = new Std140SizeCalculator();
        for (int i = 0; i < RigShaderLimits.MAX_BONES; i++) size.putMat4f();
        return size.get();
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private RigInstance instance;
        private int boneCount;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder out = Std140Builder.intoBuffer(buffer);
            for (int i = 0; i < boneCount; i++) {
                out.putMat4f(instance.skinMatrixRef(i));
            }
            for (int i = boneCount; i < RigShaderLimits.MAX_BONES; i++) {
                out.putMat4f(IDENTITY);
            }
        }
    }
}
