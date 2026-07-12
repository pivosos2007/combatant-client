/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.postprocess;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.graph.PostProcessGraph;
import combatant.client.render.engine.profiler.RenderCostProfiler;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.rhi.FullscreenDrawCommand;
import combatant.client.render.engine.uniform.impl.PostProcessUniforms;
import combatant.client.runtime.RuntimeGate;

import java.util.OptionalDouble;

public enum PostProcessManager {
    ;
    private static final PostProcessGraph GRAPH = new PostProcessGraph();
    private static GpuSampler sampler;

    public static void register(PostProcessPass pass) {
        GRAPH.addLegacy(pass);
    }

    public static PostProcessGraph graph() {
        return GRAPH;
    }

    public static void renderAll(PostProcessPass.Phase phase, float tickDelta) {
        if (!RuntimeGate.canRunRender()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer.mainRenderTarget() == null) return;

        FullScreenRenderer.ensureInit();
        ensureSampler();

        var mv = RenderSystem.getModelViewStack();
        Matrix4f previousProjection = MeshRenderer.projection();
        GpuBufferSlice previousProjectionBuffer = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        boolean previousRendering3D = RenderState.rendering3D;
        mv.pushMatrix();
        mv.identity();
        MeshRenderer.setProjection(new Matrix4f().identity());
        RenderState.rendering3D = false;

        try {
            GRAPH.execute(
                    phase,
                    tickDelta,
                    CombatantRenderSystem.ensureFrameContext(),
                    CombatantRenderSystem.rhi(),
                    PostProcessManager::copy
            );
        } finally {
            MeshRenderer.setProjection(previousProjection);
            if (previousProjectionBuffer != null && previousProjectionType != null) {
                RenderSystem.setProjectionMatrix(previousProjectionBuffer, previousProjectionType);
            }
            RenderState.rendering3D = previousRendering3D;
            mv.popMatrix();
        }
    }

    private static void ensureSampler() {
        if (sampler != null) return;
        sampler = RenderSystem.getDevice().createSampler(
                AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR,
                FilterMode.LINEAR,
                1,
                OptionalDouble.empty()
        );
    }

    public static GpuSampler getSampler() {
        ensureSampler();
        return sampler;
    }

    public static void copy(GpuTextureView src, GpuTextureView dst) {
        try (RenderCostProfiler.Scope ignored = RenderCostProfiler.postPass("copy")) {
            if (src == null || dst == null || src == dst) return;
            if (CombatantRenderSystem.rhi().textureBlitter().copyFast(src, dst)) {
                return;
            }

            ensureSampler();
            PostProcessUniforms.update(0.0f, 0.0f, 0.0f, 0.0f);
            CombatantRenderSystem.rhi().drawFullscreen(
                    FullscreenDrawCommand.builder("Combatant PostProcess Copy")
                            .colorAttachment(dst)
                            .pipeline(CombatantRenderPipelines.DAMAGE_TINT)
                            .uniform("PostProcess", PostProcessUniforms.get())
                            .sampler("u_Texture", src, sampler)
                            .build()
            );
            CombatantRenderSystem.rhi().stats().textureShaderCopy();
        }
    }

    public static void shutdownForRuntime() {
        GRAPH.close();
        sampler = null;
    }
}
