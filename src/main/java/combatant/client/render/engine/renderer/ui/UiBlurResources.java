/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.vertex.CombatantVertexFormats;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Owns UI render targets, captured glass state and the frame-local Kawase cache.
 * These are backend resources, not part of the shape drawing facade.
 */
public final class UiBlurResources {
    private static final int MAX_KAWASE_LEVELS = Renderer2D.BlurQuality.ULTRA.iterations;
    private static final TextureTarget[] SURFACE_KAWASE_DOWN = new TextureTarget[MAX_KAWASE_LEVELS];
    private static final TextureTarget[] SURFACE_KAWASE_UP = new TextureTarget[MAX_KAWASE_LEVELS];
    private static final TextureTarget[] CAPTURED_WORLD_KAWASE_DOWN = new TextureTarget[MAX_KAWASE_LEVELS];
    private static final TextureTarget[] CAPTURED_WORLD_KAWASE_UP = new TextureTarget[MAX_KAWASE_LEVELS];
    private static final FrameBlurCacheEntry SURFACE_FRAME_CACHE = new FrameBlurCacheEntry();
    private static final FrameBlurCacheEntry CAPTURED_WORLD_FRAME_CACHE = new FrameBlurCacheEntry();

    private static TextureTarget effects;
    private static TextureTarget glassSource;
    private static MeshBuilder compositeMesh;
    private static int compositeWidth = -1;
    private static int compositeHeight = -1;
    private static boolean worldSourceReady;
    private static boolean liquidGlassBlurRequested;
    private static boolean blurBeforeNextShapeClipRequested;

    private UiBlurResources() {
    }

    public static FrameBlurCacheEntry frameCache() {
        return SURFACE_FRAME_CACHE;
    }

    public static void beginDeferredFrame() {
        liquidGlassBlurRequested = false;
        blurBeforeNextShapeClipRequested = false;
    }

