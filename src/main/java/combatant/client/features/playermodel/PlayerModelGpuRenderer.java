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
import combatant.client.render.engine.depth.SeeInvisiblesVisualDepth;
import combatant.client.render.ShaderEspRenderContext;
import combatant.client.util.logging.DebugLog;
import combatant.client.render.engine.pipeline.RigRenderPipelines;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.rig.gpu.RigGpuMeshCache;
import combatant.client.render.engine.rig.shader.RigBonesUniforms;
import combatant.client.render.engine.rig.shader.PlayerRigLightingUniforms;
import combatant.client.features.gui.preview.VisualPreviewRuntime;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.Chams;
import combatant.client.render.iris.IrisRuntime;
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
        private final int tint;
        private final List<PlayerModelCompiler.Surface> surfaces;
        private GpuBufferSlice bones;
        private GpuBufferSlice lighting;

        private Submission(PlayerModels.Snapshot snapshot, Matrix4f matrix, int light, int overlay, int tint) {
            this(snapshot, matrix, light, overlay, tint, snapshot.asset().surfaces());
        }

        private Submission(PlayerModels.Snapshot snapshot, Matrix4f matrix, int light, int overlay,
                           int tint, List<PlayerModelCompiler.Surface> surfaces) {
            this.snapshot = snapshot;
            this.matrix = matrix;
            this.light = light;
            this.overlay = overlay;
            this.tint = tint;
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
            if (lighting == null) lighting = PlayerRigLightingUniforms.upload(
                    light, snapshot.opacity(), snapshot.hit(), tint);
            return lighting;
        }
    }
    /** Mod-agnostic payload; optional Iris classes must not link in vanilla mode. */
    public record IrisDraw(PlayerModels.Snapshot snapshot, Matrix4f model,
                           int light, int overlay, int tint) {}
    private static final List<Submission> PENDING = new ArrayList<>(2);
    // The preview owns its attachments/projection; never mix these submissions
    // with the world frame queue or its camera matrices.
    private static final List<Submission> PREVIEW_PENDING = new ArrayList<>(2);
    private static final List<Submission> HAND_PENDING = new ArrayList<>(2);
    // First-person replacement arms bypass vanilla's SubmitNodeStorage entirely. Chams
    // replays the same GPU geometry into its private hand mask after the visible pass.
    // Capture the actual hand projection/view rather than relying on the world camera.
    private record HandMaskCapture(List<Submission> submissions, Matrix4f view, Matrix4f projection) {}
    private static HandMaskCapture handMaskCapture;
    private static HandMaskCapture previewHandMaskCapture;
    // Independent entity-only depth capture must not enqueue to the visible-world
    // queue, or the replacement is absent from EntityDepthLayer's framebuffer.
    private static final List<Submission> ENTITY_DEPTH_PENDING = new ArrayList<>(2);
    private static RenderTarget entityDepthTarget;
    private static final List<Submission> SHADER_ESP_PENDING = new ArrayList<>(2);
    private static RenderTarget shaderEspMaskTarget;
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
        boolean depthCapture = entityDepthTarget != null;
        boolean shaderEspCapture = shaderEspMaskTarget != null && ShaderEspRenderContext.isActive();
        boolean preview = !depthCapture && !shaderEspCapture && VisualPreviewRuntime.isRenderingSubject()
                && mc.level != null && mc.player != null && mc.gameMode != null;
        if (!collectingWorld && !preview && !depthCapture && !shaderEspCapture) return reject("non-render-context");
        // EntityDepthLayer always owns a private vanilla/RHI attachment, including
        // when the visible scene is rendered by Iris.
        boolean iris = !preview && !depthCapture && !shaderEspCapture && IrisRuntime.isShaderpackRendererActive();
        // The deferred pass runs outside the vanilla entity collector. Without a
        // current camera view/projection its MeshData could inherit the last UI or
        // previous-world transform. Never accept a replacement before these exist.
        if (!preview && !iris && !depthCapture && !shaderEspCapture && !CombatantWorldMatrices.isValid())
            return reject("world-matrices-unavailable");
        if (!shaderEspCapture && (type.isOutline() || outlineColor != 0)) return reject("outline");
        // The auxiliary silhouette must go through the vanilla depth collector, not
        // this deferred visible-color pass.
        if (!shaderEspCapture && SeeInvisiblesVisualDepth.isCapturing()) return reject("visual-depth-capture");
        // Vanilla PlayerModel ALWAYS uses entityTranslucent (even for a fully opaque
        // visible player). Rejecting hasBlending() here silently disabled the GPU path
        // for the normal case. Our compiled replacement surfaces own their alpha policy.
        // Fade, invisibility tint and hurt overlay are per-instance GPU inputs. Rejecting
        // them here used to swap the model to vanilla for one or more frames on damage.
        if (mc == null || mc.level == null || mc.gameRenderer == null
                || mc.gameRenderer.mainRenderTarget() == null) return reject("no-world-target");
        try {
            // Preflight the *complete* asset while the vanilla collector can still be chosen.
            // Uploading at render time would otherwise turn a failed GPU allocation into a
            // missing body for the current frame.
            if (iris) {
                if (!IrisRuntime.preparePlayerRig(snapshot.asset())) return reject("iris-preflight-compute-or-upload");
            } else {
                // The 26.3 RenderPearl frontend requires an exact match between the
                // pass attachment list and the pipeline's declared color targets.
                // Validate BEFORE accepting a replacement into the deferred queue:
                // on a mismatch the vanilla player renderer handles this frame.
                var worldColor = (shaderEspCapture ? shaderEspMaskTarget
                        : depthCapture ? entityDepthTarget
                        : mc.gameRenderer.mainRenderTarget()).getColorTextureView();
                if (worldColor == null) return reject("missing-world-color-view");
                var worldFormat = worldColor.texture().getFormat();
                for (var surface : snapshot.asset().surfaces()) {
                    RenderPipeline pipeline = shaderEspCapture
                            ? (worldFormat == com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM
                                ? RigRenderPipelines.PLAYER_RIG_SHADER_ESP_MASK : null)
                            : RigRenderPipelines.playerPipeline(
                                    !depthCapture && surface.translucent() && !snapshot.faded(), worldFormat);
                    if (pipeline == null) return reject("unsupported-world-color-format:" + worldFormat);
                    var targets = pipeline.getColorTargetStates();
                    if (targets.size() != 1 || targets.get(0) == null
                            || targets.get(0).format() != worldFormat) {
                        return reject("gpu-color-target-contract");
                    }
                }
                var rhi = CombatantRenderSystem.rhi();
                for (var surface : snapshot.asset().surfaces()) {
                    RigGpuMeshCache.global().acquire(rhi, surface.mesh());
                }
            }
            (shaderEspCapture ? SHADER_ESP_PENDING
                    : depthCapture ? ENTITY_DEPTH_PENDING : preview ? PREVIEW_PENDING : PENDING).add(
                    new Submission(snapshot, new Matrix4f(matrices.last().pose()), light, overlay,
                            shaderEspCapture ? outlineColor : tint));
            lastReject = "none";
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (!shaderEspCapture) {
                disable(failure);
            } else {
                DebugLog.warnOnChange("shader-esp.player-model.mask.preflight",
                        failure.getClass().getName() + ":" + failure.getMessage(),
                        "[ShaderESP] PlayerModel mask preflight failed: %s: %s",
                        failure.getClass().getSimpleName(), failure.getMessage());
            }
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
        ENTITY_DEPTH_PENDING.clear();
        entityDepthTarget = null;
        abortShaderEspMaskCapture();
        opaqueDrawn = false;
        collectingWorld = true;
    }

    /** Called around EntityDepthLayer's private vanilla submission. Never reuse the
     * visible-world queue: it can contain poses for an earlier world pass. */
    public static void beginEntityDepthCapture(RenderTarget target) {
        ENTITY_DEPTH_PENDING.clear();
        entityDepthTarget = target;
    }

    /** The private depth pass always writes the solid imported silhouette. It
     * must not apply NoRender/SeeInvisibles dither or per-instance alpha. */
    public static void finishEntityDepthCapture(RenderTarget target,
                                                Matrix4f view, Matrix4f projection) {
        entityDepthTarget = null;
        try {
            if (!ENTITY_DEPTH_PENDING.isEmpty())
                drawGpu(ENTITY_DEPTH_PENDING, target.getColorTextureView(),
                        target.getDepthTextureView(), view, projection, false, true);
        } finally {
            ENTITY_DEPTH_PENDING.clear();
        }
    }

    public static void abortEntityDepthCapture() {
        entityDepthTarget = null;
        ENTITY_DEPTH_PENDING.clear();
    }

    public static void beginShaderEspMaskCapture(RenderTarget target) {
        SHADER_ESP_PENDING.clear();
        shaderEspMaskTarget = target;
    }

    public static boolean finishShaderEspMaskCapture(RenderTarget target,
                                                     Matrix4f view, Matrix4f projection) {
        shaderEspMaskTarget = null;
        try {
            return drawShaderEspMask(SHADER_ESP_PENDING, target, view, projection);
        } finally {
            SHADER_ESP_PENDING.clear();
        }
    }

    public static void abortShaderEspMaskCapture() {
        shaderEspMaskTarget = null;
        SHADER_ESP_PENDING.clear();
    }

    /** Clear a preview at the start of its isolated subject frame. */
    public static void beginPreview() {
        PREVIEW_PENDING.clear();
        previewHandMaskCapture = null;
    }

    public static boolean submitHand(PlayerModels.Snapshot snapshot,
                                     List<PlayerModelCompiler.Surface> surfaces,
                                     PoseStack matrices, int light) {
        if (snapshot == null || matrices == null || surfaces == null || surfaces.isEmpty()) return false;
        if (retryAfterNanos != 0 && System.nanoTime() - retryAfterNanos < 0)
            return false;
        Minecraft mc = Minecraft.getInstance();
        if (VisualPreviewRuntime.isRenderingSubject()
                && (mc == null || mc.level == null || mc.player == null || mc.gameMode == null)) return false;
        List<Submission> destination = VisualPreviewRuntime.isRenderingSubject()
                ? PREVIEW_PENDING : HAND_PENDING;
        destination.add(new Submission(snapshot, new Matrix4f(matrices.last().pose()),
                light, OverlayTexture.NO_OVERLAY, -1, surfaces));
        return true;
    }

    public static void beginHands() {
        HAND_PENDING.clear();
        handMaskCapture = null;
    }

    private static boolean shouldCaptureHandMask() {
        Chams chams = Modules.get(Chams.class);
        return chams != null && (chams.isEnabled() || VisualPreviewRuntime.isPreviewingModule("chams"));
    }

    public static void renderHands(com.mojang.renderpearl.api.textures.GpuTextureView color,
                                   com.mojang.renderpearl.api.textures.GpuTextureView depth,
                                   Matrix4f modelView, Matrix4f projection) {
        if (HAND_PENDING.isEmpty()) return;
        try {
            if (shouldCaptureHandMask() && color != null && depth != null
                    && modelView != null && projection != null) {
                handMaskCapture = new HandMaskCapture(List.copyOf(HAND_PENDING),
                        new Matrix4f(modelView), new Matrix4f(projection));
            }
            drawGpu(HAND_PENDING, color, depth, modelView, projection, false);
            drawGpu(HAND_PENDING, color, depth, modelView, projection, true);
        } finally {
            HAND_PENDING.clear();
        }
    }

    /** Add imported first-person arms to Chams' mask without touching the visible scene.
     * The vanilla hand dispatcher cannot see these direct RHI submissions. */
    public static void renderHandMask(com.mojang.renderpearl.api.textures.GpuTextureView color,
                                      com.mojang.renderpearl.api.textures.GpuTextureView depth) {
        boolean preview = VisualPreviewRuntime.isRenderingSubject();
        HandMaskCapture capture = preview ? previewHandMaskCapture : handMaskCapture;
        if (preview) previewHandMaskCapture = null;
        else handMaskCapture = null;
        if (capture == null || color == null || depth == null) return;
        drawGpu(capture.submissions(), color, depth, capture.view(), capture.projection(), false);
        drawGpu(capture.submissions(), color, depth, capture.view(), capture.projection(), true);
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
            if (shouldCaptureHandMask()) {
                previewHandMaskCapture = new HandMaskCapture(List.copyOf(PREVIEW_PENDING),
                        new Matrix4f(), new Matrix4f(projection));
            }
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
        drawGpu(draws, color, depth, view, projection, translucent, false);
    }

    private static void drawGpu(List<Submission> draws,
                                com.mojang.renderpearl.api.textures.GpuTextureView color,
                                com.mojang.renderpearl.api.textures.GpuTextureView depth,
                                Matrix4f view, Matrix4f projection, boolean translucent,
                                boolean depthOnly) {
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
                GpuBufferSlice bones = null;
                GpuBufferSlice lighting = null;
                Matrix4f modelView = new Matrix4f(view).mul(draw.matrix());
                for (var surface : draw.surfaces()) {
                    // Fading geometry must keep depth writes. Translucent blending
                    // draws every interior armor shell and makes a dense model opaque.
                    // The cutout shader instead uses one screen-space coverage mask
                    // shared by all fragments at a given pixel.
                    if ((!depthOnly && surface.translucent() && !snapshot.faded()) != translucent
                            || (!depthOnly && snapshot.opacity() <= 0.001f)) continue;
                    // Hidden instances and passes with no matching surfaces must not
                    // allocate per-actor uniforms or schedule empty GPU work.
                    if (bones == null) {
                        bones = draw.bones();
                        lighting = depthOnly ? PlayerRigLightingUniforms.upload(
                                draw.light(), 1.0f, 0.0f, draw.tint) : draw.lighting();
                    }
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
                    rhi.drawMeshes(commands);
                } finally {
                    MeshRenderer.setProjection(previousProjection);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            disable(failure);
        }
    }

    private static boolean drawShaderEspMask(List<Submission> draws, RenderTarget target,
                                             Matrix4f view, Matrix4f projection) {
        if (draws.isEmpty() || target == null || target.getColorTextureView() == null
                || target.getDepthTextureView() == null || view == null || projection == null) return false;
        try {
            var rhi = CombatantRenderSystem.rhi();
            var pipeline = RigRenderPipelines.PLAYER_RIG_SHADER_ESP_MASK;
            var commands = new ArrayList<RhiDrawCommand>();
            for (Submission draw : draws) {
                AbstractTexture texture = Minecraft.getInstance().getTextureManager()
                        .getTexture(draw.snapshot().asset().texture());
                if (texture == null || texture.getTextureView() == null || texture.getSampler() == null)
                    throw new IllegalStateException("Player model texture lost GPU residency");
                GpuBufferSlice bones = draw.bones();
                GpuBufferSlice maskColor = PlayerRigLightingUniforms.upload(draw.light(), 1.0f, 0.0f, draw.tint);
                Matrix4f modelView = new Matrix4f(view).mul(draw.matrix());
                for (var surface : draw.surfaces()) {
                    var mesh = RigGpuMeshCache.global().acquire(rhi, surface.mesh());
                    commands.add(RhiDrawCommand.builder("PlayerModel Shader ESP mask")
                            .pipeline(pipeline)
                            .colorAttachment(target.getColorTextureView())
                            .depthAttachment(target.getDepthTextureView())
                            .mesh(mesh.handle())
                            .transform(modelView)
                            .uniform(RigBonesUniforms.BLOCK_NAME, bones)
                            .uniform(PlayerRigLightingUniforms.BLOCK_NAME, maskColor)
                            .sampler("u_Texture", texture.getTextureView(), texture.getSampler())
                            .build());
                }
            }
            if (commands.isEmpty()) return false;
            Matrix4f previousProjection = MeshRenderer.projection();
            try {
                MeshRenderer.setProjection(projection);
                rhi.drawMeshes(commands);
            } finally {
                MeshRenderer.setProjection(previousProjection);
            }
            return true;
        } catch (RuntimeException | LinkageError failure) {
            DebugLog.warnOnChange("shader-esp.player-model.mask.draw",
                    failure.getClass().getName() + ":" + failure.getMessage(),
                    "[ShaderESP] PlayerModel mask draw failed: %s: %s",
                    failure.getClass().getSimpleName(), failure.getMessage());
            return false;
        }
    }

    /** Called by Iris inside its real G-buffer/forward boundaries, before shaderpack composite. */
    public static void renderIris(boolean translucent) {
        if (PENDING.isEmpty() || IrisRuntime.isRenderingShadowPass()) return;
        var draws = new ArrayList<IrisDraw>(PENDING.size());
        for (Submission submission : PENDING) {
            draws.add(new IrisDraw(
                    submission.snapshot(), submission.matrix(), submission.light(),
                    submission.overlay(), submission.tint));
        }
        IrisRuntime.drawPlayerRigs(draws, translucent);
        if (translucent) PENDING.clear();
    }

    public static void reset() {
        PENDING.clear();
        PREVIEW_PENDING.clear();
        HAND_PENDING.clear();
        previewHandMaskCapture = null;
        handMaskCapture = null;
        abortEntityDepthCapture();
        abortShaderEspMaskCapture();
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
        previewHandMaskCapture = null;
        handMaskCapture = null;
        abortEntityDepthCapture();

    }
}
