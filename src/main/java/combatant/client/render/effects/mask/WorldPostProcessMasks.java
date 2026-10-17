/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.mask;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.msaa.MsaaFramebuffer;
import combatant.client.render.engine.pipeline.WorldRenderPipelines;
import combatant.client.render.engine.pipeline.PostProcessRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessExecutionContext;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.rhi.FullscreenDrawCommand;
import combatant.client.render.engine.rhi.resource.TransientTargetDescriptor;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.impl.WorldMaskedPostUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * World-space masks for local post effects. Requests are culled before rasterization and can
 * optionally use the current scene depth for occlusion.
 */
public final class WorldPostProcessMasks implements PostProcessPass {
    public static final WorldPostProcessMasks INSTANCE = new WorldPostProcessMasks();

    private static final String MASK_TARGET_NAME = "combatant-world-post-mask";
    private static final String MASK_RESOLVE_NAME = "combatant-world-post-mask-resolve";
    private static final String MASK_TARGET_OWNER = "WorldPostProcessMasks";
    /** Neutral encoded flow = RG 0.5, zero strength/coverage. */
    private static final int NEUTRAL_CLEAR_ARGB = 0x00808000;
    private static final float MIN_ALPHA = 0.003f;
    private static final int SPHERE_LONGITUDE_SEGMENTS = 20;
    private static final int SPHERE_LATITUDE_SEGMENTS = 10;

    private final List<DistortionQuad> pending = new ArrayList<>();
    private final List<DistortionSphere> pendingSpheres = new ArrayList<>();
    private long pendingFrameId = Long.MIN_VALUE;

    private WorldPostProcessMasks() {
    }

    public static void distortionBillboard(Identifier texture,
                                           Vec3 center,
                                           float halfSize,
                                           float rollRadians,
                                           float strength,
                                           float swirl,
                                           float opacity) {
        distortionBillboard(texture, center, halfSize, rollRadians, strength, swirl, opacity, true);
    }

