/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.render.engine.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.render.engine.rhi.FullscreenDrawCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import combatant.client.render.engine.rhi.MeshOwnership;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.uniform.MeshBuilder;

import java.util.HashMap;
import java.util.List;

/**
 * RenderPass-based mesh renderer for 26.2 GPU API.
 * - Uploads MeshBuilder to immediate GPU buffers.
 * - Creates RenderPass with color/depth attachments.
 * - Writes MeshData UBO (ProjMat + ModelViewMat) via MeshUniforms.
 */
public final class MeshRenderer {
    private static final MeshRenderer INSTANCE = new MeshRenderer();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static boolean taken;
    private final HashMap<String, GpuBufferSlice> uniforms = new HashMap<>();
    private final HashMap<String, SamplerBinding> samplers = new HashMap<>();
    private @Nullable GpuTextureView colorAttachment;
    private @Nullable GpuTextureView depthAttachment;
    private @Nullable Integer clearColorArgb; // OptionalInt in renderpass
    private @Nullable RenderPipeline pipeline;
    private @Nullable MeshBuilder mesh;
    private @Nullable GpuBuffer vertexBuffer;
    private @Nullable GpuBuffer indexBuffer;
    private @Nullable Matrix4f transform;

    private MeshRenderer() {
    }

    /**
     * Set per-frame projection matrix used for MeshData UBO. Call from render hook once per frame.
     */
    public static void setProjection(Matrix4f projection) {
        PROJECTION.set(projection);
    }

    public static Matrix4f projection() {
        return new Matrix4f(PROJECTION);
    }

    public static MeshRenderer begin() {
        if (taken) throw new IllegalStateException("Previous MeshRenderer.begin() was not ended.");
        taken = true;
        return INSTANCE;
    }

    /**
     * Apply camera Y translation in MV (X/Z handled in MeshBuilder for precision).
     */
    private static void applyCameraPosY(Matrix4fStack mv) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
        mv.translate(0.0f, (float) -cameraPos.y, 0.0f);
    }

    public MeshRenderer attachments(GpuTextureView color, @Nullable GpuTextureView depth) {
        this.colorAttachment = color;
        this.depthAttachment = depth;
        return this;
    }

    public MeshRenderer attachments(RenderTarget framebuffer) {
        this.colorAttachment = framebuffer.getColorTextureView();
        this.depthAttachment = framebuffer.getDepthTextureView();
        return this;
    }

    /**
     * Optional clear (ARGB). If null -> no clear.
     */
    public MeshRenderer clearColor(@Nullable Integer argb) {
        this.clearColorArgb = argb;
        return this;
    }

    public MeshRenderer pipeline(RenderPipeline pipeline) {
        this.pipeline = pipeline;
        return this;
    }

    public MeshRenderer mesh(GpuBuffer vertices, GpuBuffer indices) {
        this.vertexBuffer = vertices;
        this.indexBuffer = indices;
        return this;
    }

    public MeshRenderer mesh(MeshBuilder mesh) {
        this.mesh = mesh;
        return this;
    }

    public MeshRenderer mesh(MeshBuilder mesh, Matrix4f matrix) {
        this.mesh = mesh;
        return this.transform(matrix);
    }

    public MeshRenderer mesh(MeshBuilder mesh, PoseStack matrices) {
        this.mesh = mesh;
        return this.transform(matrices.last().pose());
    }

    public MeshRenderer transform(@Nullable Matrix4f matrix) {
        this.transform = matrix;
        return this;
    }

    public MeshRenderer uniform(String name, GpuBufferSlice slice) {
        if (name != null && slice != null) uniforms.put(name, slice);
        return this;
    }

    public MeshRenderer sampler(String name, GpuTextureView view, GpuSampler sampler) {
        if (name != null && view != null && sampler != null) samplers.put(name, new SamplerBinding(view, sampler));
        return this;
    }

    public void end() {
        finish(null);
    }

    /**
     * Encodes this mesh into an ordered draw stream instead of immediately opening a render pass.
     * The caller owns submission of the list; mesh handles are released by the backend.
     */
    public void endTo(List<RhiDrawCommand> commands) {
        if (commands == null) {
            end();
            return;
        }
        finish(commands);
    }

    private void finish(@Nullable List<RhiDrawCommand> commands) {
        GpuMeshHandle uploaded = null;
        try {
            if (pipeline == null) return;
            if (colorAttachment == null) return;

            if (mesh != null && mesh.isBuilding()) mesh.end();

            final int indexCount;
            if (mesh != null) {
                indexCount = mesh.getIndicesCount();
            } else if (indexBuffer != null) {
                indexCount = (int) (indexBuffer.size() / Integer.BYTES);
            } else {
                indexCount = 0;
            }

            if (indexCount <= 0) return;

            if (mesh != null && FullScreenRenderer.isLegacyFullscreenMesh(mesh) && depthAttachment == null && transform == null) {
                if (commands != null && !commands.isEmpty()) {
                    CombatantRenderSystem.rhi().drawMeshes(commands);
                    commands.clear();
                }
                FullscreenDrawCommand.Builder fullscreen =
                        FullscreenDrawCommand.builder("Combatant MeshRenderer Fullscreen Compatibility")
                                .pipeline(pipeline)
                                .colorAttachment(colorAttachment)
                                .clearColor(clearColorArgb);
                for (var e : uniforms.entrySet()) {
                    fullscreen.uniform(e.getKey(), e.getValue());
                }
                for (var e : samplers.entrySet()) {
                    SamplerBinding b = e.getValue();
                    fullscreen.sampler(e.getKey(), b.view, b.sampler);
                }
                CombatantRenderSystem.rhi().drawFullscreen(fullscreen.build());
                return;
            }

            if (mesh != null) {
                uploaded = CombatantRenderSystem.rhi().dynamicMeshes().upload(mesh);
            } else if (vertexBuffer != null && indexBuffer != null) {
                uploaded = new GpuMeshHandle(vertexBuffer, indexBuffer, 0L, 0, indexCount, com.mojang.blaze3d.IndexType.INT, MeshOwnership.EXTERNAL);
            } else {
                return;
            }

            RhiDrawCommand.Builder command = RhiDrawCommand.builder("Combatant MeshRenderer")
                    .pipeline(pipeline)
                    .colorAttachment(colorAttachment)
                    .depthAttachment(depthAttachment)
                    .clearColor(clearColorArgb)
                    .mesh(uploaded)
                    .transform(transform)
                    .applyWorldCameraY(RenderState.rendering3D);

            for (var e : uniforms.entrySet()) {
                command.uniform(e.getKey(), e.getValue());
            }
            for (var e : samplers.entrySet()) {
                SamplerBinding b = e.getValue();
                command.sampler(e.getKey(), b.view, b.sampler);
            }

            RhiDrawCommand encoded = command.build();
            if (commands != null) {
                commands.add(encoded);
            } else {
                CombatantRenderSystem.rhi().drawMesh(encoded);
            }
            uploaded = null; // backend owns release for this draw
        } finally {
            if (uploaded != null) uploaded.close();
            // reset instance state
            colorAttachment = null;
            depthAttachment = null;
            clearColorArgb = null;
            pipeline = null;
            mesh = null;
            vertexBuffer = null;
            indexBuffer = null;
            transform = null;
            uniforms.clear();
            samplers.clear();
            taken = false;
        }
    }

    private record SamplerBinding(GpuTextureView view, GpuSampler sampler) {
    }
}
