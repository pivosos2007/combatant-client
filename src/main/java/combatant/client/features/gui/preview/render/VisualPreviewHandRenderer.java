/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.preview.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.features.gui.preview.VisualPreviewRuntime;
import combatant.client.features.gui.preview.VisualPreviewSceneContext;
import combatant.client.features.gui.preview.VisualPreviewInteractionProfile;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.Chams;
import combatant.client.mixins.accessors.GameRendererAccessor;
import combatant.client.render.engine.RenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

/**
 * Isolated first-person renderer. Submission still goes through vanilla ItemInHandRenderer, so arm
 * skins, item models and ViewModel hooks are identical to gameplay, while buffers and post passes
 * remain owned by the preview scene.
 */
public enum VisualPreviewHandRenderer {
    ;

    private static final float DEFAULT_FOV = 70.0f;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static RenderBuffers renderBuffers;
    private static FeatureRenderDispatcher dispatcher;
    private static ProjectionMatrixBuffer projectionBuffer;
    private static final Projection projection = new Projection();

    public static void render(VisualPreviewSceneContext context) {
        Minecraft mc = context.minecraft();
        if (mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null || mc.gameMode == null) return;
        ensureRenderer(mc);

        PreviewRenderState previous = PreviewRenderState.capture();
        try (VisualPreviewRuntime.Scope ignored = VisualPreviewRuntime.enterSubjectRender()) {
            enterPreviewState(mc, context);

            SubmitNodeStorage subject = new SubmitNodeStorage();
            PoseStack subjectPose = new PoseStack();
            mc.gameRenderer.itemInHandRenderer.submitHandsWithItems(
                    context.tickDelta(),
                    subjectPose,
                    subject,
                    mc.player,
                    mc.getEntityRenderDispatcher().getPackedLightCoords(mc.player, context.tickDelta())
            );

            // An empty hotbar must not produce an empty preview. The fallback is submitted into the
            // same subject phase, so Chams sees exactly the same geometry as the base renderer.
            if (mc.player.getMainHandItem().isEmpty() && mc.player.getOffhandItem().isEmpty()) {
                submitFallbackItem(mc, subject);
            }

            Chams chams = Modules.get(Chams.class);
            SubmitNodeStorage maskSubject = chams == null ? null : chams.snapshotPreparedHandScene(subject);
            dispatcher.renderAllFeatures(subject);
            renderBuffers.endFrame();

            if (chams != null && maskSubject != null && chams.renderPreparedHandScene(maskSubject)) {
                chams.compositePreparedHandScene(context.tickDelta());
            }
        } catch (RuntimeException failure) {
            resetRenderer();
            throw failure;
        } finally {
            previous.restore();
        }
    }

    private static void enterPreviewState(Minecraft mc,
                                          VisualPreviewSceneContext context) {
        RenderState.rendering3D = true;
        RenderSystem.outputColorTextureOverride = mc.gameRenderer.mainRenderTarget().getColorTextureView();
        RenderSystem.outputDepthTextureOverride = mc.gameRenderer.mainRenderTarget().getDepthTextureView();
        RenderSystem.getDevice().createCommandEncoder()
                .clearDepthTexture(mc.gameRenderer.mainRenderTarget().getDepthTexture(), 0.0D);

        projection.setupPerspective(
                0.05f,
                100.0f,
                DEFAULT_FOV,
                Math.max(1.0f, mc.getWindow().getWidth()),
                Math.max(1.0f, mc.getWindow().getHeight())
        );
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE);

        float centerX = context.subjectX() + context.subjectWidth() * 0.5f;
        float subjectOffsetX = centerX / Math.max(1.0f, context.width()) * 2.0f - 1.0f;
        VisualPreviewInteractionProfile interactions = context.screen().provider().interactionProfile();
        boolean fixedCamera = interactions.equals(VisualPreviewInteractionProfile.FIXED);
        float moveX = subjectOffsetX * 0.52f;
        float moveY = 0.0f;
        float subjectYaw = fixedCamera ? 0.0f : context.screen().yaw();
        float subjectPitch = fixedCamera ? 0.0f : context.screen().pitch();
        float orbitYaw = fixedCamera ? 0.0f : context.screen().cameraYaw();
        float orbitPitch = fixedCamera ? 0.0f : context.screen().cameraPitch();
        float dolly = fixedCamera ? 0.0f : context.screen().cameraDolly();
        float zoom = fixedCamera ? 1.0f : context.screen().zoom();

