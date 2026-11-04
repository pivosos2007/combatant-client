/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.playermodel;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.ViewObstructionFadeContext;
import combatant.client.render.engine.pipeline.RigRenderPipelines;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.rig.gpu.RigGpuMeshCache;
import combatant.client.render.engine.rig.shader.RigBonesUniforms;
import combatant.client.render.engine.rig.shader.PlayerRigLightingUniforms;
import combatant.client.features.gui.preview.VisualPreviewRuntime;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * World entity GPU skinning, with the same indexed rig vertex ABI used by the general 3D engine.
 * Vanilla/RenderPearl pipelines perform depth testing, while immutable geometry lives in VRAM.
 * With an active Iris shaderpack, the dedicated compute adapter skins vertices into Iris' own
 * ENTITY layout and draws through mapped ENTITIES programs at the proper Iris pass boundaries.
 * Draws are submitted on GPU for both world and isolated model preview scenes.
 */
public final class PlayerModelGpuRenderer {
    private static final class Submission {
        private final PlayerModels.Snapshot snapshot;
        private final Matrix4f matrix;
        private final int light;
        private final int overlay;
        private final List<PlayerModelCompiler.Surface> surfaces;
        // Both alpha passes draw in the SAME frame. Share the uploaded GPU bone palette
        // and lighting payload, rather than writing the same UBO twice per instance.
        private GpuBufferSlice bones;
        private GpuBufferSlice lighting;

        private Submission(PlayerModels.Snapshot snapshot, Matrix4f matrix, int light, int overlay) {
            this(snapshot, matrix, light, overlay, snapshot.asset().surfaces());
        }

        private Submission(PlayerModels.Snapshot snapshot, Matrix4f matrix, int light, int overlay,
                           List<PlayerModelCompiler.Surface> surfaces) {
            this.snapshot = snapshot;
            this.matrix = matrix;
            this.light = light;
            this.overlay = overlay;
            this.surfaces = surfaces;
        }

        private PlayerModels.Snapshot snapshot() { return snapshot; }
        private Matrix4f matrix() { return matrix; }
        private int light() { return light; }
        private int overlay() { return overlay; }
        private List<PlayerModelCompiler.Surface> surfaces() { return surfaces; }
        private GpuBufferSlice bones() {
            if (bones == null) bones = RigBonesUniforms.upload(snapshot.rig().rig());
            return bones;
        }
        private GpuBufferSlice lighting() {
            if (lighting == null) lighting = PlayerRigLightingUniforms.upload(light);
            return lighting;
        }
    }
    /** Mod-agnostic payload; optional Iris classes must not link in vanilla mode. */
    public record IrisDraw(PlayerModels.Snapshot snapshot, Matrix4f model, int light, int overlay) {}
    private static final List<Submission> PENDING = new ArrayList<>(2);
    // The preview owns its attachments/projection; never mix these submissions
    // with the world frame queue or its camera matrices.
    private static final List<Submission> PREVIEW_PENDING = new ArrayList<>(2);
    private static final List<Submission> HAND_PENDING = new ArrayList<>(2);
    private static boolean opaqueDrawn;
    // A screen can be open while the LEVEL renderer still draws the world.
    // Only the actual LevelRenderer.render lifetime determines whether a
    // submit belongs to the deferred world GPU pass.
    private static boolean collectingWorld;
    // A runtime GPU failure must not poison every subsequent world frame. Retry after
    // a short cooldown (resource reload/target resize can make the same path valid again).
    private static final long ERROR_RETRY_NS = 5_000_000_000L;
    private static long retryAfterNanos;
    private static String lastReject = "none";

    public static String lastRejectReason() { return lastReject; }

    private static boolean reject(String why) { lastReject = why; return false; }

    private PlayerModelGpuRenderer() {}

