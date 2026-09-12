/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.postprocess;

import combatant.client.config.MainConfig;
import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.uniform.impl.MenuBackgroundUniforms;
import combatant.client.render.engine.uniform.impl.MenuTextureTransitionUniforms;
import combatant.client.render.engine.uniform.impl.UIBatchUniforms;
import combatant.client.runtime.RuntimeGate;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import java.time.LocalTime;
import java.util.Locale;

/**
 * Shared main-menu background renderer.
 *
 * <p>PNG keeps the current time-of-day image/crossfade path. Aurora and Waves use the
 * MenuBackground UBO and the current Combatant theme. Calls made during GUI extraction are deferred
 * until immediately before vanilla GuiRenderer.render(), so the background is underneath all GUI
 * strata and can also replace Minecraft's Panorama without letting the panorama render at all.</p>
 */
public enum MenuBackgroundRenderer {
    ;

    public static final Identifier MORNING_TEXTURE = Identifier.fromNamespaceAndPath(
            "combatant", "textures/mainmenu/morning-mountains.png");
    public static final Identifier DEFAULT_TEXTURE = Identifier.fromNamespaceAndPath(
            "combatant", "textures/mainmenu/forest-mountains.png");
    public static final Identifier DUSK_TEXTURE = Identifier.fromNamespaceAndPath(
            "combatant", "textures/mainmenu/mountains-dusk.png");
    public static final Identifier NIGHT_TEXTURE = Identifier.fromNamespaceAndPath(
            "combatant", "textures/mainmenu/night-mountains.png");

    private static final float TRANSITION_SECONDS = 2.20f;
    private static final long SHADER_TIME_ORIGIN_NANOS = System.nanoTime();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static ProjectionMatrixBuffer projection;

    private static Identifier previousTimedTexture;
    private static Identifier currentTimedTexture;
    private static TimeBand currentTimeBand;
    private static float transitionProgress = 1.0f;

    private static boolean deferredPending;
    private static Identifier deferredPreviousTexture;
    private static Identifier deferredTexture;
    private static float deferredBlend = 1.0f;
    private static RenderPipeline deferredShaderPipeline;

    /** Renders the background selected in MainConfig: png, aurora or waves. */
    public static void renderConfigured(Minecraft mc) {
        if (!RuntimeGate.canRunRender() || mc == null) return;
        switch (configuredMode()) {
            case AURORA -> renderShader(mc, CombatantRenderPipelines.MAIN_MENU_AURORA_BACKGROUND);
            case WAVES -> renderShader(mc, CombatantRenderPipelines.MAIN_MENU_WAVES_BACKGROUND);
            case PNG -> renderDefaultTexture(mc);
        }
    }

    /** Renders the system-time PNG background and crossfades whenever the active time band changes. */
    public static void renderDefaultTexture(Minecraft mc) {
        if (!RuntimeGate.canRunRender() || mc == null) return;
        updateTimedTexture();
        Identifier previous = previousTimedTexture != null ? previousTimedTexture : DEFAULT_TEXTURE;
        Identifier current = currentTimedTexture != null ? currentTimedTexture : DEFAULT_TEXTURE;
        float blend = AnimationUtility.easeInOutCubic(transitionProgress);
        renderTexturePair(mc, previous, current, blend);
    }

    /** Queues one explicit cover-fit image before the GUI glass source is captured. */
    public static void renderTexture(Minecraft mc, Identifier texture) {
        if (!RuntimeGate.canRunRender() || mc == null || texture == null) return;
        renderTexturePair(mc, texture, texture, 1.0f);
    }

    private static BackgroundMode configuredMode() {
        String configured = MainConfig.get().getMenuBackgroundMode();
        if (configured == null) return BackgroundMode.PNG;
        return switch (configured.trim().toLowerCase(Locale.ROOT)) {
            case "aurora" -> BackgroundMode.AURORA;
            case "waves" -> BackgroundMode.WAVES;
            default -> BackgroundMode.PNG;
        };
    }

