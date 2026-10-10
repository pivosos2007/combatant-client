/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.orbit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.depth.EntityDepthLayer;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessExecutionContext;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.rhi.FullscreenDrawCommand;
import combatant.client.render.engine.uniform.impl.JumpShockwaveUniforms;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Dedicated black-hole composite for TargetESP Orbit. */
public final class WorldOrbitGravity implements PostProcessPass {
    public static final WorldOrbitGravity INSTANCE = new WorldOrbitGravity();

    private Request pending;
    private long pendingFrameId = Long.MIN_VALUE;

    private WorldOrbitGravity() {
    }

    public static void submit(Vec3 center,
                              float horizonRadius,
                              float lensRadius,
                              float strength,
                              float phase,
                              int tintArgb,
                              boolean depthTest) {
        INSTANCE.enqueue(center, horizonRadius, lensRadius, strength, phase, tintArgb, depthTest);
    }

    public static void clear() {
        INSTANCE.clearPending();
    }

    private synchronized void enqueue(Vec3 center,
                                      float horizonRadius,
                                      float lensRadius,
                                      float strength,
                                      float phase,
                                      int tintArgb,
                                      boolean depthTest) {
        if (center == null || horizonRadius <= 0.001f || lensRadius <= horizonRadius || strength <= 0.0001f) return;
        double extent = lensRadius;
        AABB bounds = new AABB(
                center.x - extent, center.y - extent, center.z - extent,
                center.x + extent, center.y + extent, center.z + extent
        );
        if (!Renderer3D.Culling.isInFrustum(bounds) || !Renderer3D.Culling.isSectionVisible(bounds)) return;

        long frameId = currentFrameId();
        pendingFrameId = frameId;
        pending = new Request(
                center,
                horizonRadius,
                lensRadius,
                Mth.clamp(strength, 0.0f, 1.5f),
                phase,
                tintArgb,
                depthTest
        );
    }

    @Override
    public synchronized boolean isActive() {
        return pending != null && pendingFrameId == currentFrameId();
    }

    @Override
    public int getPriority() {
        return 4;
    }

    @Override
    public Phase getPhase() {
        return Phase.PRE_HAND;
    }

    @Override
    public boolean render(PostProcessExecutionContext execution) {
        Request request = drain();
        if (request == null || execution == null || execution.context() == null
                || execution.source() == null || execution.destination() == null) return false;

        GpuTextureView depth = request.depthTest()
                ? execution.context().preTranslucentDepth()
                : EntityDepthLayer.depthView();
        if (request.depthTest() && !depthSampleable(depth, execution.context().width(), execution.context().height())) {
            depth = execution.context().mainDepth();
        }
        if (!depthSampleable(depth, execution.context().width(), execution.context().height())) return false;

        Vec3 camera = RenderState.cameraPos;
        if (camera == null) return false;
        Matrix4f view = new Matrix4f().rotation(new Quaternionf(RenderState.cameraRotation).conjugate());
        Matrix4f inverseViewProjection = new Matrix4f(RenderState.worldProjection).mul(view).invert();
        boolean zeroToOneDepth = execution.rhi().capabilities().zeroToOneDepth();

        int tint = request.tintArgb();
        int phaseByte = Mth.clamp(Math.round(Mth.frac(request.phase()) * 255.0f), 0, 255);
        int packed = (phaseByte << 24) | (tint & 0x00FFFFFF);
        JumpShockwaveUniforms.update(
                inverseViewProjection,
                request.center().subtract(camera),
                request.lensRadius(),
                request.horizonRadius(),
                request.strength(),
                packed,
                true,
                zeroToOneDepth
        );

        GpuSampler linear = PostProcessManager.getSampler();
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        execution.rhi().drawFullscreen(
                FullscreenDrawCommand.builder("Combatant orbit gravity")
                        .colorAttachment(execution.destination())
                        .pipeline(CombatantRenderPipelines.POSTPROCESS_ORBIT_GRAVITY)
                        .uniform("JumpShockwave", JumpShockwaveUniforms.get())
                        .sampler("u_Texture", execution.source(), linear)
                        .sampler("u_Depth", depth, nearest)
                        .build()
        );
        return true;
    }

    private synchronized Request drain() {
        long frameId = currentFrameId();
        if (pendingFrameId != frameId || pending == null) {
            pending = null;
            pendingFrameId = frameId;
            return null;
        }
        Request out = pending;
        pending = null;
        return out;
    }

    private synchronized void clearPending() {
        pending = null;
        pendingFrameId = Long.MIN_VALUE;
    }

    private static long currentFrameId() {
        RenderFrameContext context = CombatantRenderSystem.currentContext();
        return context != null ? context.frameId() : CombatantRenderSystem.resources().frameId();
    }

    private record Request(Vec3 center,
                           float horizonRadius,
                           float lensRadius,
                           float strength,
                           float phase,
                           int tintArgb,
                           boolean depthTest) {
    }

    private static boolean depthSampleable(GpuTextureView depth, int width, int height) {
        return depth != null && depth.getWidth(0) == width && depth.getHeight(0) == height
                && !(depth.texture() instanceof IMsaaTexture msaa
                    && msaa.combatant$isMsaa() && msaa.combatant$getSamples() > 1);
    }
}
