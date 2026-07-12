/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.core.policy.VisibilityProvider;
import combatant.client.render.engine.core.policy.VisibilityQuery;
import combatant.client.render.engine.math.RenderMath;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.world.WorldCommandBuffer;
import combatant.client.render.engine.world.WorldPassCompiler;
import combatant.client.render.engine.world.WorldRenderStatsSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * Renderer3D is the public world-primitive API only:
 * - modules still call line/tri/quad/batch exactly as before;
 * - calls record CPU-side world commands into WorldCommandBuffer;
 * - WorldPassCompiler converts commands to RHI draw commands;
 * - mesh upload/draw/depth/fog/visibility are delegated to context/RHI/providers.
 */
public final class Renderer3D {
    private static final String DEFAULT_SAMPLER = "u_Texture";
    private static final WorldPassCompiler WORLD_COMPILER = new WorldPassCompiler();

    private final RenderPipeline linesPipeline;
    private final RenderPipeline trisPipeline;
    private final WorldCommandBuffer commands = new WorldCommandBuffer();

    public Renderer3D(RenderPipeline linesPipeline, RenderPipeline trisPipeline) {
        this.linesPipeline = linesPipeline;
        this.trisPipeline = trisPipeline;
    }

    public static WorldRenderStatsSnapshot worldStatsSnapshot() {
        return WORLD_COMPILER.statsSnapshot();
    }

    /**
     * Call once per frame/phase before modules write geometry.
     */
    public void begin() {
        RenderFrameContext ctx = CombatantRenderSystem.ensureFrameContext();
        WORLD_COMPILER.beginFrame(ctx.frameId());
        commands.beginFrame();
    }

    public MeshBuilder lines() {
        return commands.batch(linesPipeline, DepthMode.MAIN, RenderState.lineWidth, BatchBindings.none());
    }

    public MeshBuilder tris() {
        return commands.batch(trisPipeline, DepthMode.MAIN, 1.0f, BatchBindings.none());
    }

    public MeshBuilder batch(RenderPipeline pipeline) {
        return batch(pipeline, DepthMode.MAIN);
    }

    public MeshBuilder batch(RenderPipeline pipeline, DepthMode depthMode) {
        return batch(pipeline, depthMode, BatchBindings.none());
    }

    public MeshBuilder batch(RenderPipeline pipeline, DepthMode depthMode, BatchBindings bindings) {
        return commands.batch(pipeline, depthMode, RenderState.lineWidth, bindings);
    }

    public MeshBuilder batchTextured(RenderPipeline pipeline, Identifier textureId) {
        return batchTextured(pipeline, textureId, DepthMode.MAIN);
    }

    public MeshBuilder batchTextured(RenderPipeline pipeline, Identifier textureId, DepthMode depthMode) {
        return batch(pipeline, depthMode,
                BatchBindings.none().withSampler(DEFAULT_SAMPLER, textureId));
    }

    public MeshBuilder batchTextured(RenderPipeline pipeline,
                                     GpuTextureView view,
                                     GpuSampler sampler) {
        return batchTextured(pipeline, view, sampler, DepthMode.MAIN);
    }

    public MeshBuilder batchTextured(RenderPipeline pipeline,
                                     GpuTextureView view,
                                     GpuSampler sampler,
                                     DepthMode depthMode) {
        return batch(pipeline, depthMode,
                BatchBindings.none().withSampler(DEFAULT_SAMPLER, view, sampler));
    }

    public MeshBuilder batchTextured(RenderPipeline pipeline,
                                     AbstractTexture texture,
                                     DepthMode depthMode) {
        if (texture == null) return null;
        return batchTextured(pipeline, texture.getTextureView(), texture.getSampler(), depthMode);
    }

    // -----------------------
    // Convenience API
    // -----------------------

    public void line(double x1, double y1, double z1,
                     double x2, double y2, double z2,
                     int r, int g, int b, int a) {

        MeshBuilder mesh = lines();
        if (mesh == null) return;
        mesh.ensureLineCapacity();
        int i1 = mesh.vec3(x1, y1, z1).color(r, g, b, a).next();
        int i2 = mesh.vec3(x2, y2, z2).color(r, g, b, a).next();
        mesh.line(i1, i2);
    }

    public void lineGradient(double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             int argb1, int argb2) {
        int a1 = (argb1 >>> 24) & 255;
        int r1 = (argb1 >>> 16) & 255;
        int g1 = (argb1 >>> 8) & 255;
        int b1 = argb1 & 255;
        int a2 = (argb2 >>> 24) & 255;
        int r2 = (argb2 >>> 16) & 255;
        int g2 = (argb2 >>> 8) & 255;
        int b2 = argb2 & 255;

        MeshBuilder mesh = lines();
        if (mesh == null) return;
        mesh.ensureLineCapacity();
        int i1 = mesh.vec3(x1, y1, z1).color(r1, g1, b1, a1).next();
        int i2 = mesh.vec3(x2, y2, z2).color(r2, g2, b2, a2).next();
        mesh.line(i1, i2);
    }