    private static void updateTimedTexture() {
        TimeBand targetBand = timeBand(LocalTime.now());
        Identifier targetTexture = textureFor(targetBand);

        if (currentTimedTexture == null || currentTimeBand == null) {
            currentTimeBand = targetBand;
            currentTimedTexture = targetTexture;
            previousTimedTexture = targetTexture;
            transitionProgress = 1.0f;
            return;
        }

        if (targetBand != currentTimeBand) {
            previousTimedTexture = currentTimedTexture;
            currentTimedTexture = targetTexture;
            currentTimeBand = targetBand;
            transitionProgress = 0.0f;
        }

        if (transitionProgress < 1.0f) {
            float dt = Math.max(0.0f, AnimationUtility.deltaTime(AnimationUtility.Mode.MILLIS));
            transitionProgress = AnimationUtility.clamp01(
                    transitionProgress + dt / Math.max(0.001f, TRANSITION_SECONDS)
            );
            if (transitionProgress >= 1.0f) previousTimedTexture = currentTimedTexture;
        }
    }

    private static TimeBand timeBand(LocalTime time) {
        int hour = time != null ? time.getHour() : 12;
        if (hour >= 5 && hour < 11) return TimeBand.MORNING;
        if (hour >= 11 && hour < 17) return TimeBand.DAY;
        if (hour >= 17 && hour < 21) return TimeBand.DUSK;
        return TimeBand.NIGHT;
    }

    private static Identifier textureFor(TimeBand band) {
        return switch (band) {
            case MORNING -> MORNING_TEXTURE;
            case DAY -> DEFAULT_TEXTURE;
            case DUSK -> DUSK_TEXTURE;
            case NIGHT -> NIGHT_TEXTURE;
        };
    }

    private static void renderTexturePair(Minecraft mc, Identifier previous, Identifier current, float blend) {
        if (Renderer2D.isDeferredExtractRecording()) {
            deferredPending = true;
            deferredShaderPipeline = null;
            deferredPreviousTexture = previous;
            deferredTexture = current;
            deferredBlend = AnimationUtility.clamp01(blend);
            return;
        }
        renderTextureNow(mc, previous, current, blend);
    }

    private static void renderShader(Minecraft mc, RenderPipeline pipeline) {
        if (pipeline == null) return;
        if (Renderer2D.isDeferredExtractRecording()) {
            deferredPending = true;
            deferredShaderPipeline = pipeline;
            deferredPreviousTexture = null;
            deferredTexture = null;
            deferredBlend = 1.0f;
            return;
        }
        renderShaderNow(mc, pipeline);
    }

    public static void drainDeferred(Minecraft mc) {
        if (!RuntimeGate.canRunRender()) {
            clearDeferred();
            return;
        }
        if (!deferredPending) return;

        RenderPipeline shaderPipeline = deferredShaderPipeline;
        Identifier previous = deferredPreviousTexture;
        Identifier current = deferredTexture;
        float blend = deferredBlend;

        deferredPending = false;
        deferredShaderPipeline = null;
        deferredPreviousTexture = null;
        deferredTexture = null;
        deferredBlend = 1.0f;

        if (shaderPipeline != null) {
            renderShaderNow(mc, shaderPipeline);
        } else if (previous != null && current != null) {
            renderTextureNow(mc, previous, current, blend);
        }
    }

    /** Discard stale extraction commands without touching GPU state. */
    public static void clearDeferred() {
        deferredPending = false;
        deferredShaderPipeline = null;
        deferredPreviousTexture = null;
        deferredTexture = null;
        deferredBlend = 1.0f;
    }

