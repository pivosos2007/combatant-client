/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.depth;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.mixins.accessors.LevelRendererAccessor;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
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

/** Entity geometry depth without terrain depth. */
public final class EntityDepthLayer {
    private static final SubmitNodeStorage COMMANDS = new SubmitNodeStorage();

    private static ProjectionMatrixBuffer projectionBuffer;
    private static FeatureRenderDispatcher dispatcher;
    private static RenderBuffers renderBuffers;
    private static RenderTarget target;
    private static boolean valid;
    private static List<EntityRenderState> capturedEntities = List.of();

    private EntityDepthLayer() {
    }

    public static GpuTextureView depthView() {
        return valid && target != null ? target.getDepthTextureView() : null;
    }

    public static void reset() {
        valid = false;
        capturedEntities = List.of();
    }

    public static void snapshotEntities(List<EntityRenderState> entities) {
        capturedEntities = entities == null || entities.isEmpty() ? List.of() : new ArrayList<>(entities);
    }

    public static boolean capture(LevelRenderer levelRenderer, LevelRenderState state, int width, int height) {
        valid = false;
        if (levelRenderer == null || state == null || state.cameraRenderState == null
                || width <= 0 || height <= 0) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameRenderer == null) {
            return false;
        }

        target = CombatantRenderSystem.resources().persistentFramebuffer(
                "entity-only-depth",
                width,
                height,
                true,
                "EntityDepthLayer"
        );
        if (target == null || target.getColorTexture() == null || target.getDepthTexture() == null) {
            return false;
        }

        boolean hasEntities = !capturedEntities.isEmpty();
        FeatureRenderDispatcher renderDispatcher = hasEntities ? dispatcher(minecraft) : null;
        if (hasEntities && renderDispatcher == null) {
            return false;
        }

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(target.getColorTexture(), new Vector4f(0.0f));
        encoder.clearDepthTexture(target.getDepthTexture(), 0.0);
        if (!hasEntities) {
            valid = true;
            return true;
        }

        GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        Matrix4f previousMeshProjection = MeshRenderer.projection();
        Matrix4f previousWorldProjection = new Matrix4f(RenderState.worldProjection);
        boolean previousRendering3D = RenderState.rendering3D;

        Matrix4f projection = CombatantWorldMatrices.renderProjectionMatrix();
        if (projection == null) {
            projection = new Matrix4f(RenderState.worldProjection);
        }
        if (projectionBuffer == null) {
            projectionBuffer = new ProjectionMatrixBuffer("combatant-entity-depth-projection");
        }

        RenderSystem.outputColorTextureOverride = target.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
        MeshRenderer.setProjection(projection);
        RenderState.worldProjection.set(projection);
        RenderState.rendering3D = true;

        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        Matrix4f position = CombatantWorldMatrices.positionMatrix();
        if (position != null) {
            modelView.mul(position);
        } else if (minecraft.gameRenderer.mainCamera() != null) {
            modelView.mul(new Matrix4f().rotation(
                    minecraft.gameRenderer.mainCamera().rotation().conjugate(new Quaternionf())));
        }

        COMMANDS.getSubmitsPerOrder().clear();
        try {
            LevelRendererAccessor accessor = (LevelRendererAccessor) levelRenderer;
            state.entityRenderStates.addAll(capturedEntities);
            try {
                accessor.combatant$invokeSubmitEntities(new PoseStack(), state, COMMANDS);
            } finally {
                state.entityRenderStates.clear();
                capturedEntities = List.of();
            }
            try (FeatureRenderDispatcher.PreparedFrame prepared = renderDispatcher.prepareFrame(COMMANDS)) {
                prepared.executeSolid();
                prepared.executeTranslucent();
                prepared.executeTranslucentAfterTerrain();
            } finally {
                renderBuffers.endFrame();
            }
            valid = true;
            return true;
        } catch (Throwable failure) {
            DebugLog.warnOnChange("entity-depth.capture.failure",
                    failure.getClass().getName() + ":" + failure.getMessage(),
                    "[EntityDepthLayer] capture failed: %s: %s",
                    failure.getClass().getSimpleName(), failure.getMessage());
            return false;
        } finally {
            COMMANDS.getSubmitsPerOrder().clear();
            modelView.popMatrix();
            MeshRenderer.setProjection(previousMeshProjection);
            RenderState.worldProjection.set(previousWorldProjection);
            RenderState.rendering3D = previousRendering3D;
            if (previousProjection != null && previousProjectionType != null) {
                RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            }
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
        }
    }

    private static FeatureRenderDispatcher dispatcher(Minecraft minecraft) {
        if (dispatcher != null) {
            return dispatcher;
        }
        if (minecraft.getModelManager() == null || minecraft.getAtlasManager() == null || minecraft.font == null) {
            return null;
        }
        renderBuffers = new RenderBuffers(1);
        dispatcher = new FeatureRenderDispatcher(
                renderBuffers,
                minecraft.getModelManager(),
                minecraft.getAtlasManager(),
                minecraft.font,
                minecraft.gameRenderer.gameRenderState()
        );
        return dispatcher;
    }
}