    public void triangle(double x1, double y1, double z1,
                         double x2, double y2, double z2,
                         double x3, double y3, double z3,
                         int r, int g, int b, int a) {

        MeshBuilder mesh = tris();
        if (mesh == null) return;
        mesh.ensureTriCapacity();
        int i1 = mesh.vec3(x1, y1, z1).color(r, g, b, a).next();
        int i2 = mesh.vec3(x2, y2, z2).color(r, g, b, a).next();
        int i3 = mesh.vec3(x3, y3, z3).color(r, g, b, a).next();
        mesh.triangle(i1, i2, i3);
    }

    public void quad(double x1, double y1, double z1,
                     double x2, double y2, double z2,
                     double x3, double y3, double z3,
                     double x4, double y4, double z4,
                     int r, int g, int b, int a) {

        MeshBuilder mesh = tris();
        if (mesh == null) return;
        mesh.ensureQuadCapacity();
        int i1 = mesh.vec3(x1, y1, z1).color(r, g, b, a).next();
        int i2 = mesh.vec3(x2, y2, z2).color(r, g, b, a).next();
        int i3 = mesh.vec3(x3, y3, z3).color(r, g, b, a).next();
        int i4 = mesh.vec3(x4, y4, z4).color(r, g, b, a).next();
        mesh.quad(i1, i2, i3, i4);
    }

    // -----------------------
    // Submit
    // -----------------------

    /**
     * Submit recorded world commands to the current main framebuffer.
     */
    public void render(PoseStack matrices) {
        Minecraft mc = Minecraft.getInstance();
        WORLD_COMPILER.submit(commands, mc != null ? mc.gameRenderer.mainRenderTarget() : null, matrices);
    }

    public enum DepthMode {
        MAIN,
        PRE_DEPTH,
        NONE
    }

    public enum Culling {
        ;

        public static boolean shouldRender(Entity target, float tickDelta, CullOptions options) {
            if (target == null) return false;
            if (options == null || options == CullOptions.NONE) return true;

            AABB visibilityBox = interpolatedBox(target, tickDelta);
            if (options.frustum() && !isInFrustum(visibilityBox)) return false;
            if (options.sectionVisibility() && !isSectionVisible(visibilityBox)) return false;
            if (options.lookAtTarget() && !isInFront(target, tickDelta, options.minLookDot())) return false;

            if (options.visibleOnly()) {
                if (!(options.skipVisibilityWhenTranslucent() && RenderState.worldTranslucent)) {
                    Minecraft mc = Minecraft.getInstance();
                    return mc == null || mc.player == null || mc.player.hasLineOfSight(target);
                }
            }

            return true;
        }

        public static boolean isInFrustum(Entity target, float tickDelta) {
            return isInFrustum(interpolatedBox(target, tickDelta));
        }

        public static boolean isInFrustum(AABB box) {
            if (RenderState.frustum == null || box == null) return true;
            return RenderState.frustum.isVisible(box);
        }

        public static boolean isSectionVisible(AABB box) {
            if (box == null) return true;
            RenderFrameContext ctx = CombatantRenderSystem.currentContext();
            VisibilityProvider provider = ctx != null ? ctx.visibilityProvider() : CombatantRenderSystem.sodium().visibilityProvider();
            if (provider == null) return true;
            return provider.isBoxVisible(box, VisibilityQuery.worldOverlay(box));
        }

        private static AABB interpolatedBox(Entity target, float tickDelta) {
            AABB box = target.getBoundingBox();
            Vec3 pos = RenderMath.getLerpedPos(target, tickDelta);
            Vec3 offset = pos.subtract(target.position());
            return box.move(offset);
        }

        public static boolean isInFront(Entity target, float tickDelta, double minDot) {
            Vec3 center = getTargetCenter(target, tickDelta);
            Vec3 camPos = RenderState.cameraPos;
            Vec3 to = center.subtract(camPos);
            if (to.lengthSqr() <= 1.0e-6) return true;
            Vec3 look = RenderState.cameraLook;
            if (look.lengthSqr() <= 1.0e-6) {
                look = Vec3.directionFromRotation(RenderState.cameraPitch, RenderState.cameraYaw).normalize();
            }
            return look.dot(to.normalize()) > minDot;
        }

        private static Vec3 getTargetCenter(Entity target, float tickDelta) {
            Vec3 pos = RenderMath.getLerpedPos(target, tickDelta);
            return pos.add(0.0, target.getBbHeight() * 0.5, 0.0);
        }
    }

    @FunctionalInterface
    public interface UniformResolver {
        GpuBufferSlice resolve();
    }

    // -----------------------
    // Culling helpers
    // -----------------------

    public static final class BatchBindings {
        private static final BatchBindings EMPTY = new BatchBindings(List.of(), List.of());

        private final List<SamplerBinding> samplers;
        private final List<UniformBinding> uniforms;

        private BatchBindings(List<SamplerBinding> samplers, List<UniformBinding> uniforms) {
            this.samplers = samplers;
            this.uniforms = uniforms;
        }

