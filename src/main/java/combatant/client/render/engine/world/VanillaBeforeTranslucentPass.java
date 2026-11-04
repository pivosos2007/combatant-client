/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.addon.AddonRenderPipelineManager;
import combatant.client.api.v0.render.CombatantRenderStage;
import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.Modules;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.module.modules.visuals.SeeInvisibles;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.render.engine.depth.PreTranslucentDepth;
import combatant.client.render.engine.pipeline.WorldRenderPipelines;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineRegistry;
import combatant.client.render.engine.world.WorldDrawCommand;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class VanillaBeforeTranslucentPass {
    private static Renderer3D beforeColorRenderer;
    private static Renderer3D beforeDepthRenderer;
    private static Renderer3D endColorRenderer;
    private static Renderer3D endDepthRenderer;
    private static boolean rendered;

    private VanillaBeforeTranslucentPass() { }

    public static void reset() {
        rendered = false;
    }

    public static boolean rendered() {
        return rendered;
    }

    /** Only ordinary main-framebuffer depth-tested primitives can precede OIT. */
    private static boolean drawBeforeTransparency(WorldDrawCommand command) {
        return command.depthMode() == Renderer3D.DepthMode.MAIN
                && RenderPipelineRegistry.global().require(command.pipeline())
                        .depthPolicy().needsDepthAttachment();
    }

    public static void flushDeferredBefore(PoseStack matrices, RenderTarget target) {
        if (!rendered) return;
        beforeColorRenderer.render(matrices, target);
        beforeDepthRenderer.render(matrices, target);
    }

    public static void flushDeferredEnd(PoseStack matrices, RenderTarget target) {
        if (!rendered) return;
        endColorRenderer.render(matrices, target);
        endDepthRenderer.render(matrices, target);
    }

    /** Must only be invoked after the opaque RenderPass has closed, before classic/OIT translucency. */
    public static void render(RenderTarget target) {
        if (rendered || target == null || target.getColorTextureView() == null) return;
        if (IrisRuntime.isShaderpackRendererActive()
                && !combatant.client.render.iris.IrisEarlyWorldBridge.isRendering()) return;
        SeeInvisibles seeInvisibles = Modules.get(SeeInvisibles.class);
        if (seeInvisibles == null || !seeInvisibles.isEnabled()
                || seeInvisibles.getInvisiblePlayerAlpha01() <= 0.0f
                || seeInvisibles.getInvisiblePlayerAlpha01() >= 1.0f) return;
        if (!combatant.client.render.iris.IrisEarlyWorldBridge.isRendering()
                && !PreTranslucentDepth.hasCapturedThisFrame()) return;

        Matrix4f view = CombatantWorldMatrices.positionMatrix();
        Matrix4f projection = CombatantWorldMatrices.renderProjectionMatrix();
        Vec3 cameraPos = CombatantWorldMatrices.cameraPosition();
        Minecraft mc = Minecraft.getInstance();
        if (view == null || projection == null || cameraPos == null || mc == null
                || mc.level == null || mc.gameRenderer == null) return;

        if (beforeColorRenderer == null) {
            beforeColorRenderer = new Renderer3D(WorldRenderPipelines.WORLD_COLORED_LINES,
                    WorldRenderPipelines.WORLD_COLORED);
            beforeDepthRenderer = new Renderer3D(WorldRenderPipelines.WORLD_COLORED_LINES_DEPTH,
                    WorldRenderPipelines.WORLD_COLORED_DEPTH);
            endColorRenderer = new Renderer3D(WorldRenderPipelines.WORLD_COLORED_LINES,
                    WorldRenderPipelines.WORLD_COLORED);
            endDepthRenderer = new Renderer3D(WorldRenderPipelines.WORLD_COLORED_LINES_DEPTH,
                    WorldRenderPipelines.WORLD_COLORED_DEPTH);
        }

        boolean oldRendering3D = RenderState.rendering3D;
        Vec3 oldCameraPos = RenderState.cameraPos;
        Vec3 oldCameraLook = RenderState.cameraLook;
        Frustum oldFrustum = RenderState.frustum;
        Matrix4f oldWorldProjection = new Matrix4f(RenderState.worldProjection);
        Matrix4f oldMeshProjection = MeshRenderer.projection();
        Quaternionf oldRotation = new Quaternionf(RenderState.cameraRotation);
        float oldYaw = RenderState.cameraYaw;
        float oldPitch = RenderState.cameraPitch;
        var oldCameraSubmersion = RenderState.cameraSubmersion;
        boolean oldWorldTranslucent = RenderState.worldTranslucent;
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.identity();
            modelView.mul(view);
            RenderState.rendering3D = true;
            RenderState.cameraPos = cameraPos;
            RenderState.cameraRotation.set(mc.gameRenderer.mainCamera().rotation());
            RenderState.cameraYaw = mc.gameRenderer.mainCamera().yRot();
            RenderState.cameraPitch = mc.gameRenderer.mainCamera().xRot();
            RenderState.cameraSubmersion = mc.gameRenderer.mainCamera().getFluidInCamera();
            RenderState.worldTranslucent = RenderState.cameraSubmersion == net.minecraft.world.level.material.FogType.WATER
                    || RenderState.cameraSubmersion == net.minecraft.world.level.material.FogType.LAVA
                    || RenderState.cameraSubmersion == net.minecraft.world.level.material.FogType.POWDER_SNOW;
            RenderState.cameraLook = Vec3.directionFromRotation(
                    RenderState.cameraPitch, RenderState.cameraYaw).normalize();
            RenderState.worldProjection.set(projection);
            RenderState.frustum = new Frustum(view, projection);
            RenderState.frustum.prepare(cameraPos.x, cameraPos.y, cameraPos.z);
            MeshRenderer.setProjection(projection);

            // WorldCommandBuffer uses camera-relative vertices; the view matrix is applied
            // by MeshData, not by duplicating the camera transform in the PoseStack.
            PoseStack matrices = new PoseStack();
            try (RenderPhaseScope ignored = CombatantRenderSystem.phase(RenderPhase.WORLD_BEFORE_TRANSLUCENT)) {
                beforeColorRenderer.begin();
                beforeDepthRenderer.begin();
                endColorRenderer.begin();
                endDepthRenderer.begin();
                float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
                ModuleManager.renderWorldEngine(WorldPhase.BEFORE_TRANSLUCENT,
                        beforeColorRenderer, beforeDepthRenderer, tickDelta);
                AddonRenderPipelineManager.render3D(CombatantRenderStage.WORLD_BEFORE_TRANSLUCENT,
                        WorldPhase.BEFORE_TRANSLUCENT, beforeColorRenderer, beforeDepthRenderer, matrices, tickDelta);
                // Unlike the previous patch, END_MAIN 3D primitives share the same
                // depth policy and must also be eligible for the early world pass.
                try (RenderPhaseScope endScope = CombatantRenderSystem.phase(RenderPhase.WORLD_AFTER_TRANSLUCENT)) {
                    ModuleManager.renderWorldEngine(WorldPhase.END_MAIN,
                            endColorRenderer, endDepthRenderer, tickDelta);
                    AddonRenderPipelineManager.render3D(CombatantRenderStage.WORLD_END_MAIN,
                            WorldPhase.END_MAIN, endColorRenderer, endDepthRenderer, matrices, tickDelta);
                }
                // PRE_DEPTH must stay above water. Selecting by depth policy here
                // fixes the blending regression without hard-coding BlockHighlight.
                beforeColorRenderer.renderMatching(matrices, target, VanillaBeforeTranslucentPass::drawBeforeTransparency);
                beforeDepthRenderer.renderMatching(matrices, target, VanillaBeforeTranslucentPass::drawBeforeTransparency);
                endColorRenderer.renderMatching(matrices, target, VanillaBeforeTranslucentPass::drawBeforeTransparency);
                endDepthRenderer.renderMatching(matrices, target, VanillaBeforeTranslucentPass::drawBeforeTransparency);
                rendered = true;
                // Pre-translucent color is still composited beneath the player's
                // transparent skin. PRE_DEPTH commands have deliberately been deferred.
                if (combatant.client.render.iris.IrisEarlyWorldBridge.isRendering()) {
                    EarlyShockwavePass.render(combatant.client.render.iris.IrisEarlyWorldBridge.sceneTarget(), tickDelta);
                } else {
                    EarlyShockwavePass.render(target, tickDelta);
                }
            }
        } catch (RuntimeException failure) {
            // A compatibility failure must not abort vanilla's own transparency pass.
            // The late world hook remains the fallback for this frame.
            DebugLog.warnOnChange("seeinvisibles.vanilla.early-pass.failed",
                    failure.getClass().getName() + ":" + failure.getMessage(),
                    "[SeeInvisibles] before-translucent world pass failed: %s: %s",
                    failure.getClass().getSimpleName(), failure.getMessage());
        } finally {
            modelView.popMatrix();
            MeshRenderer.setProjection(oldMeshProjection);
            RenderState.rendering3D = oldRendering3D;
            RenderState.cameraPos = oldCameraPos;
            RenderState.cameraLook = oldCameraLook;
            RenderState.cameraRotation.set(oldRotation);
            RenderState.cameraYaw = oldYaw;
            RenderState.cameraPitch = oldPitch;
            RenderState.cameraSubmersion = oldCameraSubmersion;
            RenderState.worldTranslucent = oldWorldTranslucent;
            RenderState.frustum = oldFrustum;
            RenderState.worldProjection.set(oldWorldProjection);
        }
    }
}