    public static boolean trySubmit(PlayerModels.Snapshot snapshot, PoseStack matrices,
                                    RenderType type, int light, int overlay, int tint, int outlineColor) {
        if (retryAfterNanos != 0 && System.nanoTime() - retryAfterNanos < 0)
            return reject("gpu-retry-cooldown");
        if (snapshot == null || matrices == null || type == null) return reject("missing-input");
        // Opening chat / ClickGUI is irrelevant to a LevelRenderer submission.
        // Dedicated preview scenes use their own GPU queue and perspective.
        Minecraft mc = Minecraft.getInstance();
        boolean preview = VisualPreviewRuntime.isRenderingSubject();
        if (!collectingWorld && !preview) return reject("non-render-context");
        boolean iris = !preview && IrisRuntime.isShaderpackRendererActive();
        // The deferred pass runs outside the vanilla entity collector. Without a
        // current camera view/projection its MeshData could inherit the last UI or
        // previous-world transform. Never accept a replacement before these exist.
        if (!preview && !iris && !CombatantWorldMatrices.isValid()) return reject("world-matrices-unavailable");
        if (type.isOutline() || outlineColor != 0) return reject("outline");
        // Vanilla PlayerModel ALWAYS uses entityTranslucent (even for a fully opaque
        // visible player). Rejecting hasBlending() here silently disabled the GPU path
        // for the normal case. Our compiled replacement surfaces own their alpha policy.
        // Preserve the ordered collector only when the entity really needs fade blending.
        if (ViewObstructionFadeContext.isActive() && ViewObstructionFadeContext.alpha() < 0.99f)
            return reject("view-obstruction-fade");
        if (tint != -1) return reject("nonwhite-tint");
        if (overlay != OverlayTexture.NO_OVERLAY) return reject("overlay");
        if (mc == null || mc.level == null || mc.gameRenderer == null
                || mc.gameRenderer.mainRenderTarget() == null) return reject("no-world-target");
        try {
            // Preflight the *complete* asset while the vanilla collector can still be chosen.
            // Uploading at render time would otherwise turn a failed GPU allocation into a
            // missing body for the current frame.
            if (iris) {
                PlayerModelRenderDiagnostics.targetFormat("Iris native shaderpack MRT");
                if (!IrisRuntime.preparePlayerRig(snapshot.asset())) return reject("iris-preflight-compute-or-upload");
            } else {
                // The 26.3 RenderPearl frontend requires an exact match between the
                // pass attachment list and the pipeline's declared color targets.
                // Validate BEFORE accepting a replacement into the deferred queue:
                // on a mismatch the vanilla player renderer handles this frame.
                var worldColor = mc.gameRenderer.mainRenderTarget().getColorTextureView();
                if (worldColor == null) return reject("missing-world-color-view");
                var worldFormat = worldColor.texture().getFormat();
                PlayerModelRenderDiagnostics.targetFormat(worldFormat.toString());
                for (var surface : snapshot.asset().surfaces()) {
                    RenderPipeline pipeline = RigRenderPipelines.playerPipeline(
                            surface.translucent(), worldFormat);
                    if (pipeline == null) return reject("unsupported-world-color-format:" + worldFormat);
                    var targets = pipeline.getColorTargetStates();
                    if (targets.size() != 1 || targets.get(0) == null
                            || targets.get(0).format() != worldFormat) {
                        DebugLog.warnOnce("playermodel.gpu.color-target-contract",
                                "[PlayerModel] RHI pipeline %s declares %d color targets, "
                                        + "but the world pass has one %s attachment; using vanilla player renderer",
                                pipeline.getLocation(), targets.size(), worldFormat);
                        return reject("gpu-color-target-contract");
                    }
                }
                var rhi = CombatantRenderSystem.rhi();
                for (var surface : snapshot.asset().surfaces()) {
                    RigGpuMeshCache.global().acquire(rhi, surface.mesh());
                }
            }
            (preview ? PREVIEW_PENDING : PENDING).add(
                    new Submission(snapshot, new Matrix4f(matrices.last().pose()), light, overlay));
            lastReject = "none";
            PlayerModelRenderDiagnostics.queued(snapshot.asset());
            return true;
        } catch (RuntimeException | LinkageError failure) {
            disable(failure);
            return reject("gpu-exception:" + failure.getClass().getSimpleName());
        }
    }

    /**
     * Start of LevelRenderer.render, before entities submit their replacement geometry.
     * A frame skipped by the frame graph must not replay its queued poses next frame.
     * Do not reset retryAfterNanos here: failures retain their short retry cooldown.
     */
    public static void beginWorldFrame() {
        PENDING.clear();
        opaqueDrawn = false;
        collectingWorld = true;
    }

    /** Clear a preview at the start of its isolated subject frame. */
    public static void beginPreview() { PREVIEW_PENDING.clear(); }