        public static BatchBindings none() {
            return EMPTY;
        }

        private static String nameOrDefault(String name) {
            return name != null ? name : DEFAULT_SAMPLER;
        }

        public BatchBindings withSampler(String name, Identifier textureId) {
            if (textureId == null) return this;
            return appendSampler(new SamplerBinding(nameOrDefault(name), textureId, null, null));
        }

        public BatchBindings withSampler(String name, GpuTextureView view, GpuSampler sampler) {
            if (view == null || sampler == null) return this;
            return appendSampler(new SamplerBinding(nameOrDefault(name), null, view, sampler));
        }

        public BatchBindings withUniform(String name, GpuBufferSlice slice) {
            if (name == null || slice == null) return this;
            return appendUniform(UniformBinding.direct(name, slice));
        }

        public BatchBindings withUniform(String name, Object mergeKey, UniformResolver resolver) {
            if (name == null || mergeKey == null || resolver == null) return this;
            return appendUniform(UniformBinding.deferred(name, mergeKey, resolver));
        }

        private BatchBindings appendSampler(SamplerBinding binding) {
            ArrayList<SamplerBinding> next = new ArrayList<>(samplers.size() + 1);
            next.addAll(samplers);
            next.add(binding);
            return new BatchBindings(List.copyOf(next), uniforms);
        }

        private BatchBindings appendUniform(UniformBinding binding) {
            ArrayList<UniformBinding> next = new ArrayList<>(uniforms.size() + 1);
            next.addAll(uniforms);
            next.add(binding);
            return new BatchBindings(samplers, List.copyOf(next));
        }

        public boolean compatibleWith(BatchBindings other) {
            if (other == null) return false;
            if (samplers.size() != other.samplers.size() || uniforms.size() != other.uniforms.size()) {
                return false;
            }

            for (int i = 0; i < samplers.size(); i++) {
                if (!samplers.get(i).sameBinding(other.samplers.get(i))) {
                    return false;
                }
            }

            for (int i = 0; i < uniforms.size(); i++) {
                if (!uniforms.get(i).sameBinding(other.uniforms.get(i))) {
                    return false;
                }
            }

            return true;
        }

        public void applyTo(RhiDrawCommand.Builder builder, Minecraft mc) {
            for (SamplerBinding sampler : samplers) {
                sampler.apply(builder, mc);
            }
            for (UniformBinding uniform : uniforms) {
                uniform.apply(builder);
            }
        }
    }

    public record CullOptions(boolean frustum,
                              boolean sectionVisibility,
                              boolean visibleOnly,
                              boolean lookAtTarget,
                              boolean skipVisibilityWhenTranslucent,
                              double minLookDot) {
        public static final CullOptions NONE =
                new CullOptions(false, false, false, false, false, 0.0);
        public static final CullOptions FRUSTUM_LOOK =
                new CullOptions(true, true, false, true, false, 0.05);
        public static final CullOptions SMART_TARGET =
                new CullOptions(true, true, true, true, true, 0.05);

        public CullOptions withoutSectionVisibility() {
            return new CullOptions(frustum, false, visibleOnly, lookAtTarget, skipVisibilityWhenTranslucent, minLookDot);
        }
    }

    private record SamplerBinding(String name,
                                  Identifier textureId,
                                  GpuTextureView view,
                                  GpuSampler sampler) {
        private boolean sameBinding(SamplerBinding other) {
            if (other == null) return false;
            if (!name.equals(other.name)) return false;
            if (textureId != null || other.textureId != null) {
                return textureId != null && textureId.equals(other.textureId);
            }
            return view == other.view && sampler == other.sampler;
        }

        private void apply(RhiDrawCommand.Builder builder, Minecraft mc) {
            if (textureId != null) {
                if (mc == null) return;
                AbstractTexture texture = mc.getTextureManager().getTexture(textureId);
                if (texture == null) return;
                GpuTextureView textureView = texture.getTextureView();
                GpuSampler textureSampler = texture.getSampler();
                if (textureView == null || textureSampler == null) return;
                builder.sampler(name, textureView, textureSampler);
                return;
            }

            if (view != null && sampler != null) {
                builder.sampler(name, view, sampler);
            }
        }
    }

    private record UniformBinding(String name,
                                  Object mergeKey,
                                  UniformResolver resolver) {
        private static UniformBinding direct(String name, GpuBufferSlice slice) {
            return new UniformBinding(name, slice, () -> slice);
        }

        private static UniformBinding deferred(String name, Object mergeKey, UniformResolver resolver) {
            return new UniformBinding(name, mergeKey, resolver);
        }

        private boolean sameBinding(UniformBinding other) {
            if (other == null) return false;
            return name.equals(other.name) && mergeKey.equals(other.mergeKey);
        }

        private void apply(RhiDrawCommand.Builder builder) {
            GpuBufferSlice slice = resolver.resolve();
            if (slice != null) {
                builder.uniform(name, slice);
            }
        }
    }
}
