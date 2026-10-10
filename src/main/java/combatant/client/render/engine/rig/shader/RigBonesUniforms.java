/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rig.shader;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.uniform.ShaderUniformBindings;
import combatant.client.render.engine.rig.core.RigInstance;
import org.joml.Matrix4f;

/** std140 writer for the fixed-size rig skin-matrix palette. */
public final class RigBonesUniforms {
    private static final ShaderUniformBindings.Block BLOCK = ShaderUniformBindings.block("RigBones");
    public static final String BLOCK_NAME = BLOCK.name();
    public static final int SIZE = BLOCK.size();

    private static final String STREAM_NAME = "Combatant - RigBones UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 32;
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final ShaderUniformBindings.Writer DATA = BLOCK.writer();

    static {
        if (BLOCK.member("u_SkinMatrices").count() != RigShaderLimits.MAX_BONES) {
            throw new IllegalStateException("Rig shader/CPU bone capacity mismatch");
        }
    }

    private RigBonesUniforms() {
    }

    public static GpuBufferSlice upload(RigInstance instance) {
        if (instance == null) throw new IllegalArgumentException("Rig instance must not be null");
        int boneCount = instance.definition().boneCount();
        if (boneCount > RigShaderLimits.MAX_BONES) {
            throw new IllegalArgumentException("Rig has " + boneCount + " bones, shader capacity is " + RigShaderLimits.MAX_BONES);
        }
        instance.solve();
        for (int i = 0; i < boneCount; i++) {
            DATA.mat4("u_SkinMatrices", i, instance.skinMatrixRef(i));
        }
        for (int i = boneCount; i < BLOCK.member("u_SkinMatrices").count(); i++) {
            DATA.mat4("u_SkinMatrices", i, IDENTITY);
        }
        return CombatantRenderSystem.uniforms().write(STREAM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

}