    public static boolean submitHand(PlayerModels.Snapshot snapshot,
                                     List<PlayerModelCompiler.Surface> surfaces,
                                     PoseStack matrices, int light) {
        if (snapshot == null || matrices == null || surfaces == null || surfaces.isEmpty()) return false;
        // If a device rejected the GPU pipeline, the first-person hook must
        // keep Vanilla's arm draw, rather than silently dropping the hand.
        if (retryAfterNanos != 0 && System.nanoTime() - retryAfterNanos < 0)
            return false;
        List<Submission> destination = VisualPreviewRuntime.isRenderingSubject()
                ? PREVIEW_PENDING : HAND_PENDING;
        destination.add(new Submission(snapshot, new Matrix4f(matrices.last().pose()),
                light, OverlayTexture.NO_OVERLAY, surfaces));
        PlayerModelRenderDiagnostics.queued(snapshot.asset());
        return true;
    }

    public static void beginHands() { HAND_PENDING.clear(); }

    public static void renderHands(com.mojang.renderpearl.api.textures.GpuTextureView color,
                                   com.mojang.renderpearl.api.textures.GpuTextureView depth,
                                   Matrix4f modelView, Matrix4f projection) {
        if (HAND_PENDING.isEmpty()) return;
        try {
            drawGpu(HAND_PENDING, color, depth, modelView, projection, false);
            drawGpu(HAND_PENDING, color, depth, modelView, projection, true);
        } finally {
            HAND_PENDING.clear();
        }
    }

    public static void endWorldFrame() {
        collectingWorld = false;
        // Never replay stranded world geometry in an unrelated UI pass.
        PENDING.clear();
        opaqueDrawn = false;
    }

    /**
     * Flush in the LevelRenderer opaque/translucency boundary, on the real world
     * framebuffer before the depth snapshot and water. Never call this from the
     * later GameRenderer world overlay hook.
     */
    public static void renderBeforeTranslucency(RenderTarget target) {
        if (opaqueDrawn || PENDING.isEmpty() || IrisRuntime.isShaderpackRendererActive()) return;
        opaqueDrawn = true;
        drawGpu(PENDING, target, CombatantWorldMatrices.positionMatrix(),
                CombatantWorldMatrices.renderProjectionMatrix(), false);
    }

    /** After vanilla translucent terrain/entities have finished, draw transparent rig surfaces. */
    public static void renderAfterTranslucency(RenderTarget target) {
        if (PENDING.isEmpty() || IrisRuntime.isShaderpackRendererActive()) return;
        try {
            if (opaqueDrawn) drawGpu(PENDING, target,
                    CombatantWorldMatrices.positionMatrix(),
                    CombatantWorldMatrices.renderProjectionMatrix(), true);
        } finally {
            PENDING.clear();
            opaqueDrawn = false;
        }
    }

    /** Render preview geometry on the preview's own depth attachment and projection. */
    public static void renderPreview(com.mojang.renderpearl.api.textures.GpuTextureView color,
                                     com.mojang.renderpearl.api.textures.GpuTextureView depth,
                                     Matrix4f projection) {
        if (PREVIEW_PENDING.isEmpty()) return;
        try {
            if (color == null || depth == null || projection == null) return;
            // Do not create a per-frame framebuffer: reuse the preview's existing
            // color and private depth attachments with the normal RHI pipeline.
            drawGpu(PREVIEW_PENDING, color, depth, new Matrix4f(), projection, false);
            drawGpu(PREVIEW_PENDING, color, depth, new Matrix4f(), projection, true);
        } finally {
            PREVIEW_PENDING.clear();
        }
    }

    private static void drawGpu(List<Submission> draws, RenderTarget target,
                                Matrix4f view, Matrix4f projection, boolean translucent) {
        if (target == null) return;
        drawGpu(draws, target.getColorTextureView(), target.getDepthTextureView(),
                view, projection, translucent);
    }

