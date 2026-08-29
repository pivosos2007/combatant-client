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
import combatant.client.render.engine.rig.deform.RigRibbonDefinition;
import combatant.client.render.engine.rig.deform.RigRibbonState;
import org.joml.Vector3fc;

/** std140 writer for sampled parallel-transport ribbon frames and their bind-space source frames. */
public final class RigRibbonUniforms {
    public static final String BLOCK_NAME = "RigRibbon";
    public static final int SIZE = calculateSize();

    private static final String STREAM_NAME = "Combatant - RigRibbon UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 32;
    private static final Data DATA = new Data();

    private RigRibbonUniforms() {
    }

    public static GpuBufferSlice upload(RigRibbonState state) {
        if (state == null) throw new IllegalArgumentException("Rig ribbon state must not be null");
        DATA.state = state;
        return CombatantRenderSystem.uniforms().write(STREAM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    private static int calculateSize() {
        Std140SizeCalculator size = new Std140SizeCalculator();
        for (int i = 0; i < RigShaderLimits.MAX_RIBBON_FRAMES; i++) size.putVec4(); // positions
        for (int i = 0; i < RigShaderLimits.MAX_RIBBON_FRAMES; i++) size.putVec4(); // normals
        for (int i = 0; i < RigShaderLimits.MAX_RIBBON_FRAMES; i++) size.putVec4(); // binormals
        for (int i = 0; i < RigShaderLimits.MAX_DEFORMS; i++) size.putVec4(); // source tangent
        for (int i = 0; i < RigShaderLimits.MAX_DEFORMS; i++) size.putVec4(); // source normal
        for (int i = 0; i < RigShaderLimits.MAX_DEFORMS; i++) size.putVec4(); // source binormal
        for (int i = 0; i < RigShaderLimits.MAX_DEFORMS; i++) size.putVec4(); // meta
        return size.get();
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private RigRibbonState state;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder out = Std140Builder.intoBuffer(buffer);

            for (int frame = 0; frame < RigShaderLimits.MAX_RIBBON_FRAMES; frame++) {
                out.putVec4(state.positionX(frame), state.positionY(frame), state.positionZ(frame), 0f);
            }
            for (int frame = 0; frame < RigShaderLimits.MAX_RIBBON_FRAMES; frame++) {
                out.putVec4(state.normalX(frame), state.normalY(frame), state.normalZ(frame), 0f);
            }
            for (int frame = 0; frame < RigShaderLimits.MAX_RIBBON_FRAMES; frame++) {
                out.putVec4(state.binormalX(frame), state.binormalY(frame), state.binormalZ(frame), 0f);
            }

            for (int id = 0; id < RigShaderLimits.MAX_DEFORMS; id++) {
                RigRibbonDefinition definition = state.definition(id);
                putDirection(out, definition != null ? definition.sourceTangent() : null);
            }
            for (int id = 0; id < RigShaderLimits.MAX_DEFORMS; id++) {
                RigRibbonDefinition definition = state.definition(id);
                putDirection(out, definition != null ? definition.sourceNormal() : null);
            }
            for (int id = 0; id < RigShaderLimits.MAX_DEFORMS; id++) {
                RigRibbonDefinition definition = state.definition(id);
                putDirection(out, definition != null ? definition.sourceBinormal() : null);
            }

            for (int id = 0; id < RigShaderLimits.MAX_DEFORMS; id++) {
                RigRibbonDefinition definition = state.definition(id);
                if (definition == null) {
                    out.putVec4(0f, 0f, 1f, 0f);
                } else {
                    out.putVec4(
                            definition.sampleCount(),
                            state.active(id) ? 1f : 0f,
                            definition.handedness(),
                            0f
                    );
                }
            }
        }

        private static void putDirection(Std140Builder out, Vector3fc value) {
            if (value == null) out.putVec4(0f, 0f, 0f, 0f);
            else out.putVec4(value.x(), value.y(), value.z(), 0f);
        }
    }
}
