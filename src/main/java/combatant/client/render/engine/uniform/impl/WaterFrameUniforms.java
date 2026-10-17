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

/** std140 mirror of the native std430 WaterFrame contract (mat4/vec4 only, identical layout). */
public enum WaterFrameUniforms {
    ;
    private static final String STREAM = "Combatant - Water Frame UBO";
    public static final int SIZE = new Std140SizeCalculator()
            .putMat4f().putMat4f().putMat4f().putMat4f().putMat4f().putMat4f()
            .putVec4().putVec4().putVec4().putVec4().putVec4()
            .putVec4().putVec4().putVec4().putVec4().putVec4().putVec4()
            .get();

    public static GpuBufferSlice write(Frame frame) {
        if (frame == null) throw new IllegalArgumentException("frame");
        return CombatantRenderSystem.uniforms().write(STREAM, SIZE, 2, buffer -> {
            Std140Builder builder = Std140Builder.intoBuffer(buffer)
                    .putMat4f(frame.currentView())
                    .putMat4f(frame.currentProjection())
                    .putMat4f(frame.currentInverseProjection())
                    .putMat4f(frame.currentInverseView())
                    .putMat4f(frame.previousView())
                    .putMat4f(frame.previousProjection());
            for (float[] value : frame.vectors()) {
                builder.putVec4(value[0], value[1], value[2], value[3]);
            }
        });
    }

    public record Frame(
            Matrix4f currentView,
            Matrix4f currentProjection,
            Matrix4f currentInverseProjection,
            Matrix4f currentInverseView,
            Matrix4f previousView,
            Matrix4f previousProjection,
            float[] currentCameraTime,
            float[] previousCameraTime,
            float[] viewport,
            float[] depthTransform,
            float[] deformation0,
            float[] deformation1,
            float[] mediumReflection,
            float[] mediumBoundary,
            float[] opticalAbsorption,
            float[] opticalScattering,
            float[] reflectionMeta
    ) {
        public Frame {
            currentView = copy(currentView);
            currentProjection = copy(currentProjection);
            currentInverseProjection = copy(currentInverseProjection);
            currentInverseView = copy(currentInverseView);
            previousView = copy(previousView);
            previousProjection = copy(previousProjection);
            currentCameraTime = vec4(currentCameraTime);
            previousCameraTime = vec4(previousCameraTime);
            viewport = vec4(viewport);
            depthTransform = vec4(depthTransform);
            deformation0 = vec4(deformation0);
            deformation1 = vec4(deformation1);
            mediumReflection = vec4(mediumReflection);
            mediumBoundary = vec4(mediumBoundary);
            opticalAbsorption = vec4(opticalAbsorption);
            opticalScattering = vec4(opticalScattering);
            reflectionMeta = vec4(reflectionMeta);
        }

        float[][] vectors() {
            return new float[][]{
                    currentCameraTime, previousCameraTime, viewport, depthTransform, deformation0,
                    deformation1, mediumReflection, mediumBoundary, opticalAbsorption, opticalScattering, reflectionMeta
            };
        }

        private static Matrix4f copy(Matrix4f matrix) {
            return matrix == null ? new Matrix4f() : new Matrix4f(matrix);
        }

        private static float[] vec4(float[] value) {
            if (value == null || value.length != 4) throw new IllegalArgumentException("vec4");
            return value.clone();
        }
    }
}
