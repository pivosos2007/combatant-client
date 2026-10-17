/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.shockwave;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.pipeline.PostProcessRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessExecutionContext;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.rhi.FullscreenDrawCommand;
import combatant.client.render.engine.rhi.resource.TransientTargetDescriptor;
import combatant.client.render.engine.uniform.impl.JumpShockwaveUniforms;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

public final class WorldBlastShockwaves implements PostProcessPass {
    public static final WorldBlastShockwaves INSTANCE = new WorldBlastShockwaves();

    private static final int MAX_SHOCKWAVES = 12;
    private static final String OWNER = "WorldBlastShockwaves";
    private static final String TEMP_A = "combatant-blast-shockwave-a";
    private static final String TEMP_B = "combatant-blast-shockwave-b";

    private final List<Request> pending = new ArrayList<>();
    private long pendingFrameId = Long.MIN_VALUE;

    private WorldBlastShockwaves() {
    }

    public static void submit(Vec3 center, float radius, float strength, boolean depthTest) {
        INSTANCE.enqueue(center, radius, strength, depthTest);
    }

    public static void clear() {
        INSTANCE.clearPending();
    }

    private synchronized void enqueue(Vec3 center, float radius, float strength, boolean depthTest) {
        if (center == null || radius <= 0.001f || strength <= 0.0001f) return;
        AABB bounds = new AABB(
                center.x - radius, center.y - 0.08, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius
        );
        if (!Renderer3D.Culling.isInFrustum(bounds) || !Renderer3D.Culling.isSectionVisible(bounds)) return;

        long frameId = currentFrameId();
        if (pendingFrameId != frameId) {
            pending.clear();
            pendingFrameId = frameId;
        }
        if (pending.size() >= MAX_SHOCKWAVES) return;
        pending.add(new Request(center, radius, strength, depthTest));
    }

    @Override
    public synchronized boolean isActive() {
        return !pending.isEmpty() && pendingFrameId == currentFrameId();
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public Phase getPhase() {
        return Phase.PRE_HAND;
    }

    @Override
    public boolean render(PostProcessExecutionContext execution) {
        if (execution == null || execution.context() == null || execution.source() == null || execution.destination() == null) {
            drain();
            return false;
        }

        List<Request> requests = drain();
        if (requests.isEmpty()) return false;

        GpuTextureView depth = execution.context().preTranslucentDepth();
        boolean depthUsable = depth != null
                && depth.getWidth(0) == execution.context().width()
                && depth.getHeight(0) == execution.context().height();

        List<Request> valid = new ArrayList<>(requests.size());
        for (Request request : requests) {
            if (!request.depthTest() || depthUsable) valid.add(request);
        }
        if (valid.isEmpty()) return false;

        Vec3 camera = RenderState.cameraPos;
        if (camera == null) return false;
        Matrix4f view = new Matrix4f().rotation(new Quaternionf(RenderState.cameraRotation).conjugate());
        Matrix4f inverseViewProjection = new Matrix4f(RenderState.worldProjection).mul(view).invert();
        boolean zeroToOneDepth = execution.rhi().capabilities().zeroToOneDepth();

        TextureTarget tempA = null;
        TextureTarget tempB = null;
        if (valid.size() > 1) {
            int width = execution.context().width();
            int height = execution.context().height();
            tempA = CombatantRenderSystem.resources().frameTransient(
                    TransientTargetDescriptor.frame(TEMP_A, width, height, false, OWNER));
            if (valid.size() > 2) {
                tempB = CombatantRenderSystem.resources().frameTransient(
                        TransientTargetDescriptor.frame(TEMP_B, width, height, false, OWNER));
            }
            if (tempA == null || tempA.getColorTextureView() == null) return false;
            if (valid.size() > 2 && (tempB == null || tempB.getColorTextureView() == null)) return false;
        }

        GpuSampler linear = PostProcessManager.getSampler();
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuTextureView source = execution.source();

        for (int i = 0; i < valid.size(); i++) {
            Request request = valid.get(i);
            boolean last = i == valid.size() - 1;
            GpuTextureView destination;
            if (last) {
                destination = execution.destination();
            } else if ((i & 1) == 0) {
                destination = tempA.getColorTextureView();
            } else {
                destination = tempB != null ? tempB.getColorTextureView() : tempA.getColorTextureView();
            }

            JumpShockwaveUniforms.update(
                    inverseViewProjection,
                    request.center().subtract(camera),
                    request.radius(),
                    0.0f,
                    request.strength(),
                    0xFFFFFFFF,
                    request.depthTest(),
                    zeroToOneDepth
            );
            execution.rhi().drawFullscreen(
                    FullscreenDrawCommand.builder("Combatant blast shockwave")
                            .colorAttachment(destination)
                            .pipeline(PostProcessRenderPipelines.POSTPROCESS_BLAST_SHOCKWAVE)
                            .uniform("JumpShockwave", JumpShockwaveUniforms.get())
                            .sampler("u_Texture", source, linear)
                            .sampler("u_Depth", request.depthTest() ? depth : source, nearest)
                            .build()
            );
            source = destination;
        }
        return true;
    }

    private synchronized List<Request> drain() {
        long frameId = currentFrameId();
        if (pendingFrameId != frameId || pending.isEmpty()) {
            pending.clear();
            pendingFrameId = frameId;
            return List.of();
        }
        List<Request> out = List.copyOf(pending);
        pending.clear();
        return out;
    }

    private synchronized void clearPending() {
        pending.clear();
        pendingFrameId = Long.MIN_VALUE;
    }

    private static long currentFrameId() {
        RenderFrameContext context = CombatantRenderSystem.currentContext();
        return context != null ? context.frameId() : CombatantRenderSystem.resources().frameId();
    }

    private record Request(Vec3 center, float radius, float strength, boolean depthTest) {
    }
}
