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
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.deferred.DeferredPrimaryViewSource;
import combatant.client.render.engine.deferred.DeferredWorldPipeline;
import combatant.client.render.engine.world.DirectionalLightDescriptor;
import combatant.client.render.engine.world.WorldRenderState;
import combatant.client.render.engine.rhi.uniform.CombatantUniformAllocator;
import org.joml.Matrix4f;

/** Frame state shared by neutral deferred lighting and compatibility presentation publish. */
public enum DeferredLightingUniforms {
    ;
    public static final int SIZE = new Std140SizeCalculator()
            .putMat4f()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private static final String STREAM = "Combatant - Deferred Lighting UBO";
    private static final Data DATA = new Data();

    public static void update(DeferredWorldPipeline.LightingState state,
                              WorldRenderState worldState,
                              DeferredPrimaryViewSource.FrameView view,
                              boolean zeroToOneDepth,
                              boolean shadowValid,
                              boolean ambientOcclusionValid,
                              boolean cloudShadowValid) {
        DATA.state = state;
        DATA.worldState = worldState;
        DATA.view = view;
        DATA.zeroToOneDepth = zeroToOneDepth;
        DATA.shadowValid = shadowValid;
        DATA.ambientOcclusionValid = ambientOcclusionValid;
        DATA.cloudShadowValid = cloudShadowValid;
        CombatantRenderSystem.uniforms().write(STREAM, SIZE, 1, DATA);
    }

    public static GpuBufferSlice get() {
        return CombatantRenderSystem.uniforms().current(STREAM);
    }

    private static final class Data implements CombatantUniformAllocator.UniformWriter {
        private DeferredWorldPipeline.LightingState state;
        private WorldRenderState worldState;
        private DeferredPrimaryViewSource.FrameView view;
        private boolean zeroToOneDepth;
        private boolean shadowValid;
        private boolean ambientOcclusionValid;
        private boolean cloudShadowValid;

        @Override
        public void write(java.nio.ByteBuffer buffer) {
            Matrix4f inverseProjection = view != null ? view.inverseProjection() : new Matrix4f();
            DirectionalLightDescriptor directional = worldState != null
                    ? worldState.directionalLight() : DirectionalLightDescriptor.NONE;
            float lightX = directional.directionX();
            float lightY = directional.directionY();
            float lightZ = directional.directionZ();
            boolean directionalValid = directional.valid();
            if (directionalValid && view != null) {
                Matrix4f matrix = view.view();
                float worldX = lightX;
                float worldY = lightY;
                float worldZ = lightZ;
                lightX = matrix.m00() * worldX + matrix.m10() * worldY + matrix.m20() * worldZ;
                lightY = matrix.m01() * worldX + matrix.m11() * worldY + matrix.m21() * worldZ;
                lightZ = matrix.m02() * worldX + matrix.m12() * worldY + matrix.m22() * worldZ;
                float length = (float) Math.sqrt(lightX * lightX + lightY * lightY + lightZ * lightZ);
                if (length > 1.0e-6f) {
                    lightX /= length;
                    lightY /= length;
                    lightZ /= length;
                } else {
                    directionalValid = false;
                }
            }

            Std140Builder.intoBuffer(buffer)
                    .putMat4f(inverseProjection)
                    .putVec4(state.fogRed(), state.fogGreen(), state.fogBlue(), state.fogAlpha())
                    .putVec4(state.environmentalFogStart(), state.environmentalFogEnd(),
                            state.renderFogStart(), state.renderFogEnd())
                    .putVec4(lightX, lightY, lightZ, directionalValid ? 1.0f : 0.0f)
                    .putVec4(directional.radianceRed(), directional.radianceGreen(), directional.radianceBlue(),
                            directional.angularRadiusRadians())
                    .putVec4(
                            zeroToOneDepth ? 1.0f : 2.0f,
                            zeroToOneDepth ? 0.0f : -1.0f,
                            shadowValid ? 1.0f : 0.0f,
                            ambientOcclusionValid ? 1.0f : 0.0f
                    )
                    .putVec4(cloudShadowValid ? 1.0f : 0.0f, 0.0f, 0.0f, 0.0f);
        }

        @Override
        public boolean equals(Object other) {
            return false;
        }
    }
}
