/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.lens;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.depth.EntityDepthLayer;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessExecutionContext;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.rhi.FullscreenDrawCommand;
import combatant.client.render.engine.uniform.impl.TargetLensUniforms;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public final class WorldTargetLenses implements PostProcessPass {
    public static final WorldTargetLenses INSTANCE = new WorldTargetLenses();

    private final List<Request> pending = new ArrayList<>();
    private long pendingFrameId = Long.MIN_VALUE;

    private WorldTargetLenses() {
    }

    public static void submit(Vec3 position, Vec3 axis, float radius, float axialLength, float strength,
                              Renderer3D.DepthMode depthMode, int tintArgb, float phase) {
        INSTANCE.enqueue(position, axis, radius, axialLength, strength, depthMode, tintArgb, phase, 1.0f);
    }

    public static void submit(Vec3 position, Vec3 axis, float radius, float axialLength, float strength,
                              Renderer3D.DepthMode depthMode, int tintArgb, float phase, float chromaticAmount) {
        INSTANCE.enqueue(position, axis, radius, axialLength, strength, depthMode, tintArgb, phase, chromaticAmount);
    }

    public static void clear() {
        INSTANCE.clearPending();
    }

    private synchronized void enqueue(Vec3 position, Vec3 axis, float radius, float axialLength, float strength,
                                      Renderer3D.DepthMode depthMode, int tintArgb, float phase, float chromaticAmount) {
        if (position == null || axis == null || radius <= 0.001f || axialLength <= 0.001f || strength <= 0.0001f) return;
        double extent = Math.max(radius, axialLength);
        AABB bounds = new AABB(
                position.x - extent, position.y - extent, position.z - extent,
                position.x + extent, position.y + extent, position.z + extent
        );
        if (!Renderer3D.Culling.isInFrustum(bounds)) return;
        if (depthMode != Renderer3D.DepthMode.ENTITY_ONLY && !Renderer3D.Culling.isSectionVisible(bounds)) return;

        long frameId = currentFrameId();
        if (pendingFrameId != frameId) {
            pending.clear();
            pendingFrameId = frameId;
        }
        if (pending.size() >= TargetLensUniforms.MAX_LENSES) return;
        pending.add(new Request(position, axis.normalize(), radius, axialLength, strength,
                depthMode != null ? depthMode : Renderer3D.DepthMode.NONE,
                tintArgb, phase, Math.max(0.0f, Math.min(1.0f, chromaticAmount))));
    }

    @Override
    public synchronized boolean isActive() {
        return !pending.isEmpty() && pendingFrameId == currentFrameId();
    }

    @Override
    public int getPriority() {
        return 3;
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

        Renderer3D.DepthMode depthMode = requests.getFirst().depthMode();
        GpuTextureView depth = depthMode == Renderer3D.DepthMode.ENTITY_ONLY
                ? EntityDepthLayer.depthView()
                : depthMode == Renderer3D.DepthMode.NONE ? null : execution.context().preTranslucentDepth();
        if (depthMode == Renderer3D.DepthMode.PRE_DEPTH
                && !depthSampleable(depth, execution.context().width(), execution.context().height())) {
            depth = execution.context().mainDepth();
        }
        boolean depthUsable = depthSampleable(depth, execution.context().width(), execution.context().height());
        if (depthMode != Renderer3D.DepthMode.NONE && !depthUsable) return false;

        Vec3 camera = RenderState.cameraPos;
        if (camera == null) return false;

        Matrix4f view = new Matrix4f().rotation(new Quaternionf(RenderState.cameraRotation).conjugate());
        Matrix4f viewProjection = new Matrix4f(RenderState.worldProjection).mul(view);
        boolean zeroToOneDepth = execution.rhi().capabilities().zeroToOneDepth();
        float projectionX = RenderState.worldProjection.m00();
        float projectionY = RenderState.worldProjection.m11();
        float aspect = Math.abs(projectionX) > 1.0e-6f ? projectionY / projectionX : 1.0f;

        float[] lenses = new float[TargetLensUniforms.MAX_LENSES * 4];
        float[] colors = new float[TargetLensUniforms.MAX_LENSES * 4];
        float[] shapes = new float[TargetLensUniforms.MAX_LENSES * 4];
        float[] meta = new float[TargetLensUniforms.MAX_LENSES * 4];
        int count = 0;
        float strength = 0.0f;
        Vector4f clip = new Vector4f();
        Vector4f axisClip = new Vector4f();

        for (Request request : requests) {
            boolean useDepth = request.depthMode() != Renderer3D.DepthMode.NONE && depthUsable;
            Vec3 relative = request.position().subtract(camera);
            clip.set((float) relative.x, (float) relative.y, (float) relative.z, 1.0f);
            viewProjection.transform(clip);
            if (clip.w <= 0.05f) continue;

            float invW = 1.0f / clip.w;
            float u = clip.x * invW * 0.5f + 0.5f;
            float v = clip.y * invW * 0.5f + 0.5f;
            float ndcDepth = clip.z * invW;
            float rawDepth = zeroToOneDepth ? ndcDepth : ndcDepth * 0.5f + 0.5f;
            float radiusUv = Math.min(request.radius() * projectionY * invW * 0.5f, 0.35f);
            if (radiusUv <= 0.0001f) continue;

            Vec3 axisWorld = request.position().add(request.axis().scale(request.axialLength()));
            Vec3 axisRelative = axisWorld.subtract(camera);
            axisClip.set((float) axisRelative.x, (float) axisRelative.y, (float) axisRelative.z, 1.0f);
            viewProjection.transform(axisClip);
            if (axisClip.w <= 0.05f) continue;
            float axisInvW = 1.0f / axisClip.w;
            float axisU = axisClip.x * axisInvW * 0.5f + 0.5f;
            float axisV = axisClip.y * axisInvW * 0.5f + 0.5f;
            float axisDx = (axisU - u) * aspect;
            float axisDy = axisV - v;
            float axialUv = Math.max(radiusUv * 1.3f, (float) Math.sqrt(axisDx * axisDx + axisDy * axisDy));
            float axisLen = (float) Math.sqrt(axisDx * axisDx + axisDy * axisDy);
            float axisCos;
            float axisSin;
            if (axisLen <= 1.0e-5f) {
                axisCos = (float) Math.cos(request.phase() * Math.PI * 2.0);
                axisSin = (float) Math.sin(request.phase() * Math.PI * 2.0);
                axisLen = radiusUv * 1.35f;
            } else {
                axisCos = axisDx / axisLen;
                axisSin = axisDy / axisLen;
            }
            float extent = Math.max(radiusUv, axialUv);
            if (u < -extent * 2.0f || u > 1.0f + extent * 2.0f
                    || v < -extent * 2.0f || v > 1.0f + extent * 2.0f) continue;

            int base = count * 4;
            lenses[base] = u;
            lenses[base + 1] = v;
            lenses[base + 2] = rawDepth;
            lenses[base + 3] = useDepth ? radiusUv : -radiusUv;
            int tint = request.tintArgb();
            colors[base] = ((tint >>> 16) & 0xFF) / 255.0f;
            colors[base + 1] = ((tint >>> 8) & 0xFF) / 255.0f;
            colors[base + 2] = (tint & 0xFF) / 255.0f;
            colors[base + 3] = ((tint >>> 24) & 0xFF) / 255.0f;
            shapes[base] = axisCos;
            shapes[base + 1] = axisSin;
            shapes[base + 2] = axialUv;
            shapes[base + 3] = request.phase();
            meta[base] = request.strength();
            meta[base + 1] = request.phase();
            meta[base + 2] = request.axialLength();
            meta[base + 3] = request.chromaticAmount();
            strength = Math.max(strength, request.strength());
            count++;
            if (count >= TargetLensUniforms.MAX_LENSES) break;
        }

        if (count == 0 || strength <= 0.0001f) return false;

        TargetLensUniforms.update(aspect, strength, depthUsable, count, lenses, colors, shapes, meta);
        GpuSampler linear = PostProcessManager.getSampler();
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        execution.rhi().drawFullscreen(
                FullscreenDrawCommand.builder("Combatant target lenses")
                        .colorAttachment(execution.destination())
                        .pipeline(CombatantRenderPipelines.POSTPROCESS_TARGET_LENS)
                        .uniform("TargetLens", TargetLensUniforms.get())
                        .sampler("u_Texture", execution.source(), linear)
                        .sampler("u_Depth", depthUsable ? depth : execution.source(), nearest)
                        .build()
        );
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

    private record Request(Vec3 position, Vec3 axis, float radius, float axialLength, float strength,
                           Renderer3D.DepthMode depthMode, int tintArgb, float phase, float chromaticAmount) {
    }

    private static boolean depthSampleable(GpuTextureView depth, int width, int height) {
        return depth != null && depth.getWidth(0) == width && depth.getHeight(0) == height
                && !(depth.texture() instanceof IMsaaTexture msaa
                    && msaa.combatant$isMsaa() && msaa.combatant$getSamples() > 1);
    }
}
