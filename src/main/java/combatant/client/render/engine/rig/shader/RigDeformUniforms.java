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
import combatant.client.render.engine.rig.deform.RigDeformDefinition;
import combatant.client.render.engine.rig.deform.RigDeformState;
import org.joml.Vector3fc;

/**
 * std140 writer for bend/twist bind frames and per-instance dynamic parameters.
 * Layout is five vec4 arrays, mirrored exactly in rig_textured.vert.
 */
public final class RigDeformUniforms {
    public static final String BLOCK_NAME = "RigDeform";
    public static final int SIZE = RigShaderLimits.MAX_DEFORMS * 5 * 16;

    private static final String STREAM_NAME = "Combatant - RigDeform UBO";
    private static final int EXPECTED_WRITES_PER_FRAME = 32;
    private static final Data DATA = new Data();

    static {
        Std140SizeCalculator size = new Std140SizeCalculator();
        for (int array = 0; array < 5; array++) {
            for (int i = 0; i < RigShaderLimits.MAX_DEFORMS; i++) size.putVec4();
        }
        if (size.get() != SIZE) {
            throw new IllegalStateException("RigDeform std140 size mismatch: expected=" + SIZE + ", actual=" + size.get());
        }
    }

    private RigDeformUniforms() {
    }

    public static GpuBufferSlice upload(RigDeformState state) {
        if (state == null) throw new IllegalArgumentException("Rig deform state must not be null");
        DATA.state = state;
        return CombatantRenderSystem.uniforms().write(STREAM_NAME, SIZE, EXPECTED_WRITES_PER_FRAME, DATA);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private RigDeformState state;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Std140Builder out = Std140Builder.intoBuffer(buffer);

            // u_DeformOriginLength[MAX_DEFORMS]
            for (int i = 0; i < RigShaderLimits.MAX_DEFORMS; i++) {
                RigDeformDefinition definition = state.definition(i);
                if (definition == null) {
                    out.putVec4(0f, 0f, 0f, 0f);
                } else {
                    Vector3fc origin = definition.origin();
                    out.putVec4(origin.x(), origin.y(), origin.z(), definition.length());
                }
            }

            // u_DeformAxisFlags[MAX_DEFORMS]
            for (int i = 0; i < RigShaderLimits.MAX_DEFORMS; i++) {
                RigDeformDefinition definition = state.definition(i);
                if (definition == null) {
                    out.putVec4(0f, 1f, 0f, 0f);
                } else {
                    Vector3fc axis = definition.axis();
                    out.putVec4(axis.x(), axis.y(), axis.z(), state.flags(i));
                }
            }

            // u_DeformBendAxis[MAX_DEFORMS]
            for (int i = 0; i < RigShaderLimits.MAX_DEFORMS; i++) {
                RigDeformDefinition definition = state.definition(i);
                if (definition == null) {
                    out.putVec4(1f, 0f, 0f, 0f);
                } else {
                    Vector3fc bendAxis = definition.bendAxis();
                    out.putVec4(bendAxis.x(), bendAxis.y(), bendAxis.z(), 0f);
                }
            }

            // u_DeformRanges[MAX_DEFORMS] = bendStart, bendEnd, twistStart, twistEnd
            for (int i = 0; i < RigShaderLimits.MAX_DEFORMS; i++) {
                RigDeformDefinition definition = state.definition(i);
                if (definition == null) {
                    out.putVec4(0f, 1f, 0f, 1f);
                } else {
                    out.putVec4(definition.bendStart(), definition.bendEnd(), definition.twistStart(), definition.twistEnd());
                }
            }

            // u_DeformParams[MAX_DEFORMS] = bendAngle, twistAngle, bendFalloff, twistFalloff
            for (int i = 0; i < RigShaderLimits.MAX_DEFORMS; i++) {
                out.putVec4(
                        state.bendAngle(i),
                        state.twistAngle(i),
                        state.bendFalloff(i),
                        state.twistFalloff(i)
                );
            }
        }
    }
}
