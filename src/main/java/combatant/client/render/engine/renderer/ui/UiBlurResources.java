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
import combatant.client.render.engine.rhi.resource.TransientTargetDescriptor;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.vertex.CombatantVertexFormats;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Owns UI render targets, captured glass state and the frame-local Kawase cache.
 * These are backend resources, not part of the shape drawing facade.
 */
public final class UiBlurResources {
    private static final FrameBlurCacheEntry SURFACE_FRAME_CACHE = new FrameBlurCacheEntry();
    private static final FrameBlurCacheEntry CAPTURED_WORLD_FRAME_CACHE = new FrameBlurCacheEntry();
    private static final FrameBlurCacheEntry UI_UNDERLAY_FRAME_CACHE = new FrameBlurCacheEntry();
    private static final EnumSet<Renderer2D.Deferred2DLayer> UI_UNDERLAY_REQUESTED =
            EnumSet.noneOf(Renderer2D.Deferred2DLayer.class);

    private static TextureTarget effects;
    private static TextureTarget glassSource;
    private static TextureTarget uiUnderlay;
    private static Renderer2D.Deferred2DLayer activeUiUnderlayLayer;
    private static long activeUiUnderlayFrame = Long.MIN_VALUE;
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
        UI_UNDERLAY_REQUESTED.clear();
        activeUiUnderlayLayer = null;
        activeUiUnderlayFrame = Long.MIN_VALUE;
        // These handles belong to FRAME transient allocations. Drop stale references before the
        // pool is allowed to alias their physical storage to another logical resource.
        effects = null;
        glassSource = null;
        uiUnderlay = null;
        worldSourceReady = false;
        SURFACE_FRAME_CACHE.clear();
        CAPTURED_WORLD_FRAME_CACHE.clear();
        UI_UNDERLAY_FRAME_CACHE.clear();
    }

    public static void requestUiUnderlay(Renderer2D.Deferred2DLayer layer) {
        if (layer != null) UI_UNDERLAY_REQUESTED.add(layer);
    }

    public static boolean isUiUnderlayRequested(Renderer2D.Deferred2DLayer layer) {
        if (layer == null) return false;
        if (!isHudLayer(layer)) return UI_UNDERLAY_REQUESTED.contains(layer);
        for (Renderer2D.Deferred2DLayer requested : UI_UNDERLAY_REQUESTED) {
            if (isHudLayer(requested)) return true;
        }
        return false;
    }

    public static @Nullable TextureTarget beginUiUnderlayLayer(
            Minecraft minecraft,
            Renderer2D.Deferred2DLayer layer) {
        if (minecraft == null || !isUiUnderlayRequested(layer)) return null;
        TextureTarget target = ensureUiUnderlay(minecraft);
        if (target == null || target.getColorTexture() == null) return null;

        long frame = currentFrameId();
        Renderer2D.Deferred2DLayer domain = underlayDomain(layer);
        if (activeUiUnderlayFrame != frame || activeUiUnderlayLayer != domain) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(
                    target.getColorTexture(), new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
            activeUiUnderlayFrame = frame;
            activeUiUnderlayLayer = domain;
        }
        return target;
    }

    public static @Nullable TextureTarget uiUnderlayTarget(
            Minecraft minecraft,
            Renderer2D.Deferred2DLayer layer) {
        return beginUiUnderlayLayer(minecraft, layer);
    }

    public static @Nullable GpuTextureView activeUiUnderlayView() {
        return activeUiUnderlayLayer != null && uiUnderlay != null
                ? uiUnderlay.getColorTextureView()
                : null;
    }

    private static @Nullable TextureTarget ensureUiUnderlay(Minecraft minecraft) {
        if (minecraft == null) return null;
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) return null;
        uiUnderlay = CombatantRenderSystem.resources().frameTransient(
                TransientTargetDescriptor.frame(
                        "combatant-ui-underlay", width, height, false, "Renderer2D.uiUnderlay"
                )
        );
        return uiUnderlay;
    }

    private static boolean isHudLayer(Renderer2D.Deferred2DLayer layer) {
        return layer != null
                && layer.ordinal() >= Renderer2D.Deferred2DLayer.HUD_FIRST.ordinal()
                && layer.ordinal() <= Renderer2D.Deferred2DLayer.HUD_LAST.ordinal();
    }

    private static Renderer2D.Deferred2DLayer underlayDomain(Renderer2D.Deferred2DLayer layer) {
        return isHudLayer(layer) ? Renderer2D.Deferred2DLayer.HUD_FIRST : layer;
    }

    public static TextureTarget ensureEffects(Minecraft minecraft) {
        if (minecraft == null) return null;
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) return null;
        effects = CombatantRenderSystem.resources().frameTransient(
                TransientTargetDescriptor.frame(
                        "combatant-ui-effects", width, height, false, "Renderer2D.effects"
                )
        );
        return effects;
    }

    public static TextureTarget ensureGlassSource(Minecraft minecraft) {
        if (minecraft == null) return null;
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) return null;
        glassSource = CombatantRenderSystem.resources().frameTransient(
                TransientTargetDescriptor.frame(
                        "combatant-ui-glass-source", width, height, false, "Renderer2D.glassSource"
                )
        );
        return glassSource;
    }

    public static boolean copyMainColor(RenderTarget source, TextureTarget target) {
        if (source == null || target == null) return false;
        if (source.getColorTexture() == null || target.getColorTexture() == null) return false;

        try {
            return CombatantRenderSystem.rhi().textureBlitter().copyFast(
                    source.getColorTextureView(), target.getColorTextureView()
            );
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

    /** Module-only replays changed the HUD backdrop without changing its texture handle. */
    public static void backdropContributionsSubmitted() {
        CAPTURED_WORLD_FRAME_CACHE.clear();
        if (worldSourceReady && liquidGlassBlurRequested) {
            prepareCapturedWorldBlur();
        }
    }

    public static void invalidateWorldSource() {
        worldSourceReady = false;
    }

    public static boolean isWorldSourceReady() {
        return worldSourceReady;
    }

    public static TextureTarget ensureKawaseDown(Minecraft minecraft, int level) {
        return ensureKawaseTarget(minecraft, "surface", level);
    }

    public static TextureTarget ensureKawaseUp(Minecraft minecraft, int level) {
        return ensureKawaseTarget(minecraft, "surface", level);
    }

    static TextureTarget ensureKawaseDown(Minecraft minecraft,
                                          int level,
                                          @Nullable GpuTextureView sourceView) {
        if (isUiUnderlaySource(sourceView)) {
            return ensureKawaseTarget(minecraft, "ui-underlay", level);
        }
        if (!isCapturedWorldSource(sourceView)) return ensureKawaseDown(minecraft, level);
        return ensureKawaseTarget(minecraft, "captured-world", level);
    }

    static TextureTarget ensureKawaseUp(Minecraft minecraft,
                                        int level,
                                        @Nullable GpuTextureView sourceView) {
        if (isUiUnderlaySource(sourceView)) {
            return ensureKawaseTarget(minecraft, "ui-underlay", level);
        }
        if (!isCapturedWorldSource(sourceView)) return ensureKawaseUp(minecraft, level);
        return ensureKawaseTarget(minecraft, "captured-world", level);
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
        // The underlay can receive more ordinary UI draws between two glass effects in the
        // same frame. Until capture generations are explicit in the pass graph, reusing its
        // earlier blur would be stale. PASS_THROUGH never enters this cache.
        if (isUiUnderlaySource(sourceView)) return null;
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
        if (isUiUnderlaySource(sourceView)) return;
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

    private static TextureTarget ensureKawaseTarget(Minecraft minecraft,
                                                    String sourceDomain,
                                                    int level) {
        if (minecraft == null || level < 0 || level >= Renderer2D.BlurQuality.ULTRA.iterations) return null;
        int divisor = 1 << (level + 1);
        int width = Math.max(1, minecraft.getWindow().getWidth() / divisor);
        int height = Math.max(1, minecraft.getWindow().getHeight() / divisor);
        // The up pass reuses down[level] after its last read. Only the final level-zero result
        // remains live for frame-cache consumers, so a second up[] allocation is unnecessary.
        String logicalName = "ui-blur-" + sourceDomain + "-level-" + level;
        return CombatantRenderSystem.resources().frameTransient(
                TransientTargetDescriptor.frame(
                        logicalName,
                        width,
                        height,
                        false,
                        "UiPassCompiler.blur"
                )
        );
    }

    private static RenderPhase cachePhaseForSource(RenderPhase phase, @Nullable GpuTextureView sourceView) {
        if (isUiUnderlaySource(sourceView)) return RenderPhase.HUD_EFFECTS;
        if (isCapturedWorldSource(sourceView)) return RenderPhase.HUD_CAPTURE;
        return phase != null ? phase : RenderPhase.NONE;
    }

    private static float cacheScaleForSource(@Nullable GpuTextureView sourceView, float uiScale) {
        return isCapturedWorldSource(sourceView) || isUiUnderlaySource(sourceView) ? 1.0f : uiScale;
    }

    static boolean isCapturedWorldSource(@Nullable GpuTextureView sourceView) {
        return sourceView != null
                && glassSource != null
                && sourceView == glassSource.getColorTextureView();
    }

    static boolean isUiUnderlaySource(@Nullable GpuTextureView sourceView) {
        return sourceView != null
                && uiUnderlay != null
                && sourceView == uiUnderlay.getColorTextureView();
    }

    private static FrameBlurCacheEntry cacheForSource(@Nullable GpuTextureView sourceView) {
        if (isUiUnderlaySource(sourceView)) return UI_UNDERLAY_FRAME_CACHE;
        return isCapturedWorldSource(sourceView) ? CAPTURED_WORLD_FRAME_CACHE : SURFACE_FRAME_CACHE;
    }
}