    private static void renderTextureNow(Minecraft mc, Identifier previousId, Identifier currentId, float blend) {
        if (!RuntimeGate.canRunRender() || mc == null || previousId == null || currentId == null) return;
        RenderTarget framebuffer = mc.gameRenderer.mainRenderTarget();
        if (framebuffer == null) return;
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        if (width <= 0 || height <= 0) return;

        AbstractTexture previousTexture = mc.getTextureManager().getTexture(previousId);
        AbstractTexture currentTexture = mc.getTextureManager().getTexture(currentId);
        if (previousTexture == null || currentTexture == null
                || previousTexture.getTextureView() == null || previousTexture.getSampler() == null
                || currentTexture.getTextureView() == null || currentTexture.getSampler() == null) return;

        FullScreenRenderer.ensureInit();
        UIBatchUniforms.update(width, height);
        MenuTextureTransitionUniforms.update(AnimationUtility.clamp01(blend));

        GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        Matrix4f previousMeshProjection = MeshRenderer.projection();
        boolean previousRendering3D = RenderState.rendering3D;
        boolean pushedModelView = false;
        var modelView = RenderSystem.getModelViewStack();
        try {
            modelView.pushMatrix();
            pushedModelView = true;
            modelView.identity();
            if (projection == null) projection = new ProjectionMatrixBuffer("combatant-menu-bg-projection");
            Matrix4f identityProjection = IDENTITY.identity();
            RenderSystem.setProjectionMatrix(projection.getBuffer(identityProjection), ProjectionType.PERSPECTIVE);
            MeshRenderer.setProjection(identityProjection);
            RenderState.rendering3D = false;

            FullScreenRenderer.begin("Combatant Main Menu PNG Background")
                    .attachment(framebuffer)
                    .pipeline(CombatantRenderPipelines.MAIN_MENU_TEXTURE_BACKGROUND)
                    .uniform("UIBatch", UIBatchUniforms.get())
                    .uniform("MenuTextureTransition", MenuTextureTransitionUniforms.get())
                    .sampler("u_PreviousTexture", previousTexture.getTextureView(), previousTexture.getSampler())
                    .sampler("u_Texture", currentTexture.getTextureView(), currentTexture.getSampler())
                    .end();
        } finally {
            if (pushedModelView) modelView.popMatrix();
            MeshRenderer.setProjection(previousMeshProjection);
            RenderState.rendering3D = previousRendering3D;
            restoreProjection(previousProjection, previousProjectionType);
        }
    }

    private static void renderShaderNow(Minecraft mc, RenderPipeline pipeline) {
        if (!RuntimeGate.canRunRender() || mc == null || pipeline == null) return;
        RenderTarget framebuffer = mc.gameRenderer.mainRenderTarget();
        if (framebuffer == null) return;
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        if (width <= 0 || height <= 0) return;

        Themes.Theme theme = Theme.theme();
        int accent = theme != null ? theme.accent() : 0xFF5CC8E7;
        int background = theme != null ? theme.windowBg() : 0xFF10141B;
        float time = (float) ((System.nanoTime() - SHADER_TIME_ORIGIN_NANOS) / 1_000_000_000.0);

        FullScreenRenderer.ensureInit();
        MenuBackgroundUniforms.update(width, height, time, accent, background);

        GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        Matrix4f previousMeshProjection = MeshRenderer.projection();
        boolean previousRendering3D = RenderState.rendering3D;
        boolean pushedModelView = false;
        var modelView = RenderSystem.getModelViewStack();
        try {
            modelView.pushMatrix();
            pushedModelView = true;
            modelView.identity();
            if (projection == null) projection = new ProjectionMatrixBuffer("combatant-menu-bg-projection");
            Matrix4f identityProjection = IDENTITY.identity();
            RenderSystem.setProjectionMatrix(projection.getBuffer(identityProjection), ProjectionType.PERSPECTIVE);
            MeshRenderer.setProjection(identityProjection);
            RenderState.rendering3D = false;

            FullScreenRenderer.begin("Combatant Main Menu Procedural Background")
                    .attachment(framebuffer)
                    .pipeline(pipeline)
                    .uniform("MenuBackground", MenuBackgroundUniforms.get())
                    .end();
        } finally {
            if (pushedModelView) modelView.popMatrix();
            MeshRenderer.setProjection(previousMeshProjection);
            RenderState.rendering3D = previousRendering3D;
            restoreProjection(previousProjection, previousProjectionType);
        }
    }

    private static void restoreProjection(GpuBufferSlice previousProjection, ProjectionType previousProjectionType) {
        if (previousProjection != null && previousProjectionType != null) {
            RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
        }
    }

    private enum BackgroundMode {
        PNG,
        AURORA,
        WAVES
    }

    private enum TimeBand {
        MORNING,
        DAY,
        DUSK,
        NIGHT
    }
}