        boolean mainHand = !mc.player.getMainHandItem().isEmpty();
        boolean offHand = !mc.player.getOffhandItem().isEmpty();
        float pivotX = mainHand == offHand ? 0.0f : (mainHand ? 0.42f : -0.42f);
        float pivotY = -0.34f;
        float pivotZ = -0.88f;

        RenderSystem.getModelViewStack()
                .identity()
                .translate(
                        moveX - context.screen().cameraX(),
                        moveY - context.screen().cameraY(),
                        dolly - context.screen().cameraZ()
                )
                .translate(pivotX, pivotY, pivotZ)
                .rotateX((float) Math.toRadians(-orbitPitch))
                .rotateY((float) Math.toRadians(-orbitYaw))
                .rotateX((float) Math.toRadians(-subjectPitch))
                .rotateY((float) Math.toRadians(-subjectYaw))
                .scale(zoom)
                .translate(-pivotX, -pivotY, -pivotZ);

        if (mc.gameRenderer instanceof GameRendererAccessor accessor) {
            FogRenderer fog = accessor.combatant$getFogRenderer();
            if (fog != null) RenderSystem.setShaderFog(fog.getBuffer(FogRenderer.FogMode.NONE));
        }
        mc.gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);
    }

    private static void submitFallbackItem(Minecraft mc, SubmitNodeStorage subject) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        TrackingItemStackRenderState state = new TrackingItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(
                state,
                stack,
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                mc.level,
                mc.player,
                0
        );
        if (state.isEmpty()) return;

        PoseStack pose = new PoseStack();
        pose.translate(0.42f, -0.34f, -1.05f);
        pose.scale(0.72f, 0.72f, 0.72f);
        state.submit(pose, subject, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
    }

    private static void ensureRenderer(Minecraft mc) {
        if (renderBuffers != null && dispatcher != null && projectionBuffer != null) return;
        renderBuffers = new RenderBuffers(1);
        dispatcher = new FeatureRenderDispatcher(
                renderBuffers,
                mc.getModelManager(),
                mc.getAtlasManager(),
                mc.font,
                mc.gameRenderer.gameRenderState()
        );
        projectionBuffer = new ProjectionMatrixBuffer("Combatant hand preview projection");
    }

    public static void resetRenderer() {
        if (dispatcher != null) dispatcher.close();
        if (renderBuffers != null) renderBuffers.close();
        if (projectionBuffer != null) projectionBuffer.close();
        dispatcher = null;
        renderBuffers = null;
        projectionBuffer = null;
    }

    private record PreviewRenderState(
            GpuTextureView outputColor,
            GpuTextureView outputDepth,
            GpuBufferSlice projection,
            ProjectionType projectionType,
            Matrix4f modelView,
            GpuBufferSlice shaderLights,
            GpuBufferSlice shaderFog,
            ScissorState scissor,
            boolean rendering3D
    ) {
        static PreviewRenderState capture() {
            return new PreviewRenderState(
                    RenderSystem.outputColorTextureOverride,
                    RenderSystem.outputDepthTextureOverride,
                    RenderSystem.getProjectionMatrixBuffer(),
                    RenderSystem.getProjectionType(),
                    RenderSystem.getModelViewMatrixCopy(),
                    RenderSystem.getShaderLights(),
                    RenderSystem.getShaderFog(),
                    new ScissorState(RenderSystem.getScissorStateForRenderTypeDraws()),
                    RenderState.rendering3D
            );
        }

        void restore() {
            RenderSystem.getModelViewStack().set(modelView);
            if (projection != null && projectionType != null) {
                RenderSystem.setProjectionMatrix(projection, projectionType);
            }
            RenderSystem.outputColorTextureOverride = outputColor;
            RenderSystem.outputDepthTextureOverride = outputDepth;
            RenderSystem.setShaderLights(shaderLights);
            RenderSystem.setShaderFog(shaderFog);
            RenderSystem.getScissorStateForRenderTypeDraws().setFrom(scissor);
            RenderState.rendering3D = rendering3D;
        }
    }
}