    private static void drawGpu(List<Submission> draws,
                                com.mojang.renderpearl.api.textures.GpuTextureView color,
                                com.mojang.renderpearl.api.textures.GpuTextureView depth,
                                Matrix4f view, Matrix4f projection, boolean translucent) {
        if (draws.isEmpty() || color == null || depth == null || view == null || projection == null) return;
        try {
            var rhi = CombatantRenderSystem.rhi();
            var commands = new ArrayList<RhiDrawCommand>(draws.size() * 2);
            var targetFormat = color.texture().getFormat();
            var game = Minecraft.getInstance().gameRenderer;
            var lightmap = game.levelLightmap();
            if (lightmap == null) throw new IllegalStateException("Player model lightmap unavailable");
            var lightSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            var shaderLights = RenderSystem.getShaderLights();
            if (shaderLights == null) throw new IllegalStateException("Player model lighting uniform unavailable");
            RenderPipeline pipeline = RigRenderPipelines.playerPipeline(translucent, targetFormat);
            if (pipeline == null) throw new IllegalStateException("Unsupported player color format: " + targetFormat);
            for (Submission draw : draws) {
                PlayerModels.Snapshot snapshot = draw.snapshot();
                AbstractTexture texture = Minecraft.getInstance().getTextureManager()
                        .getTexture(snapshot.asset().texture());
                if (texture == null || texture.getTextureView() == null || texture.getSampler() == null)
                    throw new IllegalStateException("Player model texture lost GPU residency");
                GpuBufferSlice bones = draw.bones();
                GpuBufferSlice lighting = draw.lighting();
                Matrix4f modelView = new Matrix4f(view).mul(draw.matrix());
                for (var surface : draw.surfaces()) {
                    if (surface.translucent() != translucent) continue;
                    var gpuMesh = RigGpuMeshCache.global().acquire(rhi, surface.mesh());
                    commands.add(RhiDrawCommand.builder("PlayerModel GPU skinning")
                            .pipeline(pipeline)
                            .colorAttachment(color)
                            .depthAttachment(depth)
                            .mesh(gpuMesh.handle())
                            .transform(modelView)
                            .uniform(RigBonesUniforms.BLOCK_NAME, bones)
                            .uniform(PlayerRigLightingUniforms.BLOCK_NAME, lighting)
                            .uniform("Lighting", shaderLights)
                            .sampler("u_Texture", texture.getTextureView(), texture.getSampler())
                            .sampler("u_Lightmap", lightmap, lightSampler)
                            .build());
                }
            }
            if (!commands.isEmpty()) {
                Matrix4f previousProjection = MeshRenderer.projection();
                try {
                    MeshRenderer.setProjection(projection);
                    long start = PlayerModelRenderDiagnostics.enabled() ? System.nanoTime() : 0L;
                    rhi.drawMeshes(commands);
                    if (PlayerModelRenderDiagnostics.enabled())
                        PlayerModelRenderDiagnostics.glIssued(commands.size(), System.nanoTime() - start);
                } finally {
                    MeshRenderer.setProjection(previousProjection);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            disable(failure);
        }
    }

    /** Called by Iris inside its real G-buffer/forward boundaries, before shaderpack composite. */
    public static void renderIris(boolean translucent) {
        if (PENDING.isEmpty() || IrisRuntime.isRenderingShadowPass()) return;
        var draws = new ArrayList<IrisDraw>(PENDING.size());
        for (Submission submission : PENDING) {
            draws.add(new IrisDraw(
                    submission.snapshot(), submission.matrix(), submission.light(), submission.overlay()));
        }
        IrisRuntime.drawPlayerRigs(draws, translucent);
        if (translucent) PENDING.clear();
    }

    public static void reset() {
        PENDING.clear();
        PREVIEW_PENDING.clear();
        HAND_PENDING.clear();
        opaqueDrawn = false;
        collectingWorld = false;
        retryAfterNanos = 0;
        lastReject = "none";
        IrisRuntime.invalidatePlayerRigs();
    }

    private static void disable(Throwable reason) {
        retryAfterNanos = System.nanoTime() + ERROR_RETRY_NS;
        PENDING.clear();
        PREVIEW_PENDING.clear();
        HAND_PENDING.clear();
        // Include the concrete reason in the one-shot key: a different failure after a
        // retry must be visible instead of being swallowed by the first warning.
        String description = reason.getClass().getSimpleName() + ": " + reason.getMessage();
        DebugLog.warnOnce("playermodel.gpu.fallback." + description,
                "[PlayerModel] GPU rig draw failed, retrying after 5s: %s",
                description);
    }
}