    public static TextureTarget ensureEffects(Minecraft minecraft) {
        if (minecraft == null) return null;
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) return null;
        effects = CombatantRenderSystem.resources().persistentFramebuffer(
                "combatant-ui-effects", width, height, false, "Renderer2D.effects"
        );
        return effects;
    }

    public static TextureTarget ensureGlassSource(Minecraft minecraft) {
        if (minecraft == null) return null;
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) return null;
        glassSource = CombatantRenderSystem.resources().persistentFramebuffer(
                "combatant-ui-glass-source", width, height, false, "Renderer2D.glassSource"
        );
        return glassSource;
    }

    public static boolean copyMainColor(RenderTarget source, TextureTarget target) {
        if (source == null || target == null) return false;
        if (source.getColorTexture() == null || target.getColorTexture() == null) return false;

        int width = Math.min(source.width, target.width);
        int height = Math.min(source.height, target.height);
        if (width <= 0 || height <= 0) return false;

        try {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                    source.getColorTexture(),
                    target.getColorTexture(),
                    0, 0, 0,
                    0, 0,
                    width, height
            );
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void requestLiquidGlassBlur() {
        liquidGlassBlurRequested = true;
    }

    public static void requestBeforeNextShapeClip() {
        liquidGlassBlurRequested = true;
        blurBeforeNextShapeClipRequested = true;
    }

    public static void prepareBeforeShapeClipIfRequested() {
        if (!blurBeforeNextShapeClipRequested) return;
        blurBeforeNextShapeClipRequested = false;
        UiDeferredScheduler.deferAction(UiBlurResources::prepareCapturedWorldBlur);
    }

    public static void captureWorldSource() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        RenderTarget framebuffer = minecraft.gameRenderer.mainRenderTarget();
        TextureTarget source = ensureGlassSource(minecraft);
        worldSourceReady = copyMainColor(framebuffer, source);
        if (!worldSourceReady || !liquidGlassBlurRequested || source == null) return;
        prepareCapturedWorldBlur();
    }

    public static void invalidateWorldSource() {
        worldSourceReady = false;
    }

    public static boolean isWorldSourceReady() {
        return worldSourceReady;
    }

    public static TextureTarget ensureKawaseDown(Minecraft minecraft, int level) {
        return ensureKawaseTarget(
                minecraft, SURFACE_KAWASE_DOWN, "combatant-ui-kawase-down-", "Renderer2D.kawaseDown", level
        );
    }

    public static TextureTarget ensureKawaseUp(Minecraft minecraft, int level) {
        return ensureKawaseTarget(
                minecraft, SURFACE_KAWASE_UP, "combatant-ui-kawase-up-", "Renderer2D.kawaseUp", level
        );
    }

    static TextureTarget ensureKawaseDown(Minecraft minecraft,
                                          int level,
                                          @Nullable GpuTextureView sourceView) {
        if (!isCapturedWorldSource(sourceView)) return ensureKawaseDown(minecraft, level);
        return ensureKawaseTarget(
                minecraft,
                CAPTURED_WORLD_KAWASE_DOWN,
                "combatant-ui-glass-kawase-down-",
                "Renderer2D.glassKawaseDown",
                level
        );
    }

    static TextureTarget ensureKawaseUp(Minecraft minecraft,
                                        int level,
                                        @Nullable GpuTextureView sourceView) {
        if (!isCapturedWorldSource(sourceView)) return ensureKawaseUp(minecraft, level);
        return ensureKawaseTarget(
                minecraft,
                CAPTURED_WORLD_KAWASE_UP,
                "combatant-ui-glass-kawase-up-",
                "Renderer2D.glassKawaseUp",
                level
        );
    }

    public static MeshBuilder ensureCompositeMesh(int width, int height) {
        if (width <= 0 || height <= 0) return null;
        if (compositeMesh != null && compositeWidth == width && compositeHeight == height) {
            return compositeMesh;
        }

        MeshBuilder mesh = new MeshBuilder(
                CombatantVertexFormats.POS2_TEXTURE_COLOR,
                com.mojang.blaze3d.PrimitiveTopology.TRIANGLES,
                4,
                6
        );
        mesh.begin();
        int i1 = mesh.vec2(0.0, 0.0).raw2(0.0, 1.0).color(255, 255, 255, 255).next();
        int i2 = mesh.vec2(0.0, height).raw2(0.0, 0.0).color(255, 255, 255, 255).next();
        int i3 = mesh.vec2(width, height).raw2(1.0, 0.0).color(255, 255, 255, 255).next();
        int i4 = mesh.vec2(width, 0.0).raw2(1.0, 1.0).color(255, 255, 255, 255).next();
        mesh.quad(i1, i2, i3, i4);
        mesh.end();

        compositeMesh = mesh;
        compositeWidth = width;
        compositeHeight = height;
        return compositeMesh;
    }

    public static long currentFrameId() {
        RenderFrameContext context = CombatantRenderSystem.ensureFrameContext();
        return context != null ? context.frameId() : Long.MIN_VALUE;
    }

    public static RenderPhase currentPhase() {
        RenderFrameContext context = CombatantRenderSystem.ensureFrameContext();
        return context != null && context.phase() != null ? context.phase() : RenderPhase.NONE;
    }

    public static int scaleBits(float uiScale) {
        return Float.floatToIntBits(uiScale <= 0.0f ? 1.0f : uiScale);
    }

    public static @Nullable FrameBlurCacheEntry findReusable(
            long frameId,
            RenderPhase phase,
            @Nullable GpuTextureView sourceView,
            @Nullable GpuSampler sourceSampler,
            float screenWidth,
            float screenHeight,
            float uiScale,
            Renderer2D.BlurQuality blurQuality,
            float offsetPx) {
        RenderPhase cachePhase = cachePhaseForSource(phase, sourceView);
        float cacheUiScale = cacheScaleForSource(sourceView, uiScale);
        FrameBlurCacheEntry cache = cacheForSource(sourceView);
        return cache.matches(
                frameId,
                cachePhase,
                sourceView,
                sourceSampler,
                screenWidth,
                screenHeight,
                cacheUiScale,
                blurQuality,
                offsetPx
        ) ? cache : null;
    }

    public static void remember(
            long frameId,
            RenderPhase phase,
            @Nullable GpuTextureView sourceView,
            @Nullable GpuSampler sourceSampler,
            @Nullable GpuTextureView blurredView,
            @Nullable GpuSampler blurredSampler,
            float screenWidth,
            float screenHeight,
            float uiScale,
            Renderer2D.BlurQuality blurQuality,
            float offsetPx) {
        cacheForSource(sourceView).set(
                frameId,
                cachePhaseForSource(phase, sourceView),
                sourceView,
                sourceSampler,
                blurredView,
                blurredSampler,
                screenWidth,
                screenHeight,
                cacheScaleForSource(sourceView, uiScale),
                blurQuality,
                offsetPx
        );
    }

    private static void prepareCapturedWorldBlur() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !worldSourceReady) return;

        TextureTarget sourceBuffer = ensureGlassSource(minecraft);
        GpuSampler sourceSampler = PostProcessManager.getSampler();
        if (sourceBuffer == null || sourceSampler == null) return;

        GpuTextureView sourceView = sourceBuffer.getColorTextureView();
        if (sourceView == null) return;

        float uiScale = ViewportContext.getUiScale();
        if (uiScale <= 0.0f) uiScale = 1.0f;
        OrderedUiBatcher.prewarmLiquidGlassBlur(
                minecraft,
                sourceView,
                sourceSampler,
                minecraft.getWindow().getWidth(),
                minecraft.getWindow().getHeight(),
                uiScale
        );
    }

    private static TextureTarget ensureKawaseTarget(
            Minecraft minecraft,
            TextureTarget[] targets,
            String namePrefix,
            String labelPrefix,
            int level) {
        if (minecraft == null || targets == null || level < 0 || level >= targets.length) return null;
        int divisor = 1 << (level + 1);
        int width = Math.max(1, minecraft.getWindow().getWidth() / divisor);
        int height = Math.max(1, minecraft.getWindow().getHeight() / divisor);
        TextureTarget target = CombatantRenderSystem.resources().persistentFramebuffer(
                namePrefix + level,
                width,
                height,
                false,
                labelPrefix + level
        );
        targets[level] = target;
        return target;
    }

    private static RenderPhase cachePhaseForSource(RenderPhase phase, @Nullable GpuTextureView sourceView) {
        if (isCapturedWorldSource(sourceView)) return RenderPhase.HUD_CAPTURE;
        return phase != null ? phase : RenderPhase.NONE;
    }

    private static float cacheScaleForSource(@Nullable GpuTextureView sourceView, float uiScale) {
        return isCapturedWorldSource(sourceView) ? 1.0f : uiScale;
    }

    static boolean isCapturedWorldSource(@Nullable GpuTextureView sourceView) {
        return sourceView != null
                && glassSource != null
                && sourceView == glassSource.getColorTextureView();
    }

    private static FrameBlurCacheEntry cacheForSource(@Nullable GpuTextureView sourceView) {
        return isCapturedWorldSource(sourceView) ? CAPTURED_WORLD_FRAME_CACHE : SURFACE_FRAME_CACHE;
    }
}
