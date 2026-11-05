/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.depth;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.SeeInvisibles;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.mixins.accessors.LevelRendererAccessor;
import combatant.client.render.ViewObstructionFadeState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.renderer.FeatureTargetRenderer;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.render.iris.IrisSceneDepth;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Late Combatant world effects are rendered after the vanilla/Iris translucent composite.
 * A translucent entity correctly does not write the scene depth, so those later effects
 * would otherwise appear ON TOP of an invisible player's translucent skin.
 *
 * Build a separate depth attachment for late world visuals: start with the real scene
 * depth and rasterize only SeeInvisibles players as opaque depth silhouettes. Neither
 * the scene depth nor the entity's real OIT/alpha-blended color pass is changed.
 * This is deliberately NOT an always-on player depth prepass in the world framebuffer.
 */
public final class SeeInvisiblesVisualDepth {
    private static final SubmitNodeStorage SUBMITS = new SubmitNodeStorage();
    private static final String TARGET_NAME = "seeinvisibles_visual_depth";
    private static final String PRE_TARGET_NAME = "seeinvisibles_pretranslucent_visual_depth";

    private static List<EntityRenderState> snapshot = List.of();
    private static TextureTarget visualTarget;
    private static TextureTarget preVisualTarget;
    private static RenderBuffers buffers;
    private static FeatureRenderDispatcher dispatcher;
    private static ProjectionMatrixBuffer projectionBuffer;
    private static boolean capturing;
    private static boolean active;
    private static boolean preActive;

    private SeeInvisiblesVisualDepth() { }