    public static void distortionBillboard(Identifier texture,
                                           Vec3 center,
                                           float halfSize,
                                           float rollRadians,
                                           float strength,
                                           float swirl,
                                           float opacity,
                                           boolean depthTest) {
        if (texture == null || center == null || halfSize <= 0.0f || opacity <= MIN_ALPHA) return;

        Quaternionf camera = new Quaternionf(RenderState.cameraRotation);
        Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f).rotate(camera);
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f).rotate(camera);
        float c = (float) Math.cos(rollRadians);
        float s = (float) Math.sin(rollRadians);
        Vector3f r = new Vector3f(right).mul(c).add(new Vector3f(up).mul(s)).mul(halfSize);
        Vector3f u = new Vector3f(up).mul(c).sub(new Vector3f(right).mul(s)).mul(halfSize);

        Vec3 p0 = center.add(-r.x() - u.x(), -r.y() - u.y(), -r.z() - u.z());
        Vec3 p1 = center.add( r.x() - u.x(),  r.y() - u.y(),  r.z() - u.z());
        Vec3 p2 = center.add( r.x() + u.x(),  r.y() + u.y(),  r.z() + u.z());
        Vec3 p3 = center.add(-r.x() + u.x(), -r.y() + u.y(), -r.z() + u.z());
        distortionQuad(texture, p0, p1, p2, p3, strength, swirl, opacity, depthTest);
    }

    public static void distortionQuad(Identifier texture,
                                      Vec3 p0,
                                      Vec3 p1,
                                      Vec3 p2,
                                      Vec3 p3,
                                      float strength,
                                      float swirl,
                                      float opacity) {
        distortionQuad(texture, p0, p1, p2, p3, strength, swirl, opacity, true);
    }

    public static void distortionQuad(Identifier texture,
                                      Vec3 p0,
                                      Vec3 p1,
                                      Vec3 p2,
                                      Vec3 p3,
                                      float strength,
                                      float swirl,
                                      float opacity,
                                      boolean depthTest) {
        INSTANCE.enqueue(texture, p0, p1, p2, p3, strength, swirl, opacity, depthTest);
    }

    public static void distortionVolumeSphere(Vec3 center,
                                              float radius,
                                              float phaseRadians,
                                              float strength,
                                              float swirl,
                                              float opacity,
                                              float cavityWeight,
                                              float rimWeight,
                                              float warp) {
        distortionVolumeSphere(center, radius, phaseRadians, strength, swirl, opacity,
                cavityWeight, rimWeight, warp, true);
    }

    public static void distortionVolumeSphere(Vec3 center,
                                              float radius,
                                              float phaseRadians,
                                              float strength,
                                              float swirl,
                                              float opacity,
                                              float cavityWeight,
                                              float rimWeight,
                                              float warp,
                                              boolean depthTest) {
        INSTANCE.enqueueSphere(center, radius, phaseRadians, strength, swirl, opacity,
                cavityWeight, rimWeight, warp, depthTest);
    }

    private synchronized void enqueue(Identifier texture,
                                      Vec3 p0,
                                      Vec3 p1,
                                      Vec3 p2,
                                      Vec3 p3,
                                      float strength,
                                      float swirl,
                                      float opacity,
                                      boolean depthTest) {
        if (texture == null || p0 == null || p1 == null || p2 == null || p3 == null) return;
        float a = Mth.clamp(opacity, 0.0f, 1.0f);
        if (a <= MIN_ALPHA) return;

        AABB bounds = bounds(p0, p1, p2, p3).inflate(0.035);
        if (!Renderer3D.Culling.isInFrustum(bounds) || !Renderer3D.Culling.isSectionVisible(bounds)) return;

        long frameId = currentFrameId();
        if (pendingFrameId != frameId) {
            pending.clear();
            pendingSpheres.clear();
            pendingFrameId = frameId;
        }
        pending.add(new DistortionQuad(
                texture,
                p0, p1, p2, p3,
                Mth.clamp(strength, 0.0f, 1.0f),
                Mth.clamp(swirl, -1.0f, 1.0f),
                a,
                depthTest
        ));
    }

    private synchronized void enqueueSphere(Vec3 center,
                                            float radius,
                                            float phaseRadians,
                                            float strength,
                                            float swirl,
                                            float opacity,
                                            float cavityWeight,
                                            float rimWeight,
                                            float warp,
                                            boolean depthTest) {
        if (center == null || radius <= 0.0f) return;
        float a = Mth.clamp(opacity, 0.0f, 1.0f);
        if (a <= MIN_ALPHA) return;

        double extent = Math.max(0.05, radius);
        AABB bounds = new AABB(
                center.x - extent, center.y - extent, center.z - extent,
                center.x + extent, center.y + extent, center.z + extent
        ).inflate(0.035);
        if (!Renderer3D.Culling.isInFrustum(bounds) || !Renderer3D.Culling.isSectionVisible(bounds)) return;

        long frameId = currentFrameId();
        if (pendingFrameId != frameId) {
            pending.clear();
            pendingSpheres.clear();
            pendingFrameId = frameId;
        }
        pendingSpheres.add(new DistortionSphere(
                center, radius, phaseRadians,
                Mth.clamp(strength, 0.0f, 1.0f),
                Mth.clamp(swirl, -1.0f, 1.0f),
                a,
                Mth.clamp(cavityWeight, 0.0f, 1.0f),
                Mth.clamp(rimWeight, 0.0f, 1.0f),
                Mth.clamp(warp, 0.0f, 1.0f),
                depthTest
        ));
    }

    @Override
    public synchronized boolean isActive() {
        return (!pending.isEmpty() || !pendingSpheres.isEmpty()) && pendingFrameId == currentFrameId();
    }

    @Override
    public int getPriority() {
        return 2;
    }

    @Override
    public Phase getPhase() {
        return Phase.PRE_HAND;
    }

    @Override
    public boolean render(PostProcessExecutionContext execution) {
        if (execution == null || execution.context() == null || execution.source() == null || execution.destination() == null) {
            drainRequests();
            return false;
        }

        MaskRequests requests = drainRequests();
        if (requests.isEmpty()) return false;

        GpuTextureView depth = execution.context().preTranslucentDepth();
        boolean depthUsable = usableDepth(depth, execution.context().width(), execution.context().height());
        if (requests.hasDepthRequests() && !depthUsable && !requests.hasNoDepthRequests()) return false;

        int width = execution.context().width();
        int height = execution.context().height();
        int depthSamples = requests.hasDepthRequests() && depthUsable ? samples(depth) : 1;
        GpuTextureView mask;

        if (depthSamples > 1) {
            if (!CombatantRenderSystem.rhi().msaa().supported()) return false;
            MsaaFramebuffer maskMsaa = CombatantRenderSystem.resources().frameTransientMsaa(
                    new TransientTargetDescriptor(
                            MASK_TARGET_NAME + "-" + depthSamples + "x",
                            width, height, false, GpuFormat.RGBA8_UNORM, depthSamples,
                            TransientTargetDescriptor.Lifetime.FRAME, MASK_TARGET_OWNER
                    )
            );
            TextureTarget resolved = CombatantRenderSystem.resources().frameTransient(
                    TransientTargetDescriptor.frame(MASK_RESOLVE_NAME, width, height, false, MASK_TARGET_OWNER)
            );
            if (maskMsaa == null || maskMsaa.getColorTextureView() == null
                    || resolved == null || resolved.getColorTextureView() == null) return false;
            if (!renderMask(requests, maskMsaa.getColorTextureView(), depthUsable ? depth : null)) return false;
            if (!CombatantRenderSystem.rhi().msaa().resolveTransient(maskMsaa, resolved, true, false)) return false;
            mask = resolved.getColorTextureView();
        } else {
            TextureTarget maskTarget = CombatantRenderSystem.resources().frameTransient(
                    TransientTargetDescriptor.frame(MASK_TARGET_NAME, width, height, false, MASK_TARGET_OWNER)
            );
            if (maskTarget == null || maskTarget.getColorTextureView() == null) return false;
            mask = maskTarget.getColorTextureView();
            if (!renderMask(requests, mask, depthUsable ? depth : null)) return false;
        }

        float timeSeconds = (System.nanoTime() & 0x7FFFFFFFFFFFFFFFL) * 1.0e-9f;
        WorldMaskedPostUniforms.update(timeSeconds, execution.context().width(), execution.context().height(), 1.0f);
        execution.rhi().drawFullscreen(
                FullscreenDrawCommand.builder("Combatant world masked distortion")
                        .colorAttachment(execution.destination())
                        .pipeline(PostProcessRenderPipelines.POSTPROCESS_WORLD_MASK_DISTORTION)
                        .uniform("WorldMaskedPost", WorldMaskedPostUniforms.get())
                        .sampler("u_Texture", execution.source(), PostProcessManager.getSampler())
                        .sampler("u_Mask", mask, PostProcessManager.getSampler())
                        .build()
        );
        return true;
    }

    private boolean renderMask(MaskRequests requests, GpuTextureView mask, GpuTextureView depth) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || RenderState.cameraPos == null) return false;

        boolean attachDepth = hasUsage(depth, GpuTexture.USAGE_RENDER_ATTACHMENT);
        boolean sampleDepth = !attachDepth
                && samples(depth) == 1
                && hasUsage(depth, GpuTexture.USAGE_TEXTURE_BINDING);
        if (depth != null && !attachDepth && !sampleDepth) depth = null;
        GpuSampler depthSampler = sampleDepth
                ? RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                : null;

        Map<QuadBatchKey, List<DistortionQuad>> byTexture = new LinkedHashMap<>();
        for (DistortionQuad request : requests.quads()) {
            if (request.depthTest() && depth == null) continue;
            QuadBatchKey key = new QuadBatchKey(request.texture(), request.depthTest());
            byTexture.computeIfAbsent(key, ignored -> new ArrayList<>()).add(request);
        }
        boolean anySphere = requests.spheres().stream()
                .anyMatch(s -> !s.depthTest() || attachDepth || sampleDepth);
        if (byTexture.isEmpty() && !anySphere) return false;

        Matrix4f previousProjection = MeshRenderer.projection();
        boolean previousRendering3D = RenderState.rendering3D;
        var mv = RenderSystem.getModelViewStack();
        boolean drew = false;
        boolean first = true;
        mv.pushMatrix();
        try {
            mv.identity();
            mv.mul(new Matrix4f().rotation(new Quaternionf(RenderState.cameraRotation).conjugate()));
            MeshRenderer.setProjection(new Matrix4f(RenderState.worldProjection));
            RenderState.rendering3D = true;

            for (Map.Entry<QuadBatchKey, List<DistortionQuad>> entry : byTexture.entrySet()) {
                QuadBatchKey key = entry.getKey();
                AbstractTexture texture = mc.getTextureManager().getTexture(key.texture());
                if (texture == null || texture.getTextureView() == null || texture.getSampler() == null) continue;

                var pipeline = key.depthTest()
                        ? sampleDepth
                        ? WorldRenderPipelines.WORLD_POST_DISTORTION_MASK_SAMPLED_DEPTH
                        : WorldRenderPipelines.WORLD_POST_DISTORTION_MASK_DEPTH
                        : WorldRenderPipelines.WORLD_POST_DISTORTION_MASK;
                MeshBuilder mesh = new MeshBuilder(pipeline);
                try {
                    mesh.beginWorld(RenderState.cameraPos);
                    List<DistortionQuad> quads = entry.getValue();
                    mesh.ensureCapacity(quads.size() * 4, quads.size() * 6);
                    for (DistortionQuad q : quads) addQuad(mesh, q);
                    mesh.end();
                    if (mesh.getIndicesCount() <= 0) continue;

                    MeshRenderer renderer = MeshRenderer.begin()
                            .attachments(mask, key.depthTest() && attachDepth ? depth : null)
                            .clearColor(first ? NEUTRAL_CLEAR_ARGB : null)
                            .pipeline(pipeline)
                            .mesh(mesh)
                            .sampler("u_Texture", texture.getTextureView(), texture.getSampler());
                    if (key.depthTest() && sampleDepth) {
                        renderer.sampler("u_Depth", depth, depthSampler);
                    }
                    renderer.end();
                    first = false;
                    drew = true;
                } finally {
                    mesh.close();
                }
            }

            for (boolean depthTest : new boolean[]{true, false}) {
                if (depthTest && depth == null) continue;
                List<DistortionSphere> spheres = requests.spheres().stream()
                        .filter(sphere -> sphere.depthTest() == depthTest)
                        .toList();
                if (spheres.isEmpty()) continue;

                var pipeline = depthTest
                        ? sampleDepth
                        ? WorldRenderPipelines.WORLD_SPHERE_DISTORTION_MASK_SAMPLED_DEPTH
                        : WorldRenderPipelines.WORLD_SPHERE_DISTORTION_MASK_DEPTH
                        : WorldRenderPipelines.WORLD_SPHERE_DISTORTION_MASK;
                MeshBuilder mesh = new MeshBuilder(pipeline);
                try {
                    mesh.beginWorld(RenderState.cameraPos);
                    int sphereQuads = spheres.size() * SPHERE_LONGITUDE_SEGMENTS * SPHERE_LATITUDE_SEGMENTS;
                    mesh.ensureCapacity(sphereQuads * 4, sphereQuads * 6);
                    for (DistortionSphere sphere : spheres) addSphere(mesh, sphere, RenderState.cameraPos);
                    mesh.end();
                    if (mesh.getIndicesCount() <= 0) continue;

                    MeshRenderer renderer = MeshRenderer.begin()
                            .attachments(mask, depthTest && attachDepth ? depth : null)
                            .clearColor(first ? NEUTRAL_CLEAR_ARGB : null)
                            .pipeline(pipeline)
                            .mesh(mesh);
                    if (depthTest && sampleDepth) {
                        renderer.sampler("u_Depth", depth, depthSampler);
                    }
                    renderer.end();
                    first = false;
                    drew = true;
                } finally {
                    mesh.close();
                }
            }
        } finally {
            RenderState.rendering3D = previousRendering3D;
            MeshRenderer.setProjection(previousProjection);
            mv.popMatrix();
        }
        return drew;
    }

    private static void addQuad(MeshBuilder mesh, DistortionQuad q) {
        int strength = Mth.clamp(Math.round(q.strength() * 255.0f), 0, 255);
        int swirl = Mth.clamp(Math.round((q.swirl() * 0.5f + 0.5f) * 255.0f), 0, 255);
        int alpha = Mth.clamp(Math.round(q.opacity() * 255.0f), 0, 255);

        mesh.ensureQuadCapacity();
        int i0 = mesh.vec3(q.p0().x, q.p0().y, q.p0().z).vec2(0.0, 1.0).color(strength, swirl, 128, alpha).next();
        int i1 = mesh.vec3(q.p1().x, q.p1().y, q.p1().z).vec2(1.0, 1.0).color(strength, swirl, 128, alpha).next();
        int i2 = mesh.vec3(q.p2().x, q.p2().y, q.p2().z).vec2(1.0, 0.0).color(strength, swirl, 128, alpha).next();
        int i3 = mesh.vec3(q.p3().x, q.p3().y, q.p3().z).vec2(0.0, 0.0).color(strength, swirl, 128, alpha).next();
        mesh.quad(i0, i1, i2, i3);
    }

    private static void addSphere(MeshBuilder mesh, DistortionSphere sphere, Vec3 cameraPos) {
        double tau = Math.PI * 2.0;
        double halfPi = Math.PI * 0.5;
        boolean cameraInside = cameraPos != null && cameraPos.distanceToSqr(sphere.center()) < sphere.radius() * sphere.radius();

        for (int lat = 0; lat < SPHERE_LATITUDE_SEGMENTS; lat++) {
            double v0 = lat / (double) SPHERE_LATITUDE_SEGMENTS;
            double v1 = (lat + 1) / (double) SPHERE_LATITUDE_SEGMENTS;
            double phi0 = -halfPi + Math.PI * v0;
            double phi1 = -halfPi + Math.PI * v1;

            for (int lon = 0; lon < SPHERE_LONGITUDE_SEGMENTS; lon++) {
                double u0 = lon / (double) SPHERE_LONGITUDE_SEGMENTS;
                double u1 = (lon + 1) / (double) SPHERE_LONGITUDE_SEGMENTS;
                double theta0 = tau * u0 + sphere.phaseRadians();
                double theta1 = tau * u1 + sphere.phaseRadians();

                mesh.ensureQuadCapacity();
                int i00 = sphereMaskVertex(mesh, sphere, phi0, theta0, u0, v0);
                int i10 = sphereMaskVertex(mesh, sphere, phi1, theta0, u0, v1);
                int i11 = sphereMaskVertex(mesh, sphere, phi1, theta1, u1, v1);
                int i01 = sphereMaskVertex(mesh, sphere, phi0, theta1, u1, v0);

                if (cameraInside) mesh.quad(i00, i01, i11, i10);
                else mesh.quad(i00, i10, i11, i01);
            }
        }
    }

    private static int sphereMaskVertex(MeshBuilder mesh,
                                        DistortionSphere sphere,
                                        double phi,
                                        double theta,
                                        double u,
                                        double v) {
        double cosPhi = Math.cos(phi);
        float nx = (float) (Math.cos(theta) * cosPhi);
        float ny = (float) Math.sin(phi);
        float nz = (float) (Math.sin(theta) * cosPhi);
        Vec3 p = sphere.center().add(nx * sphere.radius(), ny * sphere.radius(), nz * sphere.radius());

        int cavity = Mth.clamp(Math.round(sphere.cavityWeight() * 255.0f), 0, 255);
        int rim = Mth.clamp(Math.round(sphere.rimWeight() * 255.0f), 0, 255);
        return mesh.vec3(p.x, p.y, p.z)
                .vec2(u, v)
                .color(cavity, rim, 255, 255)
                .vec4(sphere.strength(), sphere.swirl(), sphere.phaseRadians(), sphere.warp())
                .vec4(nx, ny, nz, sphere.opacity())
                .next();
    }

    private synchronized MaskRequests drainRequests() {
        if (pending.isEmpty() && pendingSpheres.isEmpty()) return MaskRequests.EMPTY;
        long frameId = currentFrameId();
        if (pendingFrameId != frameId) {
            pending.clear();
            pendingSpheres.clear();
            pendingFrameId = frameId;
            return MaskRequests.EMPTY;
        }
        MaskRequests out = new MaskRequests(List.copyOf(pending), List.copyOf(pendingSpheres));
        pending.clear();
        pendingSpheres.clear();
        return out;
    }

    private static long currentFrameId() {
        RenderFrameContext context = CombatantRenderSystem.currentContext();
        return context != null ? context.frameId() : CombatantRenderSystem.resources().frameId();
    }

    private static boolean usableDepth(GpuTextureView depth, int width, int height) {
        return depth != null
                && depth.getWidth(0) == width
                && depth.getHeight(0) == height
                && (hasUsage(depth, GpuTexture.USAGE_RENDER_ATTACHMENT)
                || (samples(depth) == 1 && hasUsage(depth, GpuTexture.USAGE_TEXTURE_BINDING)));
    }

    private static boolean hasUsage(GpuTextureView view, int usage) {
        return view != null && view.texture() != null && (view.texture().usage() & usage) != 0;
    }

    private static int samples(GpuTextureView view) {
        if (view == null || view.texture() == null) return 1;
        return view.texture() instanceof IMsaaTexture msaa && msaa.combatant$isMsaa()
                ? Math.max(1, msaa.combatant$getSamples())
                : 1;
    }

    private static AABB bounds(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        double minX = Math.min(Math.min(p0.x, p1.x), Math.min(p2.x, p3.x));
        double minY = Math.min(Math.min(p0.y, p1.y), Math.min(p2.y, p3.y));
        double minZ = Math.min(Math.min(p0.z, p1.z), Math.min(p2.z, p3.z));
        double maxX = Math.max(Math.max(p0.x, p1.x), Math.max(p2.x, p3.x));
        double maxY = Math.max(Math.max(p0.y, p1.y), Math.max(p2.y, p3.y));
        double maxZ = Math.max(Math.max(p0.z, p1.z), Math.max(p2.z, p3.z));
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private record DistortionQuad(
            Identifier texture,
            Vec3 p0,
            Vec3 p1,
            Vec3 p2,
            Vec3 p3,
            float strength,
            float swirl,
            float opacity,
            boolean depthTest
    ) {
    }

    private record DistortionSphere(
            Vec3 center,
            float radius,
            float phaseRadians,
            float strength,
            float swirl,
            float opacity,
            float cavityWeight,
            float rimWeight,
            float warp,
            boolean depthTest
    ) {
    }

    private record QuadBatchKey(Identifier texture, boolean depthTest) {
    }

    private record MaskRequests(List<DistortionQuad> quads, List<DistortionSphere> spheres) {
        private static final MaskRequests EMPTY = new MaskRequests(List.of(), List.of());

        private boolean isEmpty() {
            return quads.isEmpty() && spheres.isEmpty();
        }

        private boolean hasDepthRequests() {
            return quads.stream().anyMatch(DistortionQuad::depthTest)
                    || spheres.stream().anyMatch(DistortionSphere::depthTest);
        }

        private boolean hasNoDepthRequests() {
            return quads.stream().anyMatch(q -> !q.depthTest())
                    || spheres.stream().anyMatch(s -> !s.depthTest());
        }
    }

}
