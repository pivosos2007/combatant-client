/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.uniform.impl;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import combatant.client.render.engine.core.CombatantRenderSystem;
import org.joml.Matrix4f;

import java.util.List;

/**
 * std140 fallback mirror for the reusable reflection trace policy plus reflection-cascade metadata.
 * Native patch shaders consume the canonical REFLECTION_TRACE_DATA / REFLECTION_CASCADE_DATA SSBOs;
 * the declared triangle fallback receives the same semantics through this UBO.
 */
public enum WaterReflectionTraceUniforms {
    ;
    public static final int MAX_CASCADE_FACES = 24;
    private static final String STREAM = "Combatant - Water Reflection Trace UBO";
    public static final int SIZE;

    static {
        Std140SizeCalculator size = new Std140SizeCalculator()
                .putVec4().putVec4().putVec4().putVec4().putVec4();
        for (int i = 0; i < MAX_CASCADE_FACES; i++) {
            size.putMat4f().putVec4().putVec4().putVec4();
        }
        SIZE = size.get();
    }

    public static GpuBufferSlice write(Frame frame) {
        if (frame == null) throw new IllegalArgumentException("frame");
        return CombatantRenderSystem.uniforms().write(STREAM, SIZE, 2, buffer -> {
            Std140Builder builder = Std140Builder.intoBuffer(buffer);
            put(builder, frame.traceExtentAndFar());
            put(builder, frame.traceParams0());
            put(builder, frame.traceParams1());
            put(builder, frame.resolveParams());
            builder.putVec4(frame.faces().size(), 0.0f, 0.0f, 0.0f);
            for (int i = 0; i < MAX_CASCADE_FACES; i++) {
                Face face = i < frame.faces().size() ? frame.faces().get(i) : Face.ZERO;
                builder.putMat4f(face.viewProjection());
                put(builder, face.atlasScaleBias());
                put(builder, face.rangeFace());
                put(builder, face.originDelta());
            }
        });
    }

    private static void put(Std140Builder builder, float[] value) {
        builder.putVec4(value[0], value[1], value[2], value[3]);
    }

    public record Frame(float[] traceExtentAndFar,
                        float[] traceParams0,
                        float[] traceParams1,
                        float[] resolveParams,
                        List<Face> faces) {
        public Frame {
            traceExtentAndFar = vec4(traceExtentAndFar);
            traceParams0 = vec4(traceParams0);
            traceParams1 = vec4(traceParams1);
            resolveParams = vec4(resolveParams);
            faces = faces == null ? List.of() : List.copyOf(faces.subList(0, Math.min(faces.size(), MAX_CASCADE_FACES)));
        }
    }

    public record Face(Matrix4f viewProjection, float[] atlasScaleBias, float[] rangeFace, float[] originDelta) {
        private static final Face ZERO = new Face(new Matrix4f(), new float[4], new float[4], new float[4]);

        public Face {
            viewProjection = viewProjection == null ? new Matrix4f() : new Matrix4f(viewProjection);
            atlasScaleBias = vec4(atlasScaleBias);
            rangeFace = vec4(rangeFace);
            originDelta = vec4(originDelta);
        }
    }

    private static float[] vec4(float[] value) {
        if (value == null || value.length != 4) throw new IllegalArgumentException("vec4");
        return value.clone();
    }
}