    public static void snapshot(List<EntityRenderState> entities) {
        active = false;
        preActive = false;
        SeeInvisibles setting = Modules.get(SeeInvisibles.class);
        if (setting == null || !setting.isEnabled() || setting.getInvisiblePlayerAlpha01() <= 0.0f
                || entities == null || entities.isEmpty()) {
            snapshot = List.of();
            return;
        }
        List<EntityRenderState> selected = new ArrayList<>();
        for (EntityRenderState state : entities) {
            // The auxiliary world-visual depth must also include ordinary vanilla
            // player geometry: PRE_DEPTH can precede the vanilla entity submission
            // on some render paths. This does not affect the entity's color pass.
            if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living
                    && living.entityType == net.minecraft.world.entity.EntityTypes.PLAYER
                    && (state instanceof ViewObstructionFadeState fade
                        && fade.combatant$isSeeInvisibleFadeActive()
                        || !living.isInvisible)) {
                selected.add(state);
            }
        }
        snapshot = selected.isEmpty() ? List.of() : List.copyOf(selected);
    }

    /** Late overlay depth must match the color attachment's sample count. */
    public static boolean needsSingleSampleTarget() {
        return !snapshot.isEmpty();
    }

    /** True only while submitting the auxiliary depth-only silhouettes, never the normal world. */
    public static boolean isCapturing() {
        return capturing;
    }

    public static void reset() {
        active = false;
        preActive = false;
        capturing = false;
        snapshot = List.of();
    }

    /** Called immediately before the late 3D world passes on BOTH vanilla and Iris. */
    public static void begin(RenderTarget colorTarget) {
        active = false;
        preActive = false;
        if (snapshot.isEmpty() || colorTarget == null || colorTarget.getDepthTextureView() == null) return;
        if (colorTarget.width <= 0 || colorTarget.height <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.levelRenderer == null) return;
        GpuTextureView sourceDepth = IrisRuntime.isShaderpackRendererActive()
                ? IrisSceneDepth.mainDepthView() : colorTarget.getDepthTextureView();
        GpuTextureView sourcePreDepth = preTranslucentSourceFor(colorTarget);

        // Never mix sample counts. The late world pass uses a single-sample target when
        // SeeInvisibles silhouettes are needed; the early snapshot may have its own MSAA.
        if (samples(colorTarget.getColorTextureView()) != 1) return;
        if (!canCopy(sourceDepth, colorTarget)) sourceDepth = null;
        if (!canCopy(sourcePreDepth, colorTarget)) sourcePreDepth = null;
        if (sourceDepth == null && sourcePreDepth == null) return;

        GpuBufferSlice oldProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType oldProjectionType = RenderSystem.getProjectionType();
        var modelView = RenderSystem.getModelViewStack();
        Matrix4f oldMeshProjection = MeshRenderer.projection();
        LevelRenderState worldState = ((LevelRendererAccessor) mc.levelRenderer).combatant$getWorldRenderState();
        if (worldState == null || worldState.cameraRenderState == null) return;
        try {
            visualTarget = copySceneDepth(TARGET_NAME, sourceDepth, colorTarget);
            preVisualTarget = copySceneDepth(PRE_TARGET_NAME, sourcePreDepth, colorTarget);
            if (visualTarget == null && preVisualTarget == null) return;

            Matrix4f projection = CombatantWorldMatrices.renderProjectionMatrix();
            if (projection == null) return;
            if (projectionBuffer == null) projectionBuffer = new ProjectionMatrixBuffer(TARGET_NAME);
            if (dispatcher == null) {
                if (mc.getModelManager() == null || mc.getAtlasManager() == null || mc.font == null) return;
                buffers = new RenderBuffers(1);
                dispatcher = new FeatureRenderDispatcher(buffers, mc.getModelManager(),
                        mc.getAtlasManager(), mc.font, mc.gameRenderer.gameRenderState());
            }
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
            MeshRenderer.setProjection(projection);
            modelView.pushMatrix();
            try {
                modelView.identity();
                Matrix4f view = CombatantWorldMatrices.positionMatrix();
                if (view != null) modelView.mul(view);
                else if (mc.gameRenderer.mainCamera() != null)
                    modelView.mul(new Matrix4f().rotation(mc.gameRenderer.mainCamera().rotation().conjugate(new Quaternionf())));

                SUBMITS.getSubmitsPerOrder().clear();
                // Depth-only submission uses the same extracted animations, equipment and
                // selected player model as the visible pass. No duplicate state extraction.
                capturing = true;
                try {
                    List<EntityRenderState> saved = new ArrayList<>(worldState.entityRenderStates);
                    worldState.entityRenderStates.clear();
                    worldState.entityRenderStates.addAll(snapshot);
                    try {
                        ((LevelRendererAccessor) mc.levelRenderer).combatant$invokeSubmitEntities(
                                new PoseStack(), worldState, SUBMITS);
                    } finally {
                        worldState.entityRenderStates.clear();
                        worldState.entityRenderStates.addAll(saved);
                    }
                } finally {
                    capturing = false;
                }
                // Prepare the extracted entity meshes ONCE and render them to the two
                // differently seeded depth buffers. No second entity-state extraction.
                if (visualTarget != null && preVisualTarget != null) {
                    FeatureTargetRenderer.renderDepthPair(TARGET_NAME, PRE_TARGET_NAME,
                            dispatcher, SUBMITS,
                            visualTarget.getColorTextureView(), visualTarget.getDepthTextureView(),
                            preVisualTarget.getColorTextureView(), preVisualTarget.getDepthTextureView());
                    active = true;
                    preActive = true;
                } else if (visualTarget != null) {
                    FeatureTargetRenderer.render(TARGET_NAME, dispatcher, SUBMITS,
                            visualTarget.getColorTextureView(), visualTarget.getDepthTextureView(),
                            FeatureTargetRenderer.Selection.DEPTH);
                    active = true;
                } else {
                    FeatureTargetRenderer.render(PRE_TARGET_NAME, dispatcher, SUBMITS,
                            preVisualTarget.getColorTextureView(), preVisualTarget.getDepthTextureView(),
                            FeatureTargetRenderer.Selection.DEPTH);
                    preActive = true;
                }
            } finally {
                SUBMITS.getSubmitsPerOrder().clear();
                buffers.endFrame();
                modelView.popMatrix();
            }
        } catch (RuntimeException failure) {
            DebugLog.warnOnChange("seeinvisibles.visual-depth.failed",
                    failure.getClass().getName() + ":" + failure.getMessage(),
                    "[SeeInvisibles] late visual depth failed: %s: %s",
                    failure.getClass().getSimpleName(), failure.getMessage());
            active = false;
            preActive = false;
        } finally {
            capturing = false;
            MeshRenderer.setProjection(oldMeshProjection);
            if (oldProjection != null && oldProjectionType != null)
                RenderSystem.setProjectionMatrix(oldProjection, oldProjectionType);
        }
    }

    /**
     * Iris depthtex1 is the actual opaque (pre-water) scene depth; the Minecraft
     * main target depth copied at beginTranslucents is not always authoritative.
     * Copy it to our own render-attachment-capable target before drawing silhouettes.
     */
    private static GpuTextureView preTranslucentSourceFor(RenderTarget colorTarget) {
        if (IrisRuntime.isShaderpackRendererActive()) {
            GpuTextureView irisPre = IrisSceneDepth.preTranslucentDepthView();
            if (canCopy(irisPre, colorTarget)) return irisPre;
        }
        return PreTranslucentDepth.getDepthViewFor(colorTarget.getColorTextureView());
    }

    private static boolean canCopy(GpuTextureView source, RenderTarget colorTarget) {
        return source != null && source.texture() != null
                && samples(source) == 1
                && source.texture().getWidth(0) == colorTarget.width
                && source.texture().getHeight(0) == colorTarget.height;
    }

    private static TextureTarget copySceneDepth(String name, GpuTextureView source, RenderTarget colorTarget) {
        if (source == null) return null;
        TextureTarget destination = CombatantRenderSystem.resources().persistentFramebuffer(
                name, colorTarget.width, colorTarget.height, true, name);
        if (destination == null || destination.getDepthTextureView() == null
                || destination.getColorTextureView() == null || samples(destination.getDepthTextureView()) != 1) return null;
        if (destination.getDepthTexture().getFormat() != source.texture().getFormat()) {
            DebugLog.warnOnChange("seeinvisibles.visual-depth.format." + name,
                    source.texture().getFormat() + "/" + destination.getDepthTexture().getFormat(),
                    "[SeeInvisibles] source/destination depth formats differ for %s", name);
            return null;
        }
        try {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                    source.texture(), destination.getDepthTexture(),
                    0, 0, 0, 0, 0, colorTarget.width, colorTarget.height);
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(
                    destination.getColorTexture(), new Vector4f(0.0f));
            return destination;
        } catch (RuntimeException failure) {
            // Keep the other depth policy usable if just one source is unsupported.
            DebugLog.warnOnChange("seeinvisibles.visual-depth.copy." + name,
                    failure.getClass().getName() + ":" + failure.getMessage(),
                    "[SeeInvisibles] failed to copy %s depth: %s: %s", name,
                    failure.getClass().getSimpleName(), failure.getMessage());
            return null;
        }
    }

    /** PRE_DEPTH: water-independent opaque snapshot PLUS the invisible player's depth. */
    public static GpuTextureView preDepthViewFor(GpuTextureView colorView) {
        return preActive ? matchingDepth(preVisualTarget, colorView) : null;
    }

    private static GpuTextureView matchingDepth(TextureTarget target, GpuTextureView colorView) {
        if (colorView == null || target == null) return null;
        GpuTextureView depth = target.getDepthTextureView();
        return depth != null && samples(depth) == samples(colorView) ? depth : null;
    }

    public static GpuTextureView depthViewFor(GpuTextureView colorView) {
        return active ? matchingDepth(visualTarget, colorView) : null;
    }

    public static void end() {
        active = false;
        preActive = false;
    }

    private static int samples(GpuTextureView view) {
        if (view == null || view.texture() == null) return 1;
        return view.texture() instanceof IMsaaTexture texture && texture.combatant$isMsaa()
                ? texture.combatant$getSamples() : 1;
    }
}
