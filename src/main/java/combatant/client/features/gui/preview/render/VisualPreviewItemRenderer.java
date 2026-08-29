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
import com.mojang.math.Axis;
import combatant.client.features.gui.preview.VisualPreviewRuntime;
import combatant.client.features.gui.preview.VisualPreviewSceneContext;
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
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.Objects;

/**
 * Isolated 3D preview scene around vanilla item model resolution and feature submission.
 * It deliberately owns buffers/projection/state, while ItemStackRenderState still owns all
 * vanilla model, component, trim, foil and feature layers.
 */
public final class VisualPreviewItemRenderer {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final float FOV_DEGREES = 30.0f;
    private static final float CAMERA_DISTANCE = 3.2f;

    private static RenderBuffers renderBuffers;
    private static FeatureRenderDispatcher dispatcher;
    private static ProjectionMatrixBuffer projectionBuffer;
    private static final Projection projection = new Projection();
    private static TrackingItemStackRenderState resolvedState;
    private static ItemStack resolvedStack = ItemStack.EMPTY;

    private VisualPreviewItemRenderer() {
    }

    public static void render(ItemStack stack, VisualPreviewSceneContext context) {
        Minecraft mc = context.minecraft();
        if (mc == null || mc.gameRenderer == null || mc.level == null || stack == null || stack.isEmpty()) return;
        ensureRenderer(mc);
        TrackingItemStackRenderState state = resolve(mc, stack);
        if (state == null || state.isEmpty()) return;

        PreviewRenderState previous = PreviewRenderState.capture();
        try (VisualPreviewRuntime.Scope ignored = VisualPreviewRuntime.enterSubjectRender()) {
            enterPreviewState(mc, state);

            SubmitNodeStorage storage = new SubmitNodeStorage();
            PoseStack pose = createSubjectPose(mc, context);
            state.submit(pose, storage, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            dispatcher.renderAllFeatures(storage);
            renderBuffers.endFrame();
        } catch (RuntimeException failure) {
            resetRenderer();
            throw failure;
        } finally {
            previous.restore();
        }
    }

    private static TrackingItemStackRenderState resolve(Minecraft mc, ItemStack stack) {
        if (resolvedState != null && !resolvedState.isAnimated() && sameStack(resolvedStack, stack)) return resolvedState;
        TrackingItemStackRenderState next = new TrackingItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(
                next,
                stack,
                ItemDisplayContext.GUI,
                mc.level,
                mc.player,
                0
        );
        resolvedStack = stack.copy();
        resolvedState = next;
        return next;
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        return left != null && right != null
                && left.getItem() == right.getItem()
                && left.getCount() == right.getCount()
                && Objects.equals(left.getComponents(), right.getComponents());
    }

    private static PoseStack createSubjectPose(Minecraft mc, VisualPreviewSceneContext context) {
        float framebufferW = Math.max(1.0f, mc.getWindow().getWidth());
        float framebufferH = Math.max(1.0f, mc.getWindow().getHeight());
        float aspect = framebufferW / framebufferH;

        float centerX = context.subjectX() + context.subjectWidth() * 0.5f + context.screen().panX();
        float centerY = context.subjectY() + context.subjectHeight() * 0.5f + context.screen().panY();
        float ndcX = centerX / Math.max(1.0f, context.width()) * 2.0f - 1.0f;
        float ndcY = 1.0f - centerY / Math.max(1.0f, context.height()) * 2.0f;
        float distance = Mth.clamp(
                CAMERA_DISTANCE - context.screen().cameraDolly() + context.screen().cameraZ(),
                0.45f,
                12.0f
        );
        float halfHeight = (float) Math.tan(Math.toRadians(FOV_DEGREES * 0.5f)) * distance;
        float halfWidth = halfHeight * aspect;

        PoseStack pose = new PoseStack();
        pose.translate(
                ndcX * halfWidth - context.screen().cameraX(),
                ndcY * halfHeight - context.screen().cameraY(),
                -distance
        );
        pose.mulPose(Axis.XP.rotationDegrees(-context.screen().cameraPitch()));
        pose.mulPose(Axis.YP.rotationDegrees(-context.screen().cameraYaw()));
        pose.mulPose(Axis.XP.rotationDegrees(context.screen().pitch()));
        pose.mulPose(Axis.YP.rotationDegrees(context.screen().yaw()));
        float modelScale = 2.25f * context.screen().zoom();
        pose.scale(modelScale, modelScale, modelScale);
        return pose;
    }

    private static void enterPreviewState(Minecraft mc, TrackingItemStackRenderState state) {
        RenderState.rendering3D = true;
        RenderSystem.outputColorTextureOverride = mc.gameRenderer.mainRenderTarget().getColorTextureView();
        RenderSystem.outputDepthTextureOverride = mc.gameRenderer.mainRenderTarget().getDepthTextureView();
        RenderSystem.getDevice().createCommandEncoder()
                .clearDepthTexture(mc.gameRenderer.mainRenderTarget().getDepthTexture(), 0.0D);

        projection.setupPerspective(
                0.05f,
                100.0f,
                FOV_DEGREES,
                Math.max(1.0f, mc.getWindow().getWidth()),
                Math.max(1.0f, mc.getWindow().getHeight())
        );
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
        RenderSystem.getModelViewStack().identity();

        if (mc.gameRenderer instanceof GameRendererAccessor accessor) {
            FogRenderer fog = accessor.combatant$getFogRenderer();
            if (fog != null) RenderSystem.setShaderFog(fog.getBuffer(FogRenderer.FogMode.NONE));
        }
        mc.gameRenderer.lighting().setupFor(
                state.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT
        );
    }

    private static void ensureRenderer(Minecraft mc) {
        if (dispatcher != null && renderBuffers != null && projectionBuffer != null) return;
        renderBuffers = new RenderBuffers(1);
        dispatcher = new FeatureRenderDispatcher(
                renderBuffers,
                mc.getModelManager(),
                mc.getAtlasManager(),
                mc.font,
                mc.gameRenderer.gameRenderState()
        );
        projectionBuffer = new ProjectionMatrixBuffer("Combatant visual preview projection");
    }

    public static void resetRenderer() {
        resolvedState = null;
        resolvedStack = ItemStack.EMPTY;
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
