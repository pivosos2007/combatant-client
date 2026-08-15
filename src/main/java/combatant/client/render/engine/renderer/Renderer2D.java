/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.command.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.ui.BlurSource;
import combatant.client.render.engine.renderer.ui.Deferred2DSubmit;
import combatant.client.render.engine.renderer.ui.DrawBatch;
import combatant.client.render.engine.renderer.ui.FrameBlurCacheEntry;
import combatant.client.render.engine.renderer.ui.ItemBatchRenderer;
import combatant.client.render.engine.renderer.ui.OrderedUiBatcher;
import combatant.client.render.engine.renderer.ui.UiBatchStats;
import combatant.client.render.engine.renderer.ui.UiBatchType;
import combatant.client.render.engine.renderer.ui.UiBlurResources;
import combatant.client.render.engine.renderer.ui.UiDeferredScheduler;
import combatant.client.render.engine.renderer.ui.UiDirectTexturedRenderer;
import combatant.client.render.engine.renderer.ui.UiItemSubmission;
import combatant.client.render.engine.renderer.ui.UiRenderDispatcher;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.renderer.ui.draw.*;
import combatant.client.render.engine.svg.SvgMeshBackend;
import combatant.client.render.engine.svg.SvgRegistry;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.GlyphFont;
import combatant.client.render.engine.text.backend.TextPlacementMode;
import combatant.client.render.engine.uniform.MeshBuilder;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import static combatant.client.render.engine.renderer.ui.draw.UiMeshGeometry.*;

public final class Renderer2D {
    public static final int ITEM_OVERLAY_NONE = 0;
    public static final int ITEM_OVERLAY_COUNT = 1;
    public static final int ITEM_OVERLAY_DURABILITY = 1 << 1;
    public static final int ITEM_OVERLAY_COOLDOWN = 1 << 2;
    public static final int ITEM_OVERLAY_DURABILITY_TEXT = 1 << 3;
    public static final int ITEM_OVERLAY_ALL = ITEM_OVERLAY_COUNT | ITEM_OVERLAY_DURABILITY | ITEM_OVERLAY_COOLDOWN;
    private static final int DEFAULT_ITEM_DURABILITY_THRESHOLD = 100;
    private static final int DEFAULT_ITEM_DURABILITY_TEXT_COLOR_THRESHOLD = 70;
    public static OrderedUiBatcher UI_BATCHER = new OrderedUiBatcher();
    public static final BatchStats BATCH_STATS = new BatchStats();

    /**
     * Frame-local blurred framebuffer cache.
     *
     * <p>HUD widgets often get split by text, item, scissor or nested compatibility flushes.
     * The cached texture is a Dual Kawase result for a concrete source, quality and offset.
     * Liquid glass is a material call and prepares this blur internally; callers no longer need
     * to draw an extra blur rectangle under it.</p>
     */
    public static final FrameBlurCacheEntry FRAME_BLUR_CACHE = UiBlurResources.frameCache();

    /**
     * Compatibility mirror for older renderer integrations. New code should use
     * {@link #isWorldGlassSourceReady()} instead of mutating this field.
     */
    @Deprecated
    public static boolean uiGlassWorldSourceReady;

    public enum Deferred2DLayer {
        /**
         * Legacy safety bucket for 2D extracted outside a concrete HUD slot.
         * Drained before vanilla GuiRenderer.render().
         */
        BEFORE_VANILLA_GUI,

        HUD_FIRST,
        HUD_BEFORE_MISC_OVERLAYS,
        HUD_AFTER_MISC_OVERLAYS,
        HUD_AFTER_BOSS_BAR,
        HUD_BEFORE_DEMO_TIMER,
        HUD_BEFORE_CHAT,
        HUD_AFTER_SUBTITLES,
        HUD_LAST,

        /**
         * Explicit top screen bucket. This is intentionally not used by normal HudPhase slots.
         */
        AFTER_VANILLA_GUI
    }

    public enum BlurQuality {
        LOW(0, 2),
        MEDIUM(1, 3),
        HIGH(2, 4),
        ULTRA(3, 5);

        public final int id;
        public final int iterations;

        BlurQuality(int id, int iterations) {
            this.id = id;
            this.iterations = iterations;
        }
    }

    public static final BlurQuality DEFAULT_BLUR_QUALITY = BlurQuality.MEDIUM;
    public static final BlurQuality DEFAULT_LIQUID_GLASS_BLUR_QUALITY = BlurQuality.HIGH;
    public static final float DEFAULT_KAWASE_OFFSET_PX = 1.0f;
    public static final float LIQUID_GLASS_KAWASE_OFFSET_PX = 1.15f;

    public static Renderer2D COLOR;
    public static Renderer2D TEXTURE;
    private final boolean textured;
    private final MeshBuilder texturedTriangles;
    private final int[] gradientTmp = new int[4];
    private final float[] cornerRadiiTmp = new float[4];
    private final double[] rectShapeTmp = new double[256];
    private final double[] connectorTmp = new double[64];
    private final double[] connectorAnchorTmp = new double[4];
    private final int[] polygonIndexTmp = new int[64];
    private final int[] polygonVertexTmp = new int[64];
    private final int[] warpedShapeVertexTmp = new int[81];
    private final double[] progressShapeTmp = new double[256];
    private double alpha = 1.0;

    public Renderer2D(boolean textured) {
        this.textured = textured;
        this.texturedTriangles = textured ? new MeshBuilder(CombatantRenderPipelines.UI_TEXTURED) : null;
    }

    public static void init() {
        COLOR = new Renderer2D(false);
        TEXTURE = new Renderer2D(true);
        ItemBatchRenderer.init();
    }

    // ---- Public drawing facade -------------------------------------------------

    public void setAlpha(double alpha) {
        this.alpha = Mth.clamp(alpha, 0.0, 1.0);
    }

    public double getAlpha() {
        return alpha;
    }

    public void shape(UiShape shape, UiPaint paint) {
        shape(shape, paint, UiStroke.NONE, true);
    }

    public void shapeStroke(UiShape shape, UiPaint paint, UiStroke stroke) {
        shape(shape, paint, stroke, false);
    }

    public void box(UiBoxShape box, UiPaint paint) {
        box(box, paint, UiStroke.NONE, true);
    }

    public void boxStroke(UiBoxShape box, UiPaint paint, UiStroke stroke) {
        box(box, paint, stroke, false);
    }

    public void box(UiBoxShape box, UiPaint paint, UiStroke stroke, boolean fill) {
        if (box == null || paint == null) return;
        UiStroke safeStroke = stroke == null ? UiStroke.NONE : stroke;
        recordUi(new UiShapeCommand(UiShape.box(box), paint, safeStroke, fill));
        if (box.isSquircle()) {
            renderSquircleSdf(box, paint, safeStroke, fill);
        } else {
            renderFlexibleBoxFallback(box, paint, safeStroke, fill);
        }
    }

    public void shape(UiShape shape, UiPaint paint, UiStroke stroke, boolean fill) {
        if (shape == null || paint == null) return;
        recordUi(new UiShapeCommand(shape, paint, stroke == null ? UiStroke.NONE : stroke, fill));
    }

    public void path(UiShape path, UiPaint paint, UiStroke stroke, boolean fill) {
        if (path == null || paint == null) return;
        recordUi(new UiPathCommand(path, paint, stroke == null ? UiStroke.NONE : stroke, fill));
    }

    public void effect(UiEffectSpec effect) {
        if (effect == null) return;
        recordUi(new UiEffectRegionCommand(effect));
    }

    // Use this path when the current projection is already the target space
    // (for example UNSCALED_LOGICAL HUD / ClickGUI coordinates).
    public void item(ItemStack stack, double x, double y) {
        item(stack, x, y, 1.0f, 0, ITEM_OVERLAY_NONE, null);
    }

    public void item(ItemStack stack, double x, double y, float scale) {
        item(stack, x, y, scale, 0, ITEM_OVERLAY_NONE, null);
    }

    public void item(ItemStack stack, double x, double y, float scale, int seed) {
        item(stack, x, y, scale, seed, ITEM_OVERLAY_NONE, null);
    }

    public void item(ItemStack stack, double x, double y, float scale, int seed, boolean overlay) {
        item(stack, x, y, scale, seed, overlay ? ITEM_OVERLAY_ALL : ITEM_OVERLAY_NONE, null);
    }

    public void item(ItemStack stack, double x, double y, float scale, int seed, int overlayFlags) {
        item(stack, x, y, scale, seed, overlayFlags, null);
    }

    public void item(ItemStack stack,
                     double x,
                     double y,
                     float scale,
                     int seed,
                     int overlayFlags,
                     @Nullable String stackCountText) {
        item(null, stack, x, y, scale, seed, overlayFlags, stackCountText);
    }

    public void item(ItemStack stack,
                     double x,
                     double y,
                     float scale,
                     int seed,
                     int overlayFlags,
                     @Nullable String stackCountText,
                     int durabilityThresholdPercent,
                     int durabilityTextColorThresholdPercent) {
        item(null, stack, x, y, scale, seed, overlayFlags, stackCountText,
                durabilityThresholdPercent, durabilityTextColorThresholdPercent);
    }

    public void item(@Nullable LocalPlayer player,
                     ItemStack stack,
                     double x,
                     double y,
                     float scale,
                     int seed,
                     int overlayFlags,
                     @Nullable String stackCountText) {
        item(player, stack, x, y, scale, seed, overlayFlags, stackCountText,
                DEFAULT_ITEM_DURABILITY_THRESHOLD, DEFAULT_ITEM_DURABILITY_TEXT_COLOR_THRESHOLD);
    }

    public void item(@Nullable LocalPlayer player,
                     ItemStack stack,
                     double x,
                     double y,
                     float scale,
                     int seed,
                     int overlayFlags,
                     @Nullable String stackCountText,
                     int durabilityThresholdPercent,
                     int durabilityTextColorThresholdPercent) {
        item(player, stack, x, y, scale, scale, 0.0f, 0.0f, false, seed, true, overlayFlags, stackCountText,
                durabilityThresholdPercent, durabilityTextColorThresholdPercent);
    }

    public void itemPivot(ItemStack stack,
                          double x,
                          double y,
                          float scaleX,
                          float scaleY,
                          float pivotX,
                          float pivotY,
                          int seed,
                          int overlayFlags,
                          @Nullable String stackCountText) {
        item(null, stack, x, y, scaleX, scaleY, pivotX, pivotY, true, seed, true, overlayFlags, stackCountText,
                DEFAULT_ITEM_DURABILITY_THRESHOLD, DEFAULT_ITEM_DURABILITY_TEXT_COLOR_THRESHOLD);
    }

    public void itemOverlay(ItemStack stack,
                            double x,
                            double y,
                            float scale,
                            int overlayFlags,
                            @Nullable String stackCountText) {
        item(null, stack, x, y, scale, scale, 0.0f, 0.0f, false, 0, false, overlayFlags, stackCountText,
                DEFAULT_ITEM_DURABILITY_THRESHOLD, DEFAULT_ITEM_DURABILITY_TEXT_COLOR_THRESHOLD);
    }

    // Use this only when call-site coordinates are in raw framebuffer/unscaled space
    // and must be converted into the currently active Renderer2D projection.
    public void itemUnscaled(ItemStack stack,
                             double x,
                             double y,
                             float scale,
                             int seed,
                             int overlayFlags,
                             @Nullable String stackCountText) {
        float ratio = getUnscaledItemRatio();
        item(stack, x * ratio, y * ratio, scale * ratio, seed, overlayFlags, stackCountText);
    }

    public void itemPivotUnscaled(ItemStack stack,
                                  double x,
                                  double y,
                                  float scaleX,
                                  float scaleY,
                                  float pivotX,
                                  float pivotY,
                                  int seed,
                                  int overlayFlags,
                                  @Nullable String stackCountText) {
        float ratio = getUnscaledItemRatio();
        itemPivot(
                stack,
                x * ratio,
                y * ratio,
                scaleX * ratio,
                scaleY * ratio,
                pivotX * ratio,
                pivotY * ratio,
                seed,
                overlayFlags,
                stackCountText
        );
    }

    public void itemOverlayUnscaled(ItemStack stack,
                                    double x,
                                    double y,
                                    float scale,
                                    int overlayFlags,
                                    @Nullable String stackCountText) {
        float ratio = getUnscaledItemRatio();
        itemOverlay(stack, x * ratio, y * ratio, scale * ratio, overlayFlags, stackCountText);
    }

    public void item(@Nullable LocalPlayer player,
                     ItemStack stack,
                     double x,
                     double y,
                     float scaleX,
                     float scaleY,
                     float pivotX,
                     float pivotY,
                     boolean pivoted,
                     int seed,
                     boolean drawItem,
                     int overlayFlags,
                     @Nullable String stackCountText) {
        item(player, stack, x, y, scaleX, scaleY, pivotX, pivotY, pivoted, seed, drawItem, overlayFlags, stackCountText,
                DEFAULT_ITEM_DURABILITY_THRESHOLD, DEFAULT_ITEM_DURABILITY_TEXT_COLOR_THRESHOLD);
    }

    public void item(@Nullable LocalPlayer player,
                     ItemStack stack,
                     double x,
                     double y,
                     float scaleX,
                     float scaleY,
                     float pivotX,
                     float pivotY,
                     boolean pivoted,
                     int seed,
                     boolean drawItem,
                     int overlayFlags,
                     @Nullable String stackCountText,
                     int durabilityThresholdPercent,
                     int durabilityTextColorThresholdPercent) {
        UiItemSubmission.submit(
                textured,
                alpha,
                player,
                stack,
                x,
                y,
                scaleX,
                scaleY,
                pivotX,
                pivotY,
                pivoted,
                seed,
                drawItem,
                overlayFlags,
                stackCountText,
                durabilityThresholdPercent,
                durabilityTextColorThresholdPercent
        );
    }

    public void begin() {
        beginUiLayer();
        if (textured) {
            texturedTriangles.begin();
        } else {
            UI_BATCHER.begin();
        }
    }

    public void end() {
        if (textured && texturedTriangles.isBuilding()) {
            texturedTriangles.end();
        }
    }

    public void render() {
        if (textured) {
            render(null, null, null);
            return;
        }
        if (UI_BATCHER.hasPendingWork() || UiRenderDispatcher.hasPendingCommands()) {
            BATCH_STATS.noteFlushReason(FlushReason.RENDER_END);
        }
        UI_BATCHER.flush(true);
        flushUiLayer();
    }

    public void render(GpuTextureView textureView, GpuSampler sampler) {
        if (!textured) {
            throw new IllegalStateException("Renderer2D is not configured for textures.");
        }
        render("u_Texture", textureView, sampler);
    }

    public void render(Identifier textureId) {
        if (!textured) {
            throw new IllegalStateException("Renderer2D is not configured for textures.");
        }
        boolean submitted = false;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || textureId == null) return;
            AbstractTexture tex = mc.getTextureManager().getTexture(textureId);
            if (tex == null) return;
            GpuTextureView view = tex.getTextureView();
            GpuSampler sampler = tex.getSampler();
            if (view == null || sampler == null) return;
            submitted = true;
            render("u_Texture", view, sampler);
        } finally {
            if (!submitted) {
                flushUiLayer();
            }
        }
    }

    public void render(String samplerName, GpuTextureView samplerView, GpuSampler sampler) {
        UiDirectTexturedRenderer.submit(texturedTriangles, samplerName, samplerView, sampler);
    }

    public void svg(Identifier svgId, double x, double y, double width, double height) {
        svg(svgId, x, y, width, height, SvgRenderOptions.DEFAULT);
    }

    public void svg(String svgName, double x, double y, double width, double height) {
        svg(svgName, x, y, width, height, SvgRenderOptions.DEFAULT);
    }

    public void svg(String svgName,
                    double x,
                    double y,
                    double width,
                    double height,
                    SvgRenderOptions options) {
        if (textured) {
            throw new IllegalStateException("SVG mesh drawing is supported only on Renderer2D.COLOR.");
        }
        Identifier id = SvgRegistry.resolve(svgName);
        if (id == null) return;
        SvgMeshBackend.draw(this, id, x, y, width, height, options);
    }

    public void svg(Identifier svgId,
                    double x,
                    double y,
                    double width,
                    double height,
                    SvgRenderOptions options) {
        if (textured) {
            throw new IllegalStateException("SVG mesh drawing is supported only on Renderer2D.COLOR.");
        }
        SvgMeshBackend.draw(this, svgId, x, y, width, height, options);
    }

    public void svg(Path svgPath, double x, double y, double width, double height) {
        svg(svgPath, x, y, width, height, SvgRenderOptions.DEFAULT);
    }

    public void svg(Path svgPath,
                    double x,
                    double y,
                    double width,
                    double height,
                    SvgRenderOptions options) {
        if (textured) {
            throw new IllegalStateException("SVG mesh drawing is supported only on Renderer2D.COLOR.");
        }
        SvgMeshBackend.draw(this, svgPath, x, y, width, height, options);
    }

    public void textureQuad(GpuTextureView samplerView,
                            GpuSampler sampler,
                            double x,
                            double y,
                            double w,
                            double h) {
        textureQuad(samplerView, sampler, x, y, w, h, 0xFFFFFFFF);
    }

    public void textureQuad(GpuTextureView samplerView,
                            GpuSampler sampler,
                            double x,
                            double y,
                            double w,
                            double h,
                            int argb) {
        if (textured) {
            throw new IllegalStateException("Batched texture quad drawing is supported only on Renderer2D.COLOR.");
        }
        if (samplerView == null || sampler == null) return;
        if (w == 0.0 || h == 0.0) return;

        recordUi(new UiTextureDrawCommand(samplerView, sampler, UiShape.rect(x, y, w, h), UiPaint.solid(argb), 0f, 0f, 1f, 1f, false));
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.TEXTURED, samplerView, sampler);
        if (batch == null) {
            endAutoBatch(auto);
            return;
        }
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureQuadCapacity();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        int i1 = mesh.vec2(x, y).raw2(0.0, 0.0).color(r, g, b, a).next();
        int i2 = mesh.vec2(x, y + h).raw2(0.0, 1.0).color(r, g, b, a).next();
        int i3 = mesh.vec2(x + w, y + h).raw2(1.0, 1.0).color(r, g, b, a).next();
        int i4 = mesh.vec2(x + w, y).raw2(1.0, 0.0).color(r, g, b, a).next();
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void msdfTextureQuad(GpuTextureView samplerView,
                                GpuSampler sampler,
                                double x,
                                double y,
                                double w,
                                double h,
                                int argb,
                                float pxRange,
                                int atlasWidth,
                                int atlasHeight) {
        msdfTextureQuad(samplerView, sampler, x, y, w, h, argb, argb, argb, argb, pxRange, atlasWidth, atlasHeight);
    }

    public void msdfTextureQuad(GpuTextureView samplerView,
                                GpuSampler sampler,
                                double x,
                                double y,
                                double w,
                                double h,
                                int topLeft,
                                int topRight,
                                int bottomRight,
                                int bottomLeft,
                                float pxRange,
                                int atlasWidth,
                                int atlasHeight) {
        if (textured) {
            throw new IllegalStateException("Batched MSDF texture quad drawing is supported only on Renderer2D.COLOR.");
        }
        if (samplerView == null || sampler == null) return;
        if (w == 0.0 || h == 0.0) return;
        if (pxRange <= 0.0f || atlasWidth <= 0 || atlasHeight <= 0) return;

        recordUi(new UiTextureDrawCommand(samplerView, sampler, UiShape.rect(x, y, w, h), UiPaint.solid(topLeft), 0f, 0f, 1f, 1f, false));
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreateMsdf(samplerView, sampler, pxRange, atlasWidth, atlasHeight);
        if (batch == null) {
            endAutoBatch(auto);
            return;
        }
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureQuadCapacity();

        int i1 = mesh.vec2(x, y).raw2(0.0, 0.0).color((topLeft >>> 16) & 0xFF, (topLeft >>> 8) & 0xFF, topLeft & 0xFF, (topLeft >>> 24) & 0xFF).next();
        int i2 = mesh.vec2(x, y + h).raw2(0.0, 1.0).color((bottomLeft >>> 16) & 0xFF, (bottomLeft >>> 8) & 0xFF, bottomLeft & 0xFF, (bottomLeft >>> 24) & 0xFF).next();
        int i3 = mesh.vec2(x + w, y + h).raw2(1.0, 1.0).color((bottomRight >>> 16) & 0xFF, (bottomRight >>> 8) & 0xFF, bottomRight & 0xFF, (bottomRight >>> 24) & 0xFF).next();
        int i4 = mesh.vec2(x + w, y).raw2(1.0, 0.0).color((topRight >>> 16) & 0xFF, (topRight >>> 8) & 0xFF, topRight & 0xFF, (topRight >>> 24) & 0xFF).next();
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void quadBatch(Consumer<QuadBatch> consumer) {
        if (textured) {
            throw new IllegalStateException("Quad batching is supported only on Renderer2D.COLOR.");
        }
        if (consumer == null) return;

        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.COLORED, null, null);
        if (batch == null) {
            endAutoBatch(auto);
            return;
        }

        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;
        QuadBatch quadBatch = new QuadBatch(mesh);
        try {
            consumer.accept(quadBatch);
        } finally {
            endAutoBatch(auto);
        }
    }

    public void line(double x1, double y1, double x2, double y2, int argb) {
        double[] p = {x1, y1, x2, y2};
        path(UiShape.polyline(p, 2, false), UiPaint.solid(argb), UiStroke.of(1.0), false);
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.LINES, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureLineCapacity();
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int i1 = mesh.vec2(x1, y1).color(r, g, b, a).next();
        int i2 = mesh.vec2(x2, y2).color(r, g, b, a).next();
        mesh.line(i1, i2);

        endAutoBatch(auto);
    }

    public void line(double x1, double y1, double x2, double y2, RenderColor color) {
        double[] p = {x1, y1, x2, y2};
        path(UiShape.polyline(p, 2, false), UiPaint.solid(color.argb()), UiStroke.of(1.0), false);
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.LINES, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureLineCapacity();
        int i1 = mesh.vec2(x1, y1).color(color.r, color.g, color.b, color.a).next();
        int i2 = mesh.vec2(x2, y2).color(color.r, color.g, color.b, color.a).next();
        mesh.line(i1, i2);

        endAutoBatch(auto);
    }

    public void boxLines(double x, double y, double width, double height, int argb) {
        shapeStroke(UiShape.rect(x, y, width, height), UiPaint.solid(argb), UiStroke.of(1.0));
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.LINES, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureCapacity(4, 8);

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int i1 = mesh.vec2(x, y).color(r, g, b, a).next();
        int i2 = mesh.vec2(x, y + height).color(r, g, b, a).next();
        int i3 = mesh.vec2(x + width, y + height).color(r, g, b, a).next();
        int i4 = mesh.vec2(x + width, y).color(r, g, b, a).next();

        mesh.line(i1, i2);
        mesh.line(i2, i3);
        mesh.line(i3, i4);
        mesh.line(i4, i1);

        endAutoBatch(auto);
    }

    public void quad(double x, double y, double width, double height, int argb) {
        quad(x, y, width, height, argb, argb, argb, argb);
    }

    public void quad(double x, double y, double width, double height, int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        shape(UiShape.rect(x, y, width, height), UiPaint.corners(cTopLeft, cTopRight, cBottomRight, cBottomLeft));
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.COLORED, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        appendColoredQuad(mesh, x, y, width, height, cTopLeft, cTopRight, cBottomRight, cBottomLeft);

        endAutoBatch(auto);
    }

    public void roundedRect(double x, double y, double w, double h,
                            float radius, float softness, int argb) {
        roundedRectMasked(x, y, w, h, x, y, w, h, radius, softness, argb);
    }

    public void roundedRect(double x, double y, double w, double h,
                            float radius, int argb) {
        roundedRect(x, y, w, h, radius, 0.0f, argb);
    }

    public void circle(double cx, double cy, double radius, int argb) {
        circle(cx, cy, radius, 1.1f, argb);
    }

    public void circle(double cx, double cy, double radius, float softness, int argb) {
        shape(UiShape.circle(cx, cy, radius), UiPaint.solid(argb));
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.CIRCLE, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double outerRadius = radius + Math.max(0.0f, softness);
        double x = cx - outerRadius;
        double y = cy - outerRadius;
        double w = outerRadius * 2.0;
        double h = outerRadius * 2.0;

        if (RenderWarpStack.active()) {
            appendWarpedSdfGrid(mesh, x, y, w, h, argb, argb, argb, argb,
                    UiRect.of(x, y, w, h), (float) radius, softness, 0.0f, 0.0f);
        } else {
            mesh.ensureQuadCapacity();
            int i1 = mesh.vec2(x, y).local2(x, y).color(r, g, b, a).vec4(x, y, w, h).vec4((float) radius, softness, 0f, 0f).next();
            int i2 = mesh.vec2(x, y + h).local2(x, y + h).color(r, g, b, a).vec4(x, y, w, h).vec4((float) radius, softness, 0f, 0f).next();
            int i3 = mesh.vec2(x + w, y + h).local2(x + w, y + h).color(r, g, b, a).vec4(x, y, w, h).vec4((float) radius, softness, 0f, 0f).next();
            int i4 = mesh.vec2(x + w, y).local2(x + w, y).color(r, g, b, a).vec4(x, y, w, h).vec4((float) radius, softness, 0f, 0f).next();
            mesh.quad(i1, i2, i3, i4);
        }

        endAutoBatch(auto);
    }

    public void circleSoftShadow(double cx, double cy, double radius, float blur, float innerAlpha, int argb) {
        if (radius <= 0.0) return;
        double size = radius * 2.0;
        roundedRectSoftShadow(cx - radius, cy - radius, size, size, (float) radius, blur, innerAlpha, argb);
    }

    public void circleStroke(double cx, double cy, double radius, double thickness, int argb) {
        circleStroke(cx, cy, radius, thickness, 1.1f, argb);
    }

    public void circleStroke(double cx, double cy, double radius, double thickness, float softness, int argb) {
        shapeStroke(UiShape.circle(cx, cy, radius), UiPaint.solid(argb), UiStroke.of(thickness));
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.CIRCLE, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        float stroke = (float) Math.max(0.0, thickness);
        double outerRadius = radius + stroke * 0.5 + softness;
        double x = cx - outerRadius;
        double y = cy - outerRadius;
        double w = outerRadius * 2.0;
        double h = outerRadius * 2.0;

        if (RenderWarpStack.active()) {
            appendWarpedSdfGrid(mesh, x, y, w, h, argb, argb, argb, argb,
                    UiRect.of(x, y, w, h), (float) radius, softness, stroke, 0.0f);
        } else {
            mesh.ensureQuadCapacity();
            int i1 = mesh.vec2(x, y).local2(x, y).color(r, g, b, a).vec4(x, y, w, h).vec4((float) radius, softness, stroke, 0f).next();
            int i2 = mesh.vec2(x, y + h).local2(x, y + h).color(r, g, b, a).vec4(x, y, w, h).vec4((float) radius, softness, stroke, 0f).next();
            int i3 = mesh.vec2(x + w, y + h).local2(x + w, y + h).color(r, g, b, a).vec4(x, y, w, h).vec4((float) radius, softness, stroke, 0f).next();
            int i4 = mesh.vec2(x + w, y).local2(x + w, y).color(r, g, b, a).vec4(x, y, w, h).vec4((float) radius, softness, stroke, 0f).next();
            mesh.quad(i1, i2, i3, i4);
        }

        endAutoBatch(auto);
    }

    public void arcStroke(double cx, double cy, double radius, double thickness,
                          float startAngleDeg, float endAngleDeg, int argb) {
        arcStroke(cx, cy, radius, thickness, startAngleDeg, endAngleDeg, 1.1f, argb);
    }

    public void arcStroke(double cx, double cy, double radius, double thickness,
                          float startAngleDeg, float endAngleDeg, float softness, int argb) {
        arcStrokeQuad(cx, cy, radius, thickness, startAngleDeg, endAngleDeg, softness, true,
                argb, argb, argb, argb);
    }

    public void arcStrokeFlat(double cx, double cy, double radius, double thickness,
                              float startAngleDeg, float endAngleDeg, float softness, int argb) {
        arcStrokeQuad(cx, cy, radius, thickness, startAngleDeg, endAngleDeg, softness, false,
                argb, argb, argb, argb);
    }

    public void arcStrokeFlat(double cx, double cy, double radius, double thickness,
                              float startAngleDeg, float endAngleDeg, int argb) {
        arcStrokeFlat(cx, cy, radius, thickness, startAngleDeg, endAngleDeg, 0.0f, argb);
    }

    public void arcStrokeGradient(double cx, double cy, double radius, double thickness,
                                  float startAngleDeg, float endAngleDeg, float softness,
                                  int startArgb, int endArgb, float angleDeg) {
        arcStrokeGradient(cx, cy, radius, thickness, startAngleDeg, endAngleDeg, softness,
                startArgb, endArgb, angleDeg, 0.0f);
    }

    public void arcStrokeGradient(double cx, double cy, double radius, double thickness,
                                  float startAngleDeg, float endAngleDeg,
                                  int startArgb, int endArgb, float angleDeg) {
        arcStrokeGradient(cx, cy, radius, thickness, startAngleDeg, endAngleDeg, 0.0f,
                startArgb, endArgb, angleDeg, 0.0f);
    }

    public void arcStrokeGradient(double cx, double cy, double radius, double thickness,
                                  float startAngleDeg, float endAngleDeg,
                                  int startArgb, int endArgb, float angleDeg, float offsetPx) {
        arcStrokeGradient(cx, cy, radius, thickness, startAngleDeg, endAngleDeg, 0.0f,
                startArgb, endArgb, angleDeg, offsetPx);
    }

    public void arcStrokeGradient(double cx, double cy, double radius, double thickness,
                                  float startAngleDeg, float endAngleDeg, float softness,
                                  int startArgb, int endArgb, float angleDeg, float offsetPx) {
        double outerRadius = radius + Math.max(0.0, thickness) * 0.5 + softness;
        computeLinearGradientColors((float) (outerRadius * 2.0), (float) (outerRadius * 2.0),
                startArgb, endArgb, angleDeg, offsetPx, gradientTmp);
        arcStrokeQuad(cx, cy, radius, thickness, startAngleDeg, endAngleDeg, softness, true,
                gradientTmp[0], gradientTmp[1], gradientTmp[2], gradientTmp[3]);
    }

    public void arcStrokeQuad(double cx, double cy, double radius, double thickness,
                              float startAngleDeg, float endAngleDeg, float softness,
                              int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        arcStrokeQuad(cx, cy, radius, thickness, startAngleDeg, endAngleDeg, softness, true,
                cTopLeft, cTopRight, cBottomRight, cBottomLeft);
    }

    public void arcStrokeQuad(double cx, double cy, double radius, double thickness,
                              float startAngleDeg, float endAngleDeg, float softness, boolean caps,
                              int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        float start = normalizeDegrees(startAngleDeg);
        float end = normalizeDegrees(endAngleDeg);
        float sweep = end - start;
        if (sweep <= 0.0f) {
            sweep += 360.0f;
        }
        if (sweep <= 0.001f) {
            return;
        }

        shapeStroke(UiShape.arc(cx, cy, radius, start, end), UiPaint.corners(cTopLeft, cTopRight, cBottomRight, cBottomLeft), UiStroke.of(thickness));
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.ARC, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureQuadCapacity();

        float stroke = (float) Math.max(0.0, thickness);
        double outerRadius = radius + stroke * 0.5 + softness;
        double x = cx - outerRadius;
        double y = cy - outerRadius;
        double w = outerRadius * 2.0;
        double h = outerRadius * 2.0;

        int tlA = (cTopLeft >>> 24) & 0xFF;
        int tlR = (cTopLeft >>> 16) & 0xFF;
        int tlG = (cTopLeft >>> 8) & 0xFF;
        int tlB = cTopLeft & 0xFF;
        int trA = (cTopRight >>> 24) & 0xFF;
        int trR = (cTopRight >>> 16) & 0xFF;
        int trG = (cTopRight >>> 8) & 0xFF;
        int trB = cTopRight & 0xFF;
        int brA = (cBottomRight >>> 24) & 0xFF;
        int brR = (cBottomRight >>> 16) & 0xFF;
        int brG = (cBottomRight >>> 8) & 0xFF;
        int brB = cBottomRight & 0xFF;
        int blA = (cBottomLeft >>> 24) & 0xFF;
        int blR = (cBottomLeft >>> 16) & 0xFF;
        int blG = (cBottomLeft >>> 8) & 0xFF;
        int blB = cBottomLeft & 0xFF;

        int i1 = mesh.vec2(x, y).local2(x, y).color(tlR, tlG, tlB, tlA).vec4(x, y, w, h)
                .vec4((float) radius, softness, stroke, start).vec4(end, caps ? 1f : 0f, 0f, 0f).next();
        int i2 = mesh.vec2(x, y + h).local2(x, y + h).color(blR, blG, blB, blA).vec4(x, y, w, h)
                .vec4((float) radius, softness, stroke, start).vec4(end, caps ? 1f : 0f, 0f, 0f).next();
        int i3 = mesh.vec2(x + w, y + h).local2(x + w, y + h).color(brR, brG, brB, brA).vec4(x, y, w, h)
                .vec4((float) radius, softness, stroke, start).vec4(end, caps ? 1f : 0f, 0f, 0f).next();
        int i4 = mesh.vec2(x + w, y).local2(x + w, y).color(trR, trG, trB, trA).vec4(x, y, w, h)
                .vec4((float) radius, softness, stroke, start).vec4(end, caps ? 1f : 0f, 0f, 0f).next();
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void orbizRing(double cx, double cy,
                          double radius, double thickness,
                          float startAngleDeg, float sweepAngleDeg,
                          float softness, float glowRadius, float glowStrength,
                          int primaryArgb, int secondaryArgb,
                          int colorMode, float angularOffset, float angularCycles,
                          float trackAlpha, float bodyAlpha, float headBoost,
                          float trackGlow, boolean caps) {
        if (textured) {
            throw new IllegalStateException("Orbiz ring drawing is supported only on Renderer2D.COLOR.");
        }
        if (radius <= 0.0 || thickness <= 0.0) return;
        if (((primaryArgb >>> 24) & 0xFF) <= 0 && ((secondaryArgb >>> 24) & 0xFF) <= 0) return;

        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.ORBIZ_RING, null, null);
        if (batch == null) {
            endAutoBatch(auto);
            return;
        }
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureQuadCapacity();

        int a = (primaryArgb >>> 24) & 0xFF;
        int r = (primaryArgb >>> 16) & 0xFF;
        int g = (primaryArgb >>> 8) & 0xFF;
        int b = primaryArgb & 0xFF;

        float sr = ((secondaryArgb >>> 16) & 0xFF) / 255.0f;
        float sg = ((secondaryArgb >>> 8) & 0xFF) / 255.0f;
        float sb = (secondaryArgb & 0xFF) / 255.0f;
        float sa = ((secondaryArgb >>> 24) & 0xFF) / 255.0f;

        float stroke = (float) Math.max(0.0, thickness);
        float ringRadius = (float) Math.max(0.0, radius);
        float soft = Math.max(0.0f, softness);
        float glow = Math.max(0.0f, glowRadius);
        float expand = stroke * 0.5f + soft + glow + 1.0f;
        double outerRadius = radius + expand;
        double x = cx - outerRadius;
        double y = cy - outerRadius;
        double w = outerRadius * 2.0;
        double h = outerRadius * 2.0;
        double ringX = cx - radius;
        double ringY = cy - radius;
        double ringSize = radius * 2.0;

        float start = normalizeDegrees(startAngleDeg);
        float sweep = Mth.clamp(sweepAngleDeg, 0.0f, 360.0f);
        float offset = angularOffset - (float) Math.floor(angularOffset);
        float cycles = Math.max(0.001f, angularCycles);
        float mode = Math.max(0, colorMode);
        float track = Mth.clamp(trackAlpha, 0.0f, 1.0f);
        float body = Mth.clamp(bodyAlpha, 0.0f, 1.0f);
        float glowPower = Math.max(0.0f, glowStrength);
        float head = Math.max(0.0f, headBoost);
        float trackGlowAmount = Mth.clamp(trackGlow, 0.0f, 1.0f);

        int i1 = mesh.vec2(x, y).local2(x, y).color(r, g, b, a)
                .vec4(ringX, ringY, ringSize, ringSize)
                .vec4(ringRadius, soft, stroke, start)
                .vec4(sweep, glow, glowPower, mode)
                .vec4(sr, sg, sb, sa)
                .vec4(offset, cycles, 0.0f, caps ? 1.0f : 0.0f)
                .vec4(track, body, head, trackGlowAmount)
                .next();
        int i2 = mesh.vec2(x, y + h).local2(x, y + h).color(r, g, b, a)
                .vec4(ringX, ringY, ringSize, ringSize)
                .vec4(ringRadius, soft, stroke, start)
                .vec4(sweep, glow, glowPower, mode)
                .vec4(sr, sg, sb, sa)
                .vec4(offset, cycles, 0.0f, caps ? 1.0f : 0.0f)
                .vec4(track, body, head, trackGlowAmount)
                .next();
        int i3 = mesh.vec2(x + w, y + h).local2(x + w, y + h).color(r, g, b, a)
                .vec4(ringX, ringY, ringSize, ringSize)
                .vec4(ringRadius, soft, stroke, start)
                .vec4(sweep, glow, glowPower, mode)
                .vec4(sr, sg, sb, sa)
                .vec4(offset, cycles, 0.0f, caps ? 1.0f : 0.0f)
                .vec4(track, body, head, trackGlowAmount)
                .next();
        int i4 = mesh.vec2(x + w, y).local2(x + w, y).color(r, g, b, a)
                .vec4(ringX, ringY, ringSize, ringSize)
                .vec4(ringRadius, soft, stroke, start)
                .vec4(sweep, glow, glowPower, mode)
                .vec4(sr, sg, sb, sa)
                .vec4(offset, cycles, 0.0f, caps ? 1.0f : 0.0f)
                .vec4(track, body, head, trackGlowAmount)
                .next();
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void roundedRectMasked(double x, double y, double w, double h,
                                  double maskX, double maskY, double maskW, double maskH,
                                  float radius, float softness, int argb) {
        roundedRectMaskedQuad(x, y, w, h, maskX, maskY, maskW, maskH, radius, softness, argb, argb, argb, argb);
    }

    public void roundedRectStroke(double x, double y, double w, double h,
                                  float radius, float softness, float thickness, int argb) {
        roundedRectStrokeGradientQuad(x, y, w, h, radius, softness, thickness,
                argb, argb, argb, argb);
    }

    public void roundedRectStroke(double x, double y, double w, double h,
                                  float radius, float thickness, int argb) {
        roundedRectStroke(x, y, w, h, radius, 0.0f, thickness, argb);
    }

    public void roundedRectStrokeAngularGradient(double x, double y, double w, double h,
                                                 float radius, float softness, float thickness,
                                                 int startArgb, int endArgb, float offset) {
        shapeStroke(UiShape.roundedRect(x, y, w, h, radius), UiPaint.solid(startArgb), UiStroke.of(thickness));
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.ROUNDED_STROKE_ANGULAR, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureQuadCapacity();

        int sa = (startArgb >>> 24) & 0xFF;
        int sr = (startArgb >>> 16) & 0xFF;
        int sg = (startArgb >>> 8) & 0xFF;
        int sb = startArgb & 0xFF;
        float ea = ((endArgb >>> 24) & 0xFF) / 255.0f;
        float er = ((endArgb >>> 16) & 0xFF) / 255.0f;
        float eg = ((endArgb >>> 8) & 0xFF) / 255.0f;
        float eb = (endArgb & 0xFF) / 255.0f;
        float clampedRadius = clampRoundedRadius(radius, w, h);
        float stroke = Math.max(0.0f, thickness);
        float phase = offset - (float) Math.floor(offset);

        int i1 = mesh.vec2(x, y).local2(x, y).color(sr, sg, sb, sa).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, stroke, phase).vec4(er, eg, eb, ea).next();
        int i2 = mesh.vec2(x, y + h).local2(x, y + h).color(sr, sg, sb, sa).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, stroke, phase).vec4(er, eg, eb, ea).next();
        int i3 = mesh.vec2(x + w, y + h).local2(x + w, y + h).color(sr, sg, sb, sa).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, stroke, phase).vec4(er, eg, eb, ea).next();
        int i4 = mesh.vec2(x + w, y).local2(x + w, y).color(sr, sg, sb, sa).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, stroke, phase).vec4(er, eg, eb, ea).next();
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void roundedRectStrokeCorners(double x, double y, double w, double h,
                                         float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                         float softness, float thickness, int argb) {
        shapeStroke(UiShape.roundedRect(x, y, w, h, radiusTL, radiusTR, radiusBR, radiusBL), UiPaint.solid(argb), UiStroke.of(thickness));
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.ROUNDED_STROKE_CORNERS, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureQuadCapacity();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        normalizeCornerRadii(w, h, radiusTL, radiusTR, radiusBR, radiusBL, cornerRadiiTmp);
        float stroke = Math.max(0.0f, thickness);

        int i1 = mesh.vec2(x, y).local2(x, y).color(r, g, b, a).vec4(x, y, w, h).vec4(cornerRadiiTmp[0], cornerRadiiTmp[1], cornerRadiiTmp[2], cornerRadiiTmp[3]).vec4(0.0f, stroke, 0f, 0f).next();
        int i2 = mesh.vec2(x, y + h).local2(x, y + h).color(r, g, b, a).vec4(x, y, w, h).vec4(cornerRadiiTmp[0], cornerRadiiTmp[1], cornerRadiiTmp[2], cornerRadiiTmp[3]).vec4(0.0f, stroke, 0f, 0f).next();
        int i3 = mesh.vec2(x + w, y + h).local2(x + w, y + h).color(r, g, b, a).vec4(x, y, w, h).vec4(cornerRadiiTmp[0], cornerRadiiTmp[1], cornerRadiiTmp[2], cornerRadiiTmp[3]).vec4(0.0f, stroke, 0f, 0f).next();
        int i4 = mesh.vec2(x + w, y).local2(x + w, y).color(r, g, b, a).vec4(x, y, w, h).vec4(cornerRadiiTmp[0], cornerRadiiTmp[1], cornerRadiiTmp[2], cornerRadiiTmp[3]).vec4(0.0f, stroke, 0f, 0f).next();
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void roundedRectStrokeCorners(double x, double y, double w, double h,
                                         float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                         float thickness, int argb) {
        roundedRectStrokeCorners(x, y, w, h, radiusTL, radiusTR, radiusBR, radiusBL, 0.0f, thickness, argb);
    }

    public void roundedRectCorners(double x, double y, double w, double h,
                                   float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                   float softness, int argb) {
        roundedRectCornersQuad(x, y, w, h,
                radiusTL, radiusTR, radiusBR, radiusBL,
                0.0f,
                argb, argb, argb, argb);
    }

    public void roundedRectCorners(double x, double y, double w, double h,
                                   float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                   int argb) {
        roundedRectCorners(x, y, w, h, radiusTL, radiusTR, radiusBR, radiusBL, 0.0f, argb);
    }

    public void roundedRectCornersQuad(double x, double y, double w, double h,
                                       float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                       float softness,
                                       int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        shape(UiShape.roundedRect(x, y, w, h, radiusTL, radiusTR, radiusBR, radiusBL), UiPaint.corners(cTopLeft, cTopRight, cBottomRight, cBottomLeft));
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.ROUNDED_CORNERS, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        int tlA = (cTopLeft >>> 24) & 0xFF;
        int tlR = (cTopLeft >>> 16) & 0xFF;
        int tlG = (cTopLeft >>> 8) & 0xFF;
        int tlB = cTopLeft & 0xFF;
        int trA = (cTopRight >>> 24) & 0xFF;
        int trR = (cTopRight >>> 16) & 0xFF;
        int trG = (cTopRight >>> 8) & 0xFF;
        int trB = cTopRight & 0xFF;
        int brA = (cBottomRight >>> 24) & 0xFF;
        int brR = (cBottomRight >>> 16) & 0xFF;
        int brG = (cBottomRight >>> 8) & 0xFF;
        int brB = cBottomRight & 0xFF;
        int blA = (cBottomLeft >>> 24) & 0xFF;
        int blR = (cBottomLeft >>> 16) & 0xFF;
        int blG = (cBottomLeft >>> 8) & 0xFF;
        int blB = cBottomLeft & 0xFF;
        normalizeCornerRadii(w, h, radiusTL, radiusTR, radiusBR, radiusBL, cornerRadiiTmp);

        int i1 = mesh.vec2(x, y).local2(x, y).color(tlR, tlG, tlB, tlA).vec4(x, y, w, h).vec4(cornerRadiiTmp[0], cornerRadiiTmp[1], cornerRadiiTmp[2], cornerRadiiTmp[3]).vec4(0.0f, 0f, 0f, 0f).next();
        int i2 = mesh.vec2(x, y + h).local2(x, y + h).color(blR, blG, blB, blA).vec4(x, y, w, h).vec4(cornerRadiiTmp[0], cornerRadiiTmp[1], cornerRadiiTmp[2], cornerRadiiTmp[3]).vec4(0.0f, 0f, 0f, 0f).next();
        int i3 = mesh.vec2(x + w, y + h).local2(x + w, y + h).color(brR, brG, brB, brA).vec4(x, y, w, h).vec4(cornerRadiiTmp[0], cornerRadiiTmp[1], cornerRadiiTmp[2], cornerRadiiTmp[3]).vec4(0.0f, 0f, 0f, 0f).next();
        int i4 = mesh.vec2(x + w, y).local2(x + w, y).color(trR, trG, trB, trA).vec4(x, y, w, h).vec4(cornerRadiiTmp[0], cornerRadiiTmp[1], cornerRadiiTmp[2], cornerRadiiTmp[3]).vec4(0.0f, 0f, 0f, 0f).next();
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void roundedRectCornersQuad(double x, double y, double w, double h,
                                       float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                       int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        roundedRectCornersQuad(x, y, w, h, radiusTL, radiusTR, radiusBR, radiusBL, 0.0f,
                cTopLeft, cTopRight, cBottomRight, cBottomLeft);
    }

    public void roundedRectGradient(double x, double y, double w, double h,
                                    float radius, float softness,
                                    int startArgb, int endArgb, float angleDeg) {
        roundedRectGradient(x, y, w, h, radius, 0.0f, startArgb, endArgb, angleDeg, 0.0f);
    }

    public void roundedRectGradient(double x, double y, double w, double h,
                                    float radius, int startArgb, int endArgb, float angleDeg) {
        roundedRectGradient(x, y, w, h, radius, 0.0f, startArgb, endArgb, angleDeg, 0.0f);
    }

    public void roundedRectGradient(double x, double y, double w, double h,
                                    float radius, float softness,
                                    int startArgb, int endArgb, float angleDeg, float offsetPx) {
        computeLinearGradientColors((float) w, (float) h, startArgb, endArgb, angleDeg, offsetPx, gradientTmp);
        roundedRectGradientQuad(x, y, w, h, radius, 0.0f, gradientTmp[0], gradientTmp[1], gradientTmp[2], gradientTmp[3]);
    }

    public void roundedRectGradient(double x, double y, double w, double h,
                                    float radius,
                                    int startArgb, int endArgb, float angleDeg, float offsetPx) {
        roundedRectGradient(x, y, w, h, radius, 0.0f, startArgb, endArgb, angleDeg, offsetPx);
    }

    /**
     * Draws a progress segment clipped by the full rounded bar outline.
     *
     * <p>Unlike drawing a rounded rectangle with the current fill width, this keeps the moving
     * progress edge straight and lets only the fixed outer bar bounds define rounded ends. That
     * prevents small health values from becoming a pill/capsule and prevents the fill from leaking
     * outside the left/right rounded shell when the progress edge crosses an outer corner.</p>
     */
    public void roundedProgressRectGradient(double x, double y, double w, double h,
                                            float radius, float softness,
                                            float progress,
                                            boolean fromRight,
                                            int startArgb, int endArgb,
                                            float angleDeg, float offsetPx) {
        if (w <= 0.0 || h <= 0.0) return;
        float p = Mth.clamp(progress, 0.0f, 1.0f);
        if (p <= 0.0001f) return;
        if (p >= 0.9999f) {
            roundedRectGradient(x, y, w, h, radius, softness, startArgb, endArgb, angleDeg, offsetPx);
            return;
        }

        double fillW = Math.max(0.0, Math.min(w, w * p));
        double fillX = fromRight ? x + w - fillW : x;
        double clipX = fromRight ? fillX : x + fillW;
        int count = buildRoundedProgressRect(progressShapeTmp, rectShapeTmp, x, y, w, h, radius, clipX, fromRight);
        if (count < 3) return;
        polygonGradient(progressShapeTmp, count, fillX, y, fillW, h, startArgb, endArgb, angleDeg, offsetPx);
    }

    public void roundedProgressRectGradient(double x, double y, double w, double h,
                                            float radius,
                                            float progress,
                                            boolean fromRight,
                                            int startArgb, int endArgb,
                                            float angleDeg, float offsetPx) {
        roundedProgressRectGradient(x, y, w, h, radius, 0.0f, progress, fromRight,
                startArgb, endArgb, angleDeg, offsetPx);
    }

    public void roundedSmokeFill(double x, double y, double w, double h,
                                 float radius, float softness,
                                 float fillRatio,
                                 boolean fromRight,
                                 int firstArgb,
                                 int secondArgb,
                                 int thirdArgb,
                                 float time,
                                 float smokeScale,
                                 float smokeMix,
                                 int octaves,
                                 float flowX,
                                 float flowY,
                                 float intensity) {
        if (w <= 0.0 || h <= 0.0) return;
        float fill = Mth.clamp(fillRatio, 0.0f, 1.0f);
        if (fill <= 0.0001f) return;

        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.ROUNDED_FILL_SMOKE, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureQuadCapacity();

        int a0 = (firstArgb >>> 24) & 0xFF;
        int r0 = (firstArgb >>> 16) & 0xFF;
        int g0 = (firstArgb >>> 8) & 0xFF;
        int b0 = firstArgb & 0xFF;

        float r1 = ((secondArgb >>> 16) & 0xFF) / 255.0f;
        float g1 = ((secondArgb >>> 8) & 0xFF) / 255.0f;
        float b1 = (secondArgb & 0xFF) / 255.0f;
        float a1 = ((secondArgb >>> 24) & 0xFF) / 255.0f;

        float r2 = ((thirdArgb >>> 16) & 0xFF) / 255.0f;
        float g2 = ((thirdArgb >>> 8) & 0xFF) / 255.0f;
        float b2 = (thirdArgb & 0xFF) / 255.0f;
        float a2 = ((thirdArgb >>> 24) & 0xFF) / 255.0f;

        float fromRightFlag = fromRight ? 1.0f : 0.0f;
        float safeScale = Math.max(0.1f, smokeScale);
        float safeMix = Mth.clamp(smokeMix, 0.0f, 1.0f);
        float safeOctaves = Mth.clamp(octaves, 1, 8);
        float safeIntensity = Math.max(0.0f, intensity);

        int i1 = mesh.vec2(x, y).local2(x, y).color(r0, g0, b0, a0)
                .vec4((float) x, (float) y, (float) w, (float) h)
                .vec4(radius, softness, fill, fromRightFlag)
                .vec4(r1, g1, b1, a1)
                .vec4(r2, g2, b2, a2)
                .vec4(time, safeScale, safeMix, safeOctaves)
                .vec4(flowX, flowY, safeIntensity, 0.0f)
                .next();
        int i2 = mesh.vec2(x, y + h).local2(x, y + h).color(r0, g0, b0, a0)
                .vec4((float) x, (float) y, (float) w, (float) h)
                .vec4(radius, softness, fill, fromRightFlag)
                .vec4(r1, g1, b1, a1)
                .vec4(r2, g2, b2, a2)
                .vec4(time, safeScale, safeMix, safeOctaves)
                .vec4(flowX, flowY, safeIntensity, 0.0f)
                .next();
        int i3 = mesh.vec2(x + w, y + h).local2(x + w, y + h).color(r0, g0, b0, a0)
                .vec4((float) x, (float) y, (float) w, (float) h)
                .vec4(radius, softness, fill, fromRightFlag)
                .vec4(r1, g1, b1, a1)
                .vec4(r2, g2, b2, a2)
                .vec4(time, safeScale, safeMix, safeOctaves)
                .vec4(flowX, flowY, safeIntensity, 0.0f)
                .next();
        int i4 = mesh.vec2(x + w, y).local2(x + w, y).color(r0, g0, b0, a0)
                .vec4((float) x, (float) y, (float) w, (float) h)
                .vec4(radius, softness, fill, fromRightFlag)
                .vec4(r1, g1, b1, a1)
                .vec4(r2, g2, b2, a2)
                .vec4(time, safeScale, safeMix, safeOctaves)
                .vec4(flowX, flowY, safeIntensity, 0.0f)
                .next();
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void roundedSmokeFill(double x, double y, double w, double h,
                                 float radius,
                                 float fillRatio,
                                 boolean fromRight,
                                 int firstArgb,
                                 int secondArgb,
                                 int thirdArgb,
                                 float time,
                                 float smokeScale,
                                 float smokeMix,
                                 int octaves,
                                 float flowX,
                                 float flowY,
                                 float intensity) {
        roundedSmokeFill(x, y, w, h, radius, 0.0f, fillRatio, fromRight,
                firstArgb, secondArgb, thirdArgb, time, smokeScale, smokeMix, octaves, flowX, flowY, intensity);
    }

    public void roundedRectStrokeGradient(double x, double y, double w, double h,
                                          float radius, float softness, float thickness,
                                          int startArgb, int endArgb, float angleDeg) {
        roundedRectStrokeGradient(x, y, w, h, radius, 0.0f, thickness, startArgb, endArgb, angleDeg, 0.0f);
    }

    public void roundedRectStrokeGradient(double x, double y, double w, double h,
                                          float radius, float thickness,
                                          int startArgb, int endArgb, float angleDeg) {
        roundedRectStrokeGradient(x, y, w, h, radius, 0.0f, thickness, startArgb, endArgb, angleDeg, 0.0f);
    }

    public void roundedRectStrokeGradient(double x, double y, double w, double h,
                                          float radius, float softness, float thickness,
                                          int startArgb, int endArgb, float angleDeg, float offsetPx) {
        computeLinearGradientColors((float) w, (float) h, startArgb, endArgb, angleDeg, offsetPx, gradientTmp);
        roundedRectStrokeGradientQuad(x, y, w, h, radius, 0.0f, thickness, gradientTmp[0], gradientTmp[1], gradientTmp[2], gradientTmp[3]);
    }

    public void roundedRectStrokeGradient(double x, double y, double w, double h,
                                          float radius, float thickness,
                                          int startArgb, int endArgb, float angleDeg, float offsetPx) {
        roundedRectStrokeGradient(x, y, w, h, radius, 0.0f, thickness, startArgb, endArgb, angleDeg, offsetPx);
    }

    public void roundedRectGlow(double x, double y, double w, double h,
                                float radius, float softness, float glow, int argb) {
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.ROUNDED_GLOW, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureQuadCapacity();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        float clampedRadius = clampRoundedRadius(radius, w, h);

        double expand = glow;
        double gx = x - expand;
        double gy = y - expand;
        double gw = w + expand * 2.0f;
        double gh = h + expand * 2.0f;

        int i1 = mesh.vec2(gx, gy).local2(gx, gy).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, glow, 0f).next();
        int i2 = mesh.vec2(gx, gy + gh).local2(gx, gy + gh).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, glow, 0f).next();
        int i3 = mesh.vec2(gx + gw, gy + gh).local2(gx + gw, gy + gh).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, glow, 0f).next();
        int i4 = mesh.vec2(gx + gw, gy).local2(gx + gw, gy).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, glow, 0f).next();
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void roundedRectShadow(double x, double y, double w, double h,
                                  float radius, float softness, float spread, int argb) {
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.ROUNDED_SHADOW, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureQuadCapacity();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        float clampedRadius = clampRoundedRadius(radius, w, h);

        double expandX = 2.0f;
        double expandY = spread + 2.0f;
        double gx = x - expandX;
        double gw = w + expandX * 2.0f;
        double gy = y;
        double gh = h + expandY;

        int i1 = mesh.vec2(gx, gy).local2(gx, gy).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, spread, 0f).next();
        int i2 = mesh.vec2(gx, gy + gh).local2(gx, gy + gh).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, spread, 0f).next();
        int i3 = mesh.vec2(gx + gw, gy + gh).local2(gx + gw, gy + gh).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, spread, 0f).next();
        int i4 = mesh.vec2(gx + gw, gy).local2(gx + gw, gy).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, spread, 0f).next();
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void roundedRectSoftShadow(double x, double y, double w, double h,
                                      float radius, float blur, float innerAlpha, int argb) {
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.ROUNDED_SOFT_SHADOW, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureQuadCapacity();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double expand = blur * 2.0;
        double sx = x - expand;
        double sy = y - expand;
        double sw = w + expand * 2.0;
        double sh = h + expand * 2.0;

        int i1 = mesh.vec2(sx, sy).local2(sx, sy).color(r, g, b, a).vec4(x, y, w, h).vec4(radius, blur, innerAlpha, 0f).next();
        int i2 = mesh.vec2(sx, sy + sh).local2(sx, sy + sh).color(r, g, b, a).vec4(x, y, w, h).vec4(radius, blur, innerAlpha, 0f).next();
        int i3 = mesh.vec2(sx + sw, sy + sh).local2(sx + sw, sy + sh).color(r, g, b, a).vec4(x, y, w, h).vec4(radius, blur, innerAlpha, 0f).next();
        int i4 = mesh.vec2(sx + sw, sy).local2(sx + sw, sy).color(r, g, b, a).vec4(x, y, w, h).vec4(radius, blur, innerAlpha, 0f).next();
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void squircleSoftShadow(double x, double y, double w, double h,
                                   UiSquircleProfile profile, float blur, float innerAlpha, int argb) {
        UiSquircleProfile safe = profile != null ? profile : UiSquircleProfile.STANDARD;
        squircleSoftShadow(x, y, w, h, safe.exponent(), blur, innerAlpha, argb);
    }

    public void squircleSoftShadow(double x, double y, double w, double h,
                                   float exponent, float blur, float innerAlpha, int argb) {
        UiBoxShape shape = UiBoxShape.squircle(x, y, w, h, exponent);
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.ROUNDED_SOFT_SHADOW, null, null);
        if (batch == null) {
            endAutoBatch(auto);
            return;
        }
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;
        mesh.ensureQuadCapacity();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        double expand = blur * 2.0;
        double sx = x - expand;
        double sy = y - expand;
        double sw = w + expand * 2.0;
        double sh = h + expand * 2.0;
        float encodedShape = -shape.squircleExponent();

        int i1 = mesh.vec2(sx, sy).local2(sx, sy).color(r, g, b, a).vec4(x, y, w, h).vec4(encodedShape, blur, innerAlpha, 0f).next();
        int i2 = mesh.vec2(sx, sy + sh).local2(sx, sy + sh).color(r, g, b, a).vec4(x, y, w, h).vec4(encodedShape, blur, innerAlpha, 0f).next();
        int i3 = mesh.vec2(sx + sw, sy + sh).local2(sx + sw, sy + sh).color(r, g, b, a).vec4(x, y, w, h).vec4(encodedShape, blur, innerAlpha, 0f).next();
        int i4 = mesh.vec2(sx + sw, sy).local2(sx + sw, sy).color(r, g, b, a).vec4(x, y, w, h).vec4(encodedShape, blur, innerAlpha, 0f).next();
        mesh.quad(i1, i2, i3, i4);
        endAutoBatch(auto);
    }

    public void radialGlowMasked(double x, double y, double w, double h,
                                 float radius, float softness,
                                 float glowRadius, float cx, float cy,
                                 int argb) {
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.RADIAL_GLOW, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureQuadCapacity();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        float clampedRadius = clampRoundedRadius(radius, w, h);

        int i1 = mesh.vec2(x, y).local2(x, y).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, glowRadius, 0.0f).vec4(cx, cy, 0f, 0f).next();
        int i2 = mesh.vec2(x, y + h).local2(x, y + h).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, glowRadius, 0.0f).vec4(cx, cy, 0f, 0f).next();
        int i3 = mesh.vec2(x + w, y + h).local2(x + w, y + h).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, glowRadius, 0.0f).vec4(cx, cy, 0f, 0f).next();
        int i4 = mesh.vec2(x + w, y).local2(x + w, y).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, glowRadius, 0.0f).vec4(cx, cy, 0f, 0f).next();
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void roundedRectGradientQuad(double x, double y, double w, double h,
                                        float radius, float softness,
                                        int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        roundedRectMaskedQuad(x, y, w, h, x, y, w, h, radius, softness, cTopLeft, cTopRight, cBottomRight, cBottomLeft);
    }

    public void roundedRectGradientQuad(double x, double y, double w, double h,
                                        float radius,
                                        int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        roundedRectGradientQuad(x, y, w, h, radius, 0.0f, cTopLeft, cTopRight, cBottomRight, cBottomLeft);
    }

    public void roundedRectMaskedQuad(double x, double y, double w, double h,
                                      double maskX, double maskY, double maskW, double maskH,
                                      float radius, float softness,
                                      int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        shape(UiShape.roundedRect(maskX, maskY, maskW, maskH, radius), UiPaint.corners(cTopLeft, cTopRight, cBottomRight, cBottomLeft));
        boolean auto = beginAutoBatch();
        boolean warped = RenderWarpStack.active();
        DrawBatch batch = UI_BATCHER.getOrCreate(warped ? UiBatchType.SHAPE_WARPED : UiBatchType.SHAPE, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        float clampedRadius = clampRoundedRadius(radius, maskW, maskH);
        UiFastShapeParams params = UiFastShapeParams.rounded(clampedRadius, UiStroke.NONE, true, softness);
        UiRect bounds = UiRect.of(maskX, maskY, maskW, maskH);

        int i1;
        int i2;
        int i3;
        int i4;
        if (warped) {
            appendWarpedSdfGrid(mesh, x, y, w, h,
                    cTopLeft, cTopRight, cBottomRight, cBottomLeft,
                    bounds, params.kind(), params.shape(), params.strokeWidth(), params.flags());
            endAutoBatch(auto);
            return;
        } else {
            mesh.ensureQuadCapacity();
            i1 = appendShapeVertex(mesh, x, y, cTopLeft, bounds, params);
            i2 = appendShapeVertex(mesh, x, y + h, cBottomLeft, bounds, params);
            i3 = appendShapeVertex(mesh, x + w, y + h, cBottomRight, bounds, params);
            i4 = appendShapeVertex(mesh, x + w, y, cTopRight, bounds, params);
        }
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void roundedRectStrokeGradientQuad(double x, double y, double w, double h,
                                              float radius, float softness, float thickness,
                                              int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        shapeStroke(UiShape.roundedRect(x, y, w, h, radius), UiPaint.corners(cTopLeft, cTopRight, cBottomRight, cBottomLeft), UiStroke.of(thickness));
        boolean auto = beginAutoBatch();
        boolean warped = RenderWarpStack.active();
        DrawBatch batch = UI_BATCHER.getOrCreate(warped ? UiBatchType.SHAPE_WARPED : UiBatchType.SHAPE, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        float clampedRadius = clampRoundedRadius(radius, w, h);
        float stroke = Math.max(0.0f, thickness);
        UiFastShapeParams params = UiFastShapeParams.rounded(clampedRadius, UiStroke.of(stroke), false, softness);
        UiRect bounds = UiRect.of(x, y, w, h);

        int i1;
        int i2;
        int i3;
        int i4;
        if (warped) {
            appendWarpedSdfGrid(mesh, x, y, w, h,
                    cTopLeft, cTopRight, cBottomRight, cBottomLeft,
                    bounds, params.kind(), params.shape(), params.strokeWidth(), params.flags());
            endAutoBatch(auto);
            return;
        } else {
            mesh.ensureQuadCapacity();
            i1 = appendShapeVertex(mesh, x, y, cTopLeft, bounds, params);
            i2 = appendShapeVertex(mesh, x, y + h, cBottomLeft, bounds, params);
            i3 = appendShapeVertex(mesh, x + w, y + h, cBottomRight, bounds, params);
            i4 = appendShapeVertex(mesh, x + w, y, cTopRight, bounds, params);
        }
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void roundedRectStrokeGradientQuad(double x, double y, double w, double h,
                                              float radius, float thickness,
                                              int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        roundedRectStrokeGradientQuad(x, y, w, h, radius, 0.0f, thickness,
                cTopLeft, cTopRight, cBottomRight, cBottomLeft);
    }

    public void roundedTexRect(double x, double y, double w, double h,
                               float radius, float softness,
                               double texX1, double texY1, double texX2, double texY2,
                               int argb, GpuTextureView samplerView, GpuSampler sampler) {
        if (!textured) {
            throw new IllegalStateException("Renderer2D is not configured for textures.");
        }
        if (samplerView == null || sampler == null) return;

        recordUi(new UiTextureDrawCommand(samplerView, sampler, UiShape.roundedRect(x, y, w, h, radius), UiPaint.solid(argb),
                (float) texX1, (float) texY1, (float) texX2, (float) texY2, false));
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.ROUNDED_TEXTURED, samplerView, sampler);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureQuadCapacity();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        float clampedRadius = clampRoundedRadius(radius, w, h);
        int i1 = mesh.vec2(x, y).raw2(texX1, texY1).local2(x, y).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, 0f, 0f).next();
        int i2 = mesh.vec2(x, y + h).raw2(texX1, texY2).local2(x, y + h).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, 0f, 0f).next();
        int i3 = mesh.vec2(x + w, y + h).raw2(texX2, texY2).local2(x + w, y + h).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, 0f, 0f).next();
        int i4 = mesh.vec2(x + w, y).raw2(texX2, texY1).local2(x + w, y).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, 0f, 0f).next();
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void roundedTexMaskRect(double x, double y, double w, double h,
                                   float radius, float softness,
                                   double texX1, double texY1, double texX2, double texY2,
                                   int argb, GpuTextureView samplerView, GpuSampler sampler) {
        if (!textured) {
            throw new IllegalStateException("Renderer2D is not configured for textures.");
        }
        if (samplerView == null || sampler == null) return;

        recordUi(new UiTextureDrawCommand(samplerView, sampler, UiShape.roundedRect(x, y, w, h, radius), UiPaint.solid(argb),
                (float) texX1, (float) texY1, (float) texX2, (float) texY2, true));
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.ROUNDED_TEXTURED_MASK, samplerView, sampler);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureQuadCapacity();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        float clampedRadius = clampRoundedRadius(radius, w, h);
        int i1 = mesh.vec2(x, y).raw2(texX1, texY1).local2(x, y).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, 0f, 0f).next();
        int i2 = mesh.vec2(x, y + h).raw2(texX1, texY2).local2(x, y + h).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, 0f, 0f).next();
        int i3 = mesh.vec2(x + w, y + h).raw2(texX2, texY2).local2(x + w, y + h).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, 0f, 0f).next();
        int i4 = mesh.vec2(x + w, y).raw2(texX2, texY1).local2(x + w, y).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, 0f, 0f).next();
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void roundedTexRect(double x, double y, double w, double h,
                               float radius, float softness, int argb,
                               Identifier textureId) {
        if (!textured) {
            throw new IllegalStateException("Renderer2D is not configured for textures.");
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || textureId == null) return;
        AbstractTexture tex = mc.getTextureManager().getTexture(textureId);
        if (tex == null) return;
        GpuTextureView view = tex.getTextureView();
        GpuSampler sampler = tex.getSampler();
        if (view == null || sampler == null) return;
        roundedTexRect(x, y, w, h, radius, 0.0f, 0, 0, 1, 1, argb, view, sampler);
    }

    public void roundedTexRect(double x, double y, double w, double h,
                               float radius, int argb,
                               Identifier textureId) {
        roundedTexRect(x, y, w, h, radius, 0.0f, argb, textureId);
    }

    public void roundedTexMaskRect(double x, double y, double w, double h,
                                   float radius, float softness, int argb,
                                   Identifier textureId) {
        if (!textured) {
            throw new IllegalStateException("Renderer2D is not configured for textures.");
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || textureId == null) return;
        AbstractTexture tex = mc.getTextureManager().getTexture(textureId);
        if (tex == null) return;
        GpuTextureView view = tex.getTextureView();
        GpuSampler sampler = tex.getSampler();
        if (view == null || sampler == null) return;
        roundedTexMaskRect(x, y, w, h, radius, 0.0f, 0, 0, 1, 1, argb, view, sampler);
    }

    public void roundedTexMaskRect(double x, double y, double w, double h,
                                   float radius, int argb,
                                   Identifier textureId) {
        roundedTexMaskRect(x, y, w, h, radius, 0.0f, argb, textureId);
    }

    public void roundedTexRect(double x, double y, double w, double h,
                               float radius, float softness,
                               double texX1, double texY1, double texX2, double texY2,
                               int argb, Identifier textureId) {
        if (!textured) {
            throw new IllegalStateException("Renderer2D is not configured for textures.");
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || textureId == null) return;
        AbstractTexture tex = mc.getTextureManager().getTexture(textureId);
        if (tex == null) return;
        GpuTextureView view = tex.getTextureView();
        GpuSampler sampler = tex.getSampler();
        if (view == null || sampler == null) return;
        roundedTexRect(x, y, w, h, radius, 0.0f, texX1, texY1, texX2, texY2, argb, view, sampler);
    }

    public void roundedTexMaskRect(double x, double y, double w, double h,
                                   float radius, float softness,
                                   double texX1, double texY1, double texX2, double texY2,
                                   int argb, Identifier textureId) {
        if (!textured) {
            throw new IllegalStateException("Renderer2D is not configured for textures.");
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || textureId == null) return;
        AbstractTexture tex = mc.getTextureManager().getTexture(textureId);
        if (tex == null) return;
        GpuTextureView view = tex.getTextureView();
        GpuSampler sampler = tex.getSampler();
        if (view == null || sampler == null) return;
        roundedTexMaskRect(x, y, w, h, radius, 0.0f, texX1, texY1, texX2, texY2, argb, view, sampler);
    }

    public void texQuad(double x, double y, double width, double height, int argb) {
        texQuad(x, y, width, height, 0, 0, 1, 1, argb);
    }

    public void quadGradient(double x, double y, double width, double height,
                             int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        quad(x, y, width, height, cTopLeft, cTopRight, cBottomRight, cBottomLeft);
    }

    public void quadGradientLinear(double x, double y, double width, double height,
                                   int startArgb, int endArgb, float angleDeg) {
        quadGradientLinear(x, y, width, height, startArgb, endArgb, angleDeg, 0.0f);
    }

    public void quadGradientLinear(double x, double y, double width, double height,
                                   int startArgb, int endArgb, float angleDeg, float offsetPx) {
        computeLinearGradientColors((float) width, (float) height, startArgb, endArgb, angleDeg, offsetPx, gradientTmp);
        quad(x, y, width, height, gradientTmp[0], gradientTmp[1], gradientTmp[2], gradientTmp[3]);
    }

    public void quadStrokeGradientLinear(double x, double y, double width, double height,
                                         float thickness,
                                         int startArgb, int endArgb, float angleDeg) {
        quadStrokeGradientLinear(x, y, width, height, thickness, startArgb, endArgb, angleDeg, 0.0f);
    }

    public void quadStrokeGradientLinear(double x, double y, double width, double height,
                                         float thickness,
                                         int startArgb, int endArgb, float angleDeg, float offsetPx) {
        roundedRectStrokeGradient(x, y, width, height, 0.0f, 0.0f, thickness, startArgb, endArgb, angleDeg, offsetPx);
    }

    public void chamferedRect(double x, double y, double width, double height, double chamfer, int argb) {
        chamferedRectQuad(x, y, width, height, chamfer, argb, argb, argb, argb);
    }

    public void chamferedRect(double x, double y, double width, double height,
                              double chamferTL, double chamferTR, double chamferBR, double chamferBL,
                              int argb) {
        chamferedRectQuad(x, y, width, height,
                chamferTL, chamferTR, chamferBR, chamferBL,
                argb, argb, argb, argb);
    }

    public void chamferedRectGradient(double x, double y, double width, double height,
                                      double chamfer, int startArgb, int endArgb, float angleDeg, float offsetPx) {
        computeLinearGradientColors((float) width, (float) height, startArgb, endArgb, angleDeg, offsetPx, gradientTmp);
        chamferedRectQuad(x, y, width, height, chamfer, gradientTmp[0], gradientTmp[1], gradientTmp[2], gradientTmp[3]);
    }

    public void chamferedRectGradient(double x, double y, double width, double height,
                                      double chamferTL, double chamferTR, double chamferBR, double chamferBL,
                                      int startArgb, int endArgb, float angleDeg, float offsetPx) {
        computeLinearGradientColors((float) width, (float) height, startArgb, endArgb, angleDeg, offsetPx, gradientTmp);
        chamferedRectQuad(x, y, width, height,
                chamferTL, chamferTR, chamferBR, chamferBL,
                gradientTmp[0], gradientTmp[1], gradientTmp[2], gradientTmp[3]);
    }

    public void chamferedRectStroke(double x, double y, double width, double height,
                                    double chamfer, double thickness, int argb) {
        chamferedRectStrokeQuad(x, y, width, height, chamfer, thickness, argb, argb, argb, argb);
    }

    public void chamferedRectStroke(double x, double y, double width, double height,
                                    double chamferTL, double chamferTR, double chamferBR, double chamferBL,
                                    double thickness, int argb) {
        chamferedRectStrokeQuad(x, y, width, height,
                chamferTL, chamferTR, chamferBR, chamferBL,
                thickness, argb, argb, argb, argb);
    }

    public void chamferedRectStrokeGradient(double x, double y, double width, double height,
                                            double chamfer, double thickness,
                                            int startArgb, int endArgb, float angleDeg, float offsetPx) {
        computeLinearGradientColors((float) width, (float) height, startArgb, endArgb, angleDeg, offsetPx, gradientTmp);
        chamferedRectStrokeQuad(x, y, width, height, chamfer, thickness,
                gradientTmp[0], gradientTmp[1], gradientTmp[2], gradientTmp[3]);
    }

    public void chamferedRectStrokeGradient(double x, double y, double width, double height,
                                            double chamferTL, double chamferTR, double chamferBR, double chamferBL,
                                            double thickness,
                                            int startArgb, int endArgb, float angleDeg, float offsetPx) {
        computeLinearGradientColors((float) width, (float) height, startArgb, endArgb, angleDeg, offsetPx, gradientTmp);
        chamferedRectStrokeQuad(x, y, width, height,
                chamferTL, chamferTR, chamferBR, chamferBL,
                thickness, gradientTmp[0], gradientTmp[1], gradientTmp[2], gradientTmp[3]);
    }

    public void chamferedRectQuad(double x, double y, double w, double h,
                                  double chamfer,
                                  int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        chamferedRectQuad(x, y, w, h,
                chamfer, chamfer, chamfer, chamfer,
                cTopLeft, cTopRight, cBottomRight, cBottomLeft);
    }

    public void chamferedRectQuad(double x, double y, double w, double h,
                                  double chamferTL, double chamferTR, double chamferBR, double chamferBL,
                                  int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        normalizeChamfers(w, h,
                (float) chamferTL,
                (float) chamferTR,
                (float) chamferBR,
                (float) chamferBL,
                cornerRadiiTmp);
        chamferedRectQuadAxes(x, y, w, h,
                cornerRadiiTmp[0], cornerRadiiTmp[0],
                cornerRadiiTmp[1], cornerRadiiTmp[1],
                cornerRadiiTmp[2], cornerRadiiTmp[2],
                cornerRadiiTmp[3], cornerRadiiTmp[3],
                cTopLeft, cTopRight, cBottomRight, cBottomLeft);
    }

    public void chamferedRectQuadAxes(double x, double y, double w, double h,
                                      double chamferTLX, double chamferTLY,
                                      double chamferTRX, double chamferTRY,
                                      double chamferBRX, double chamferBRY,
                                      double chamferBLX, double chamferBLY,
                                      int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        shape(UiShape.chamferedRectAxes(x, y, w, h, chamferTLX, chamferTLY, chamferTRX, chamferTRY, chamferBRX, chamferBRY, chamferBLX, chamferBLY),
                UiPaint.corners(cTopLeft, cTopRight, cBottomRight, cBottomLeft));
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.CHAMFERED, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureQuadCapacity();

        int tlA = (cTopLeft >>> 24) & 0xFF;
        int tlR = (cTopLeft >>> 16) & 0xFF;
        int tlG = (cTopLeft >>> 8) & 0xFF;
        int tlB = cTopLeft & 0xFF;
        int trA = (cTopRight >>> 24) & 0xFF;
        int trR = (cTopRight >>> 16) & 0xFF;
        int trG = (cTopRight >>> 8) & 0xFF;
        int trB = cTopRight & 0xFF;
        int brA = (cBottomRight >>> 24) & 0xFF;
        int brR = (cBottomRight >>> 16) & 0xFF;
        int brG = (cBottomRight >>> 8) & 0xFF;
        int brB = cBottomRight & 0xFF;
        int blA = (cBottomLeft >>> 24) & 0xFF;
        int blR = (cBottomLeft >>> 16) & 0xFF;
        int blG = (cBottomLeft >>> 8) & 0xFF;
        int blB = cBottomLeft & 0xFF;

        float tlX = clampChamferAxis(chamferTLX, w);
        float trX = clampChamferAxis(chamferTRX, w);
        float brX = clampChamferAxis(chamferBRX, w);
        float blX = clampChamferAxis(chamferBLX, w);
        float tlY = clampChamferAxis(chamferTLY, h);
        float trY = clampChamferAxis(chamferTRY, h);
        float brY = clampChamferAxis(chamferBRY, h);
        float blY = clampChamferAxis(chamferBLY, h);

        int i1 = mesh.vec2(x, y).local2(x, y).color(tlR, tlG, tlB, tlA).vec4(x, y, w, h).vec4(tlX, trX, brX, blX).vec4(tlY, trY, brY, blY).next();
        int i2 = mesh.vec2(x, y + h).local2(x, y + h).color(blR, blG, blB, blA).vec4(x, y, w, h).vec4(tlX, trX, brX, blX).vec4(tlY, trY, brY, blY).next();
        int i3 = mesh.vec2(x + w, y + h).local2(x + w, y + h).color(brR, brG, brB, brA).vec4(x, y, w, h).vec4(tlX, trX, brX, blX).vec4(tlY, trY, brY, blY).next();
        int i4 = mesh.vec2(x + w, y).local2(x + w, y).color(trR, trG, trB, trA).vec4(x, y, w, h).vec4(tlX, trX, brX, blX).vec4(tlY, trY, brY, blY).next();
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void chamferedRectStrokeQuad(double x, double y, double w, double h,
                                        double chamfer, double thickness,
                                        int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        chamferedRectStrokeQuad(x, y, w, h,
                chamfer, chamfer, chamfer, chamfer,
                thickness, cTopLeft, cTopRight, cBottomRight, cBottomLeft);
    }

    public void chamferedRectStrokeQuad(double x, double y, double w, double h,
                                        double chamferTL, double chamferTR, double chamferBR, double chamferBL,
                                        double thickness,
                                        int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
        shapeStroke(UiShape.chamferedRect(x, y, w, h, chamferTL, chamferTR, chamferBR, chamferBL),
                UiPaint.corners(cTopLeft, cTopRight, cBottomRight, cBottomLeft), UiStroke.of(thickness));
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.CHAMFERED_STROKE, null, null);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureQuadCapacity();

        int tlA = (cTopLeft >>> 24) & 0xFF;
        int tlR = (cTopLeft >>> 16) & 0xFF;
        int tlG = (cTopLeft >>> 8) & 0xFF;
        int tlB = cTopLeft & 0xFF;
        int trA = (cTopRight >>> 24) & 0xFF;
        int trR = (cTopRight >>> 16) & 0xFF;
        int trG = (cTopRight >>> 8) & 0xFF;
        int trB = cTopRight & 0xFF;
        int brA = (cBottomRight >>> 24) & 0xFF;
        int brR = (cBottomRight >>> 16) & 0xFF;
        int brG = (cBottomRight >>> 8) & 0xFF;
        int brB = cBottomRight & 0xFF;
        int blA = (cBottomLeft >>> 24) & 0xFF;
        int blR = (cBottomLeft >>> 16) & 0xFF;
        int blG = (cBottomLeft >>> 8) & 0xFF;
        int blB = cBottomLeft & 0xFF;
        normalizeChamfers(w, h,
                (float) chamferTL,
                (float) chamferTR,
                (float) chamferBR,
                (float) chamferBL,
                cornerRadiiTmp);
        float stroke = (float) Math.max(0.0, thickness);

        int i1 = mesh.vec2(x, y).local2(x, y).color(tlR, tlG, tlB, tlA).vec4(x, y, w, h).vec4(cornerRadiiTmp[0], cornerRadiiTmp[1], cornerRadiiTmp[2], cornerRadiiTmp[3]).vec4(stroke, 0f, 0f, 0f).next();
        int i2 = mesh.vec2(x, y + h).local2(x, y + h).color(blR, blG, blB, blA).vec4(x, y, w, h).vec4(cornerRadiiTmp[0], cornerRadiiTmp[1], cornerRadiiTmp[2], cornerRadiiTmp[3]).vec4(stroke, 0f, 0f, 0f).next();
        int i3 = mesh.vec2(x + w, y + h).local2(x + w, y + h).color(brR, brG, brB, brA).vec4(x, y, w, h).vec4(cornerRadiiTmp[0], cornerRadiiTmp[1], cornerRadiiTmp[2], cornerRadiiTmp[3]).vec4(stroke, 0f, 0f, 0f).next();
        int i4 = mesh.vec2(x + w, y).local2(x + w, y).color(trR, trG, trB, trA).vec4(x, y, w, h).vec4(cornerRadiiTmp[0], cornerRadiiTmp[1], cornerRadiiTmp[2], cornerRadiiTmp[3]).vec4(stroke, 0f, 0f, 0f).next();
        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }

    public void cutCornerRect(double x, double y, double width, double height, double cut, int argb) {
        chamferedRect(x, y, width, height, cut, argb);
    }

    public void cutCornerRectGradient(double x, double y, double width, double height,
                                      double cut, int startArgb, int endArgb, float angleDeg, float offsetPx) {
        chamferedRectGradient(x, y, width, height, cut, startArgb, endArgb, angleDeg, offsetPx);
    }

    public void cutCornerRectStroke(double x, double y, double width, double height,
                                    double cut, double thickness, int argb) {
        chamferedRectStroke(x, y, width, height, cut, thickness, argb);
    }

    public void cutCornerRectStrokeGradient(double x, double y, double width, double height,
                                            double cut, double thickness,
                                            int startArgb, int endArgb, float angleDeg, float offsetPx) {
        chamferedRectStrokeGradient(x, y, width, height, cut, thickness, startArgb, endArgb, angleDeg, offsetPx);
    }

    public void notchedRect(double x, double y, double width, double height,
                            double notchWidth, double notchDepth, int argb) {
        int count = buildNotchedRect(rectShapeTmp, x, y, width, height, notchWidth, notchDepth);
        polygon(rectShapeTmp, count, argb);
    }

    public void notchedRectGradient(double x, double y, double width, double height,
                                    double notchWidth, double notchDepth,
                                    int startArgb, int endArgb, float angleDeg, float offsetPx) {
        int count = buildNotchedRect(rectShapeTmp, x, y, width, height, notchWidth, notchDepth);
        polygonGradient(rectShapeTmp, count, x, y, width, height, startArgb, endArgb, angleDeg, offsetPx);
    }

    public void notchedRectStroke(double x, double y, double width, double height,
                                  double notchWidth, double notchDepth, double thickness, int argb) {
        int count = buildNotchedRect(rectShapeTmp, x, y, width, height, notchWidth, notchDepth);
        polygonStroke(rectShapeTmp, count, thickness, true, argb);
    }

    public void notchedRectStrokeGradient(double x, double y, double width, double height,
                                          double notchWidth, double notchDepth, double thickness,
                                          int startArgb, int endArgb, float angleDeg, float offsetPx) {
        int count = buildNotchedRect(rectShapeTmp, x, y, width, height, notchWidth, notchDepth);
        polylineLinearGradient(rectShapeTmp, count, thickness, true,
                x, y, width, height, startArgb, endArgb, angleDeg, offsetPx, false);
    }

    public void beveledRect(double x, double y, double width, double height,
                            double bevel, int fillArgb, int highlightArgb, int shadowArgb) {
        chamferedRect(x, y, width, height, bevel, fillArgb);
        double t = Math.max(1.0, Math.min(Math.min(Math.abs(width), Math.abs(height)) * 0.25, bevel));
        connectorTmp[0] = x + bevel;
        connectorTmp[1] = y;
        connectorTmp[2] = x + width - bevel;
        connectorTmp[3] = y;
        connectorTmp[4] = x + width;
        connectorTmp[5] = y + bevel;
        polyline(connectorTmp, 3, t, false, highlightArgb, false);
        connectorTmp[0] = x + width;
        connectorTmp[1] = y + height - bevel;
        connectorTmp[2] = x + width - bevel;
        connectorTmp[3] = y + height;
        connectorTmp[4] = x + bevel;
        connectorTmp[5] = y + height;
        connectorTmp[6] = x;
        connectorTmp[7] = y + height - bevel;
        polyline(connectorTmp, 4, t, false, shadowArgb, false);
    }

    public void beveledRectStroke(double x, double y, double width, double height,
                                  double bevel, double thickness, int argb) {
        chamferedRectStroke(x, y, width, height, bevel, thickness, argb);
    }

    public void beveledRectGradient(double x, double y, double width, double height,
                                    double bevel, int startArgb, int endArgb,
                                    int highlightArgb, int shadowArgb, float angleDeg, float offsetPx) {
        chamferedRectGradient(x, y, width, height, bevel, startArgb, endArgb, angleDeg, offsetPx);
        double t = Math.max(1.0, Math.min(Math.min(Math.abs(width), Math.abs(height)) * 0.25, bevel));
        connectorTmp[0] = x + bevel;
        connectorTmp[1] = y;
        connectorTmp[2] = x + width - bevel;
        connectorTmp[3] = y;
        connectorTmp[4] = x + width;
        connectorTmp[5] = y + bevel;
        polyline(connectorTmp, 3, t, false, highlightArgb, false);
        connectorTmp[0] = x + width;
        connectorTmp[1] = y + height - bevel;
        connectorTmp[2] = x + width - bevel;
        connectorTmp[3] = y + height;
        connectorTmp[4] = x + bevel;
        connectorTmp[5] = y + height;
        connectorTmp[6] = x;
        connectorTmp[7] = y + height - bevel;
        polyline(connectorTmp, 4, t, false, shadowArgb, false);
    }

    public void beveledRectStrokeGradient(double x, double y, double width, double height,
                                          double bevel, double thickness,
                                          int startArgb, int endArgb, float angleDeg, float offsetPx) {
        chamferedRectStrokeGradient(x, y, width, height, bevel, thickness, startArgb, endArgb, angleDeg, offsetPx);
    }

    public void connector(double x1, double y1, double x2, double y2, double thickness, int argb) {
        connectorTmp[0] = x1;
        connectorTmp[1] = y1;
        connectorTmp[2] = x2;
        connectorTmp[3] = y2;
        polyline(connectorTmp, 2, thickness, false, argb, true);
    }

    public void connectorGradient(double x1, double y1, double x2, double y2,
                                  double thickness, int startArgb, int endArgb) {
        connectorTmp[0] = x1;
        connectorTmp[1] = y1;
        connectorTmp[2] = x2;
        connectorTmp[3] = y2;
        polylineGradient(connectorTmp, 2, thickness, false, startArgb, endArgb, true);
    }

    public void wire(double x1, double y1, double x2, double y2, double thickness, int argb) {
        connector(x1, y1, x2, y2, thickness, argb);
    }

    public void wireGradient(double x1, double y1, double x2, double y2,
                             double thickness, int startArgb, int endArgb) {
        connectorGradient(x1, y1, x2, y2, thickness, startArgb, endArgb);
    }

    public void cable(double x1, double y1, double x2, double y2,
                      double thickness, int outerArgb, int innerArgb) {
        connector(x1, y1, x2, y2, thickness + 2.0, outerArgb);
        connector(x1, y1, x2, y2, Math.max(1.0, thickness), innerArgb);
    }

    public void cableGradient(double x1, double y1, double x2, double y2,
                              double thickness, int outerStartArgb, int outerEndArgb,
                              int innerStartArgb, int innerEndArgb) {
        connectorGradient(x1, y1, x2, y2, thickness + 2.0, outerStartArgb, outerEndArgb);
        connectorGradient(x1, y1, x2, y2, Math.max(1.0, thickness), innerStartArgb, innerEndArgb);
    }

    /**
     * Connects two rounded rectangles by their visible border, not by their centers.
     * The connector uses the centers only as the direction vector used to find each border anchor.
     */
    public void roundedRectConnector(double x1, double y1, double w1, double h1, double radius1,
                                     double x2, double y2, double w2, double h2, double radius2,
                                     double thickness, int argb) {
        roundedRectAnchor(connectorAnchorTmp, 0, x1, y1, w1, h1, radius1, x2 + w2 * 0.5, y2 + h2 * 0.5);
        roundedRectAnchor(connectorAnchorTmp, 2, x2, y2, w2, h2, radius2, x1 + w1 * 0.5, y1 + h1 * 0.5);
        connector(connectorAnchorTmp[0], connectorAnchorTmp[1], connectorAnchorTmp[2], connectorAnchorTmp[3], thickness, argb);
    }

    public void roundedRectConnectorGradient(double x1, double y1, double w1, double h1, double radius1,
                                             double x2, double y2, double w2, double h2, double radius2,
                                             double thickness, int startArgb, int endArgb) {
        roundedRectAnchor(connectorAnchorTmp, 0, x1, y1, w1, h1, radius1, x2 + w2 * 0.5, y2 + h2 * 0.5);
        roundedRectAnchor(connectorAnchorTmp, 2, x2, y2, w2, h2, radius2, x1 + w1 * 0.5, y1 + h1 * 0.5);
        connectorGradient(connectorAnchorTmp[0], connectorAnchorTmp[1], connectorAnchorTmp[2], connectorAnchorTmp[3],
                thickness, startArgb, endArgb);
    }

    /**
     * Draws a Bezier node graph edge between two rounded rectangles, anchored on their borders.
     */
    public void roundedRectNodeGraphEdge(double x1, double y1, double w1, double h1, double radius1,
                                         double x2, double y2, double w2, double h2, double radius2,
                                         double thickness, int argb) {
        roundedRectAnchor(connectorAnchorTmp, 0, x1, y1, w1, h1, radius1, x2 + w2 * 0.5, y2 + h2 * 0.5);
        roundedRectAnchor(connectorAnchorTmp, 2, x2, y2, w2, h2, radius2, x1 + w1 * 0.5, y1 + h1 * 0.5);
        nodeGraphEdge(connectorAnchorTmp[0], connectorAnchorTmp[1], connectorAnchorTmp[2], connectorAnchorTmp[3], thickness, argb);
    }

    public void roundedRectNodeGraphEdgeGradient(double x1, double y1, double w1, double h1, double radius1,
                                                 double x2, double y2, double w2, double h2, double radius2,
                                                 double thickness, int startArgb, int endArgb) {
        roundedRectAnchor(connectorAnchorTmp, 0, x1, y1, w1, h1, radius1, x2 + w2 * 0.5, y2 + h2 * 0.5);
        roundedRectAnchor(connectorAnchorTmp, 2, x2, y2, w2, h2, radius2, x1 + w1 * 0.5, y1 + h1 * 0.5);
        nodeGraphEdgeGradient(connectorAnchorTmp[0], connectorAnchorTmp[1], connectorAnchorTmp[2], connectorAnchorTmp[3],
                thickness, startArgb, endArgb);
    }

    /**
     * Draws an orthogonal connector between two rounded rectangles, anchored on their borders.
     */
    public void roundedRectOrthogonalConnector(double x1, double y1, double w1, double h1, double radius1,
                                               double x2, double y2, double w2, double h2, double radius2,
                                               double thickness, int argb) {
        roundedRectOrthogonalConnector(x1, y1, w1, h1, radius1, x2, y2, w2, h2, radius2,
                (x1 + w1 * 0.5 + x2 + w2 * 0.5) * 0.5, thickness, argb);
    }

    /**
     * Draws an orthogonal connector between two rounded rectangles, anchored on their borders.
     */
    public void roundedRectOrthogonalConnector(double x1, double y1, double w1, double h1, double radius1,
                                               double x2, double y2, double w2, double h2, double radius2,
                                               double midX, double thickness, int argb) {
        roundedRectAnchor(connectorAnchorTmp, 0, x1, y1, w1, h1, radius1, x2 + w2 * 0.5, y2 + h2 * 0.5);
        roundedRectAnchor(connectorAnchorTmp, 2, x2, y2, w2, h2, radius2, x1 + w1 * 0.5, y1 + h1 * 0.5);
        orthogonalConnector(connectorAnchorTmp[0], connectorAnchorTmp[1], connectorAnchorTmp[2], connectorAnchorTmp[3],
                midX, thickness, argb);
    }

    public void roundedRectOrthogonalConnectorGradient(double x1, double y1, double w1, double h1, double radius1,
                                                       double x2, double y2, double w2, double h2, double radius2,
                                                       double midX, double thickness, int startArgb, int endArgb) {
        roundedRectAnchor(connectorAnchorTmp, 0, x1, y1, w1, h1, radius1, x2 + w2 * 0.5, y2 + h2 * 0.5);
        roundedRectAnchor(connectorAnchorTmp, 2, x2, y2, w2, h2, radius2, x1 + w1 * 0.5, y1 + h1 * 0.5);
        orthogonalConnectorGradient(connectorAnchorTmp[0], connectorAnchorTmp[1], connectorAnchorTmp[2], connectorAnchorTmp[3],
                midX, thickness, startArgb, endArgb);
    }

    public void orthogonalConnector(double x1, double y1, double x2, double y2,
                                    double thickness, int argb) {
        orthogonalConnector(x1, y1, x2, y2, (x1 + x2) * 0.5, thickness, argb);
    }

    public void orthogonalConnector(double x1, double y1, double x2, double y2,
                                    double midX, double thickness, int argb) {
        connectorTmp[0] = x1;
        connectorTmp[1] = y1;
        connectorTmp[2] = midX;
        connectorTmp[3] = y1;
        connectorTmp[4] = midX;
        connectorTmp[5] = y2;
        connectorTmp[6] = x2;
        connectorTmp[7] = y2;
        polyline(connectorTmp, 4, thickness, false, argb, true);
    }

    public void orthogonalConnectorGradient(double x1, double y1, double x2, double y2,
                                            double midX, double thickness, int startArgb, int endArgb) {
        connectorTmp[0] = x1;
        connectorTmp[1] = y1;
        connectorTmp[2] = midX;
        connectorTmp[3] = y1;
        connectorTmp[4] = midX;
        connectorTmp[5] = y2;
        connectorTmp[6] = x2;
        connectorTmp[7] = y2;
        polylineGradient(connectorTmp, 4, thickness, false, startArgb, endArgb, true);
    }

    public void bezierConnector(double x1, double y1,
                                double cx1, double cy1,
                                double cx2, double cy2,
                                double x2, double y2,
                                double thickness,
                                int argb) {
        bezierConnector(x1, y1, cx1, cy1, cx2, cy2, x2, y2, 20, thickness, argb);
    }

    public void bezierConnector(double x1, double y1,
                                double cx1, double cy1,
                                double cx2, double cy2,
                                double x2, double y2,
                                int segments,
                                double thickness,
                                int argb) {
        int count = buildBezier(connectorTmp, x1, y1, cx1, cy1, cx2, cy2, x2, y2, segments);
        polyline(connectorTmp, count, thickness, false, argb, false);
    }

    public void bezierConnectorGradient(double x1, double y1,
                                        double cx1, double cy1,
                                        double cx2, double cy2,
                                        double x2, double y2,
                                        int segments,
                                        double thickness,
                                        int startArgb,
                                        int endArgb) {
        int count = buildBezier(connectorTmp, x1, y1, cx1, cy1, cx2, cy2, x2, y2, segments);
        polylineGradient(connectorTmp, count, thickness, false, startArgb, endArgb, false);
    }

    public void nodeGraphEdge(double x1, double y1, double x2, double y2, double thickness, int argb) {
        double dx = Math.abs(x2 - x1);
        double c = Math.max(24.0, dx * 0.5);
        bezierConnector(x1, y1, x1 + c, y1, x2 - c, y2, x2, y2, thickness, argb);
    }

    public void nodeGraphEdgeGradient(double x1, double y1, double x2, double y2,
                                      double thickness, int startArgb, int endArgb) {
        double dx = Math.abs(x2 - x1);
        double c = Math.max(24.0, dx * 0.5);
        bezierConnectorGradient(x1, y1, x1 + c, y1, x2 - c, y2, x2, y2, 20, thickness, startArgb, endArgb);
    }

    public void spline(double[] points, int pointCount, double thickness, boolean closed, int argb) {
        if (points == null || pointCount < 2) return;
        polyline(points, pointCount, thickness, closed, argb, true);
    }

    public void splineGradient(double[] points, int pointCount, double thickness, boolean closed,
                               int startArgb, int endArgb) {
        if (points == null || pointCount < 2) return;
        polylineGradient(points, pointCount, thickness, closed, startArgb, endArgb, true);
    }

    public void texQuad(double x, double y, double width, double height,
                        double texX1, double texY1, double texX2, double texY2,
                        int argb) {
        recordUi(new UiShapeCommand(UiShape.rect(x, y, width, height), UiPaint.solid(argb), UiStroke.NONE, true));
        if (texturedTriangles == null) return;
        texturedTriangles.alpha = alpha;
        texturedTriangles.ensureQuadCapacity();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int i1 = texturedTriangles.vec2(x, y).raw2(texX1, texY1).color(r, g, b, a).next();
        int i2 = texturedTriangles.vec2(x, y + height).raw2(texX1, texY2).color(r, g, b, a).next();
        int i3 = texturedTriangles.vec2(x + width, y + height).raw2(texX2, texY2).color(r, g, b, a).next();
        int i4 = texturedTriangles.vec2(x + width, y).raw2(texX2, texY1).color(r, g, b, a).next();

        texturedTriangles.quad(i1, i2, i3, i4);
    }

    public void liquidGlassCircle(double cx, double cy, double radius,
                                  float softness,
                                  int tintArgb,
                                  float globalAlpha,
                                  float fresnelPower,
                                  float fresnelAlpha,
                                  float baseAlpha,
                                  float fresnelMix,
                                  float distortPx,
                                  float squirclePower) {
        if (radius <= 0.0) return;
        double size = radius * 2.0;
        liquidGlassRect(cx - radius, cy - radius, size, size,
                (float) radius, softness,
                tintArgb,
                globalAlpha,
                fresnelPower,
                fresnelAlpha,
                baseAlpha,
                fresnelMix,
                distortPx,
                squirclePower);
    }

    public void liquidGlassSquircle(UiBoxShape squircle,
                                    int tintArgb,
                                    float glassAlpha,
                                    float blurAlpha,
                                    LiquidGlassPreset preset) {
        if (squircle == null || !squircle.isSquircle()) return;
        UiRect b = squircle.bounds();
        liquidGlassRect(b.x(), b.y(), b.width(), b.height(), 0.0f,
                tintArgb, glassAlpha, blurAlpha, preset, -squircle.squircleExponent());
    }

    public void liquidGlassCircle(double cx, double cy, double radius,
                                  int tintArgb,
                                  float globalAlpha,
                                  LiquidGlassPreset preset) {
        if (radius <= 0.0) return;
        double size = radius * 2.0;
        liquidGlassRect(cx - radius, cy - radius, size, size,
                (float) radius,
                tintArgb,
                globalAlpha,
                preset);
    }

    //Если squirclePower < 1.5f, поведение без squircle
    public void liquidGlassRect(double x, double y, double w, double h,
                                float radius, float softness,
                                int tintArgb,
                                float globalAlpha,
                                float fresnelPower,
                                float fresnelAlpha,
                                float baseAlpha,
                                float fresnelMix,
                                float distortPx) {
        liquidGlassRect(x, y, w, h,
                radius, softness,
                tintArgb,
                globalAlpha,
                fresnelPower,
                fresnelAlpha,
                baseAlpha,
                fresnelMix,
                distortPx,
                0.0f);
    }

    public void liquidGlassRect(double x, double y, double w, double h,
                                float radius, float softness,
                                int tintArgb,
                                float globalAlpha,
                                float fresnelPower,
                                float fresnelAlpha,
                                float baseAlpha,
                                float fresnelMix,
                                float distortPx,
                                float squirclePower) {
        liquidGlassRect(x, y, w, h,
                radius, softness,
                tintArgb,
                globalAlpha,
                globalAlpha,
                fresnelPower,
                fresnelAlpha,
                baseAlpha,
                fresnelMix,
                distortPx,
                squirclePower);
    }

    public void liquidGlassRect(double x, double y, double w, double h,
                                float radius, float softness,
                                int tintArgb,
                                float glassAlpha,
                                float blurAlpha,
                                float fresnelPower,
                                float fresnelAlpha,
                                float baseAlpha,
                                float fresnelMix,
                                float distortPx,
                                float squirclePower) {
        liquidGlassRectCorners(x, y, w, h,
                radius, radius, radius, radius,
                softness,
                tintArgb,
                glassAlpha,
                blurAlpha,
                fresnelPower,
                fresnelAlpha,
                baseAlpha,
                fresnelMix,
                distortPx,
                squirclePower);
    }

    public void liquidGlassRect(double x, double y, double w, double h,
                                float radius,
                                int tintArgb,
                                float globalAlpha,
                                LiquidGlassPreset preset) {
        liquidGlassRect(x, y, w, h,
                radius,
                tintArgb,
                globalAlpha,
                globalAlpha,
                preset,
                1.0f,
                0.0f);
    }

    public void liquidGlassRect(double x, double y, double w, double h,
                                float radius,
                                int tintArgb,
                                float glassAlpha,
                                float blurAlpha,
                                LiquidGlassPreset preset) {
        liquidGlassRect(x, y, w, h,
                radius,
                tintArgb,
                glassAlpha,
                blurAlpha,
                preset,
                1.0f,
                0.0f);
    }

    public void liquidGlassRect(double x, double y, double w, double h,
                                float radius,
                                int tintArgb,
                                float globalAlpha,
                                LiquidGlassPreset preset,
                                float squirclePower) {
        liquidGlassRect(x, y, w, h,
                radius,
                tintArgb,
                globalAlpha,
                globalAlpha,
                preset,
                1.0f,
                squirclePower);
    }

    public void liquidGlassRect(double x, double y, double w, double h,
                                float radius,
                                int tintArgb,
                                float glassAlpha,
                                float blurAlpha,
                                LiquidGlassPreset preset,
                                float squirclePower) {
        liquidGlassRect(x, y, w, h,
                radius,
                tintArgb,
                glassAlpha,
                blurAlpha,
                preset,
                1.0f,
                squirclePower);
    }

    public void liquidGlassRect(double x, double y, double w, double h,
                                float radius,
                                int tintArgb,
                                float globalAlpha,
                                LiquidGlassPreset preset,
                                float thicknessScale,
                                float squirclePower) {
        liquidGlassRect(x, y, w, h, radius, tintArgb, globalAlpha, globalAlpha, preset, thicknessScale, squirclePower);
    }

    public void liquidGlassRect(double x, double y, double w, double h,
                                float radius,
                                int tintArgb,
                                float glassAlpha,
                                float blurAlpha,
                                LiquidGlassPreset preset,
                                float thicknessScale,
                                float squirclePower) {
        LiquidGlassPreset p = preset != null ? preset : LiquidGlassPreset.BALANCED;
        float materialAlpha = clamp01(glassAlpha);
        float blurStrength = clamp01(blurAlpha);
        float scale = Math.max(0.001f, thicknessScale);
        liquidGlassRect(x, y, w, h,
                radius, p.thicknessPx * scale,
                tintArgb,
                materialAlpha,
                blurStrength,
                p.fresnelPower,
                p.fresnelAlpha,
                p.baseAlpha,
                p.fresnelMix,
                p.distortPx * materialAlpha,
                squirclePower);
    }

    public void liquidGlassRectCorners(double x, double y, double w, double h,
                                       float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                       int tintArgb,
                                       float globalAlpha,
                                       LiquidGlassPreset preset) {
        liquidGlassRectCorners(x, y, w, h,
                radiusTL, radiusTR, radiusBR, radiusBL,
                tintArgb,
                globalAlpha,
                globalAlpha,
                preset,
                1.0f,
                0.0f);
    }

    public void liquidGlassRectCorners(double x, double y, double w, double h,
                                       float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                       int tintArgb,
                                       float glassAlpha,
                                       float blurAlpha,
                                       LiquidGlassPreset preset) {
        liquidGlassRectCorners(x, y, w, h,
                radiusTL, radiusTR, radiusBR, radiusBL,
                tintArgb,
                glassAlpha,
                blurAlpha,
                preset,
                1.0f,
                0.0f);
    }

    public void liquidGlassRectCorners(double x, double y, double w, double h,
                                       float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                       int tintArgb,
                                       float globalAlpha,
                                       LiquidGlassPreset preset,
                                       float squirclePower) {
        liquidGlassRectCorners(x, y, w, h,
                radiusTL, radiusTR, radiusBR, radiusBL,
                tintArgb,
                globalAlpha,
                globalAlpha,
                preset,
                1.0f,
                squirclePower);
    }

    public void liquidGlassRectCorners(double x, double y, double w, double h,
                                       float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                       int tintArgb,
                                       float glassAlpha,
                                       float blurAlpha,
                                       LiquidGlassPreset preset,
                                       float squirclePower) {
        liquidGlassRectCorners(x, y, w, h,
                radiusTL, radiusTR, radiusBR, radiusBL,
                tintArgb,
                glassAlpha,
                blurAlpha,
                preset,
                1.0f,
                squirclePower);
    }

    public void liquidGlassRectCorners(double x, double y, double w, double h,
                                       float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                       int tintArgb,
                                       float globalAlpha,
                                       LiquidGlassPreset preset,
                                       float thicknessScale,
                                       float squirclePower) {
        liquidGlassRectCorners(x, y, w, h,
                radiusTL, radiusTR, radiusBR, radiusBL,
                tintArgb,
                globalAlpha,
                globalAlpha,
                preset,
                thicknessScale,
                squirclePower);
    }

    public void liquidGlassRectCorners(double x, double y, double w, double h,
                                       float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                       int tintArgb,
                                       float glassAlpha,
                                       float blurAlpha,
                                       LiquidGlassPreset preset,
                                       float thicknessScale,
                                       float squirclePower) {
        LiquidGlassPreset p = preset != null ? preset : LiquidGlassPreset.BALANCED;
        float materialAlpha = clamp01(glassAlpha);
        float blurStrength = clamp01(blurAlpha);
        float scale = Math.max(0.001f, thicknessScale);
        liquidGlassRectCorners(x, y, w, h,
                radiusTL, radiusTR, radiusBR, radiusBL,
                p.thicknessPx * scale,
                tintArgb,
                materialAlpha,
                blurStrength,
                p.fresnelPower,
                p.fresnelAlpha,
                p.baseAlpha,
                p.fresnelMix,
                p.distortPx * materialAlpha,
                squirclePower);
    }

    public void liquidGlassRectCorners(double x, double y, double w, double h,
                                       float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                       float softness,
                                       int tintArgb,
                                       float globalAlpha,
                                       float fresnelPower,
                                       float fresnelAlpha,
                                       float baseAlpha,
                                       float fresnelMix,
                                       float distortPx) {
        liquidGlassRectCorners(x, y, w, h,
                radiusTL, radiusTR, radiusBR, radiusBL,
                softness,
                tintArgb,
                globalAlpha,
                fresnelPower,
                fresnelAlpha,
                baseAlpha,
                fresnelMix,
                distortPx,
                0.0f);
    }

    public void liquidGlassRectCorners(double x, double y, double w, double h,
                                       float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                       float softness,
                                       int tintArgb,
                                       float globalAlpha,
                                       float fresnelPower,
                                       float fresnelAlpha,
                                       float baseAlpha,
                                       float fresnelMix,
                                       float distortPx,
                                       float squirclePower) {
        liquidGlassRectCorners(x, y, w, h,
                radiusTL, radiusTR, radiusBR, radiusBL,
                softness,
                tintArgb,
                globalAlpha,
                globalAlpha,
                fresnelPower,
                fresnelAlpha,
                baseAlpha,
                fresnelMix,
                distortPx,
                squirclePower);
    }

    public void liquidGlassRectCorners(double x, double y, double w, double h,
                                       float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                       float softness,
                                       int tintArgb,
                                       float glassAlpha,
                                       float blurAlpha,
                                       float fresnelPower,
                                       float fresnelAlpha,
                                       float baseAlpha,
                                       float fresnelMix,
                                       float distortPx,
                                       float squirclePower) {
        if (w <= 0.0 || h <= 0.0) return;

        boolean wholeBoxSquircle = squirclePower <= -1.5f;
        float shapePower = Math.abs(squirclePower);
        UiShape glassShape = wholeBoxSquircle
                ? UiShapes.squircle(x, y, w, h, shapePower)
                : UiShape.roundedRect(x, y, w, h, radiusTL, radiusTR, radiusBR, radiusBL);
        effect(UiEffectSpec.liquidGlass(glassShape, softness, softness, distortPx, tintArgb));
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        RenderTarget fb = mc.gameRenderer.mainRenderTarget();
        if (fb == null) return;
        GpuTextureView src = fb.getColorTextureView();
        if (src == null) return;
        GpuSampler sampler = PostProcessManager.getSampler();
        if (sampler == null) return;

        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreateBlur(UiBatchType.LIQUID_GLASS, src, sampler,
                DEFAULT_LIQUID_GLASS_BLUR_QUALITY, LIQUID_GLASS_KAWASE_OFFSET_PX);
        if (batch == null) return;
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;

        mesh.ensureQuadCapacity();

        int a = (tintArgb >>> 24) & 0xFF;
        int r = (tintArgb >>> 16) & 0xFF;
        int g = (tintArgb >>> 8) & 0xFF;
        int b = tintArgb & 0xFF;

        float clampedGlassAlpha = clamp01(glassAlpha);
        float clampedBlurAlpha = clamp01(blurAlpha);
        int finalA = (int) (a * clampedGlassAlpha);

        normalizeCornerRadii(w, h, radiusTL, radiusTR, radiusBR, radiusBL, cornerRadiiTmp);

        float thickness = Math.max(0.0f, softness);
        float mix = clamp01(fresnelMix);
        float fa = clamp01(fresnelAlpha);
        float ba = clamp01(baseAlpha);
        float packedDistort = packLiquidGlassPayload(distortPx, shapePower, clampedBlurAlpha, wholeBoxSquircle);

        int i1 = mesh.vec2(x, y).raw2(mix, packedDistort).local2(x, y).color(r, g, b, finalA)
                .vec4(x, y, w, h)
                .vec4(cornerRadiiTmp[0], cornerRadiiTmp[1], cornerRadiiTmp[2], cornerRadiiTmp[3])
                .vec4(thickness, fresnelPower, fa, ba)
                .next();
        int i2 = mesh.vec2(x, y + h).raw2(mix, packedDistort).local2(x, y + h).color(r, g, b, finalA)
                .vec4(x, y, w, h)
                .vec4(cornerRadiiTmp[0], cornerRadiiTmp[1], cornerRadiiTmp[2], cornerRadiiTmp[3])
                .vec4(thickness, fresnelPower, fa, ba)
                .next();
        int i3 = mesh.vec2(x + w, y + h).raw2(mix, packedDistort).local2(x + w, y + h).color(r, g, b, finalA)
                .vec4(x, y, w, h)
                .vec4(cornerRadiiTmp[0], cornerRadiiTmp[1], cornerRadiiTmp[2], cornerRadiiTmp[3])
                .vec4(thickness, fresnelPower, fa, ba)
                .next();
        int i4 = mesh.vec2(x + w, y).raw2(mix, packedDistort).local2(x + w, y).color(r, g, b, finalA)
                .vec4(x, y, w, h)
                .vec4(cornerRadiiTmp[0], cornerRadiiTmp[1], cornerRadiiTmp[2], cornerRadiiTmp[3])
                .vec4(thickness, fresnelPower, fa, ba)
                .next();

        mesh.quad(i1, i2, i3, i4);

        endAutoBatch(auto);
    }


    private static BlurQuality legacyBlurQuality(float legacyQuality) {
        if (legacyQuality >= 32.0f) return BlurQuality.ULTRA;
        if (legacyQuality >= 12.0f) return BlurQuality.HIGH;
        if (legacyQuality >= 5.0f) return BlurQuality.MEDIUM;
        return BlurQuality.LOW;
    }

    private static float legacyKawaseOffset(float legacyQuality) {
        if (!Float.isFinite(legacyQuality)) return DEFAULT_KAWASE_OFFSET_PX;
        return Mth.clamp(legacyQuality / 18.0f, 0.85f, 3.25f);
    }

    public void blurRect(double x, double y, double w, double h,
                         float radius, float quality, float brightness, float alpha, int ignoredTintRgb) {
        blurRect(x, y, w, h, radius, quality, brightness, alpha, ignoredTintRgb, UiBatchType.BLUR);
    }

    public void blurSquircle(double x, double y, double w, double h,
                             UiSquircleProfile profile,
                             float quality, float brightness, float alpha, int ignoredTintRgb) {
        UiSquircleProfile safe = profile != null ? profile : UiSquircleProfile.STANDARD;
        blurSquircle(x, y, w, h, safe.exponent(), quality, brightness, alpha, ignoredTintRgb);
    }

    public void blurSquircle(double x, double y, double w, double h,
                             float exponent,
                             float quality, float brightness, float alpha, int ignoredTintRgb) {
        if (w <= 0.0 || h <= 0.0 || alpha <= 0.001f) return;
        UiBoxShape box = UiBoxShape.squircle(x, y, w, h, exponent);
        effect(UiEffectSpec.blur(box, 0.0, ignoredTintRgb));
        BlurSource source = getBlurSource();
        if (source == null) return;

        boolean auto = beginAutoBatch();
        try {
            DrawBatch batch = UI_BATCHER.getOrCreateBlur(UiBatchType.BLUR, source.view, source.sampler,
                    legacyBlurQuality(quality), legacyKawaseOffset(quality));
            if (batch == null) return;
            MeshBuilder mesh = batch.mesh;
            mesh.alpha = 1.0;
            float finalAlpha = (float) (alpha * this.alpha);
            float encodedShape = -1000.0f - box.squircleExponent();
            mesh.ensureQuadCapacity();
            int i1 = mesh.vec2(x, y).local2(x, y).color(255, 255, 255, 255).vec4(x, y, w, h).vec4(encodedShape, quality, brightness, finalAlpha).next();
            int i2 = mesh.vec2(x, y + h).local2(x, y + h).color(255, 255, 255, 255).vec4(x, y, w, h).vec4(encodedShape, quality, brightness, finalAlpha).next();
            int i3 = mesh.vec2(x + w, y + h).local2(x + w, y + h).color(255, 255, 255, 255).vec4(x, y, w, h).vec4(encodedShape, quality, brightness, finalAlpha).next();
            int i4 = mesh.vec2(x + w, y).local2(x + w, y).color(255, 255, 255, 255).vec4(x, y, w, h).vec4(encodedShape, quality, brightness, finalAlpha).next();
            mesh.quad(i1, i2, i3, i4);
        } finally {
            endAutoBatch(auto);
        }
    }

    public void glassBlurRect(double x, double y, double w, double h,
                              float radius, float quality, float brightness, float alpha, int ignoredTintRgb) {
        if (w <= 0.0 || h <= 0.0) return;
        if (alpha <= 0.001f) return;

        effect(UiEffectSpec.blur(UiShape.roundedRect(x, y, w, h, radius), radius, ignoredTintRgb));
        BlurSource source = getBlurSource();
        if (source == null) return;

        boolean auto = beginAutoBatch();
        try {
            DrawBatch batch = UI_BATCHER.getOrCreateBlur(UiBatchType.GLASS_BLUR, source.view, source.sampler,
                    BlurQuality.HIGH, legacyKawaseOffset(quality));
            if (batch == null) return;
            MeshBuilder mesh = batch.mesh;
            mesh.alpha = 1.0;

            float finalAlpha = clamp01((float) (alpha * this.alpha));
            if (finalAlpha <= 0.001f) return;

            int a = Math.max(0, Math.min(255, Math.round(finalAlpha * 255.0f)));
            float smoothness = 2.0f;
            float r = Math.max(0.0f, radius);

            mesh.ensureQuadCapacity();
            int i1 = mesh.vec2(x, y).local2(x, y).color(255, 255, 255, a)
                    .vec4(x, y, w, h)
                    .vec4(r, r, r, r)
                    .vec4(quality, brightness, smoothness, 0.0f)
                    .next();
            int i2 = mesh.vec2(x, y + h).local2(x, y + h).color(255, 255, 255, a)
                    .vec4(x, y, w, h)
                    .vec4(r, r, r, r)
                    .vec4(quality, brightness, smoothness, 0.0f)
                    .next();
            int i3 = mesh.vec2(x + w, y + h).local2(x + w, y + h).color(255, 255, 255, a)
                    .vec4(x, y, w, h)
                    .vec4(r, r, r, r)
                    .vec4(quality, brightness, smoothness, 0.0f)
                    .next();
            int i4 = mesh.vec2(x + w, y).local2(x + w, y).color(255, 255, 255, a)
                    .vec4(x, y, w, h)
                    .vec4(r, r, r, r)
                    .vec4(quality, brightness, smoothness, 0.0f)
                    .next();
            mesh.quad(i1, i2, i3, i4);
        } finally {
            endAutoBatch(auto);
        }
    }

    private void blurRect(double x, double y, double w, double h,
                          float radius, float quality, float brightness, float alpha, int ignoredTintRgb,
                          UiBatchType batchType) {
        if (w <= 0.0 || h <= 0.0) return;
        if (alpha <= 0.001f) return;

        effect(UiEffectSpec.blur(UiShape.roundedRect(x, y, w, h, radius), radius, ignoredTintRgb));
        BlurSource source = getBlurSource();
        if (source == null) return;

        boolean auto = beginAutoBatch();
        try {
            DrawBatch batch = UI_BATCHER.getOrCreateBlur(batchType, source.view, source.sampler,
                    legacyBlurQuality(quality), legacyKawaseOffset(quality));
            if (batch == null) return;
            MeshBuilder mesh = batch.mesh;
            mesh.alpha = 1.0;

            float finalAlpha = (float) (alpha * this.alpha);
            mesh.ensureQuadCapacity();
            int i1 = mesh.vec2(x, y).local2(x, y).color(255, 255, 255, 255).vec4(x, y, w, h).vec4(radius, quality, brightness, finalAlpha).next();
            int i2 = mesh.vec2(x, y + h).local2(x, y + h).color(255, 255, 255, 255).vec4(x, y, w, h).vec4(radius, quality, brightness, finalAlpha).next();
            int i3 = mesh.vec2(x + w, y + h).local2(x + w, y + h).color(255, 255, 255, 255).vec4(x, y, w, h).vec4(radius, quality, brightness, finalAlpha).next();
            int i4 = mesh.vec2(x + w, y).local2(x + w, y).color(255, 255, 255, 255).vec4(x, y, w, h).vec4(radius, quality, brightness, finalAlpha).next();
            mesh.quad(i1, i2, i3, i4);
        } finally {
            endAutoBatch(auto);
        }
    }

    public void blurChamferedRect(double x, double y, double w, double h,
                                  double chamfer, float quality, float brightness, float alpha, int ignoredTintRgb) {
        if (w <= 0.0 || h <= 0.0) return;
        if (alpha <= 0.001f) return;
        float cut = clampChamfer(chamfer, w, h);
        if (cut <= 0.0f) {
            blurRect(x, y, w, h, 0.0f, quality, brightness, alpha, ignoredTintRgb);
            return;
        }
        blurRect(x, y, w, h, -cut, quality, brightness, alpha, ignoredTintRgb);
    }

    public void blurComposite(Consumer<BlurComposite> consumer) {
        if (consumer == null) return;

        BlurSource source = getBlurSource();
        if (source == null) return;

        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreateBlur(UiBatchType.BLUR_COMPLEX, source.view, source.sampler,
                DEFAULT_BLUR_QUALITY, DEFAULT_KAWASE_OFFSET_PX);
        if (batch == null) return;

        MeshBuilder mesh = batch.mesh;
        mesh.alpha = 1.0;

        try {
            consumer.accept(new BlurComposite(mesh, (float) this.alpha));
        } finally {
            endAutoBatch(auto);
        }
    }

    private BlurSource getBlurSource() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        RenderTarget fb = mc.gameRenderer.mainRenderTarget();
        if (fb == null) return null;
        GpuTextureView src = fb.getColorTextureView();
        if (src == null) return null;
        GpuSampler sampler = PostProcessManager.getSampler();
        if (sampler == null) return null;
        return new BlurSource(src, sampler);
    }

    private boolean beginAutoBatch() {
        return UiRenderDispatcher.beginAutoBatch();
    }

    private void endAutoBatch(boolean auto) {
        UiRenderDispatcher.endAutoBatch(auto);
    }

    private void renderFlexibleBoxFallback(UiBoxShape box, UiPaint paint, UiStroke stroke, boolean fill) {
        if (textured || box == null || paint == null) return;
        int count = UiBoxPathBuilder.write(box, rectShapeTmp, rectShapeTmp.length / 2);
        if (count < 3) return;

        if (fill) {
            switch (paint.kind()) {
                case LINEAR_GRADIENT ->
                        polygonGradient(rectShapeTmp, count, box.bounds().x(), box.bounds().y(), box.bounds().width(), box.bounds().height(),
                                paint.topLeft(), paint.topRight(), paint.angleDeg(), paint.offsetPx());
                default -> polygon(rectShapeTmp, count, paint.solidColor());
            }
        } else if (stroke != null && stroke.enabled()) {
            if (paint.kind() == UiPaintKind.LINEAR_GRADIENT) {
                polylineLinearGradient(rectShapeTmp, count, stroke.thickness(), true,
                        box.bounds().x(), box.bounds().y(), box.bounds().width(), box.bounds().height(),
                        paint.topLeft(), paint.topRight(), paint.angleDeg(), paint.offsetPx(),
                        stroke.join() == UiPathJoin.ROUND || stroke.cap() == UiPathCap.ROUND);
            } else {
                polyline(rectShapeTmp, count, stroke.thickness(), true, paint.solidColor(),
                        stroke.join() == UiPathJoin.ROUND || stroke.cap() == UiPathCap.ROUND);
            }
        }
    }

    private void renderSquircleSdf(UiBoxShape box, UiPaint paint, UiStroke stroke, boolean fill) {
        if (textured || box == null || paint == null) return;
        UiRect bounds = box.bounds();
        double x = bounds.x();
        double y = bounds.y();
        double w = bounds.width();
        double h = bounds.height();
        if (w <= 0.0 || h <= 0.0 || (!fill && (stroke == null || !stroke.enabled()))) return;

        boolean auto = beginAutoBatch();
        boolean warped = RenderWarpStack.active();
        DrawBatch batch = UI_BATCHER.getOrCreate(warped ? UiBatchType.SHAPE_WARPED : UiBatchType.SHAPE, null, null);
        if (batch == null) {
            endAutoBatch(auto);
            return;
        }

        int cTL = paint.topLeft();
        int cTR = paint.topRight();
        int cBR = paint.bottomRight();
        int cBL = paint.bottomLeft();
        if (paint.kind() == UiPaintKind.LINEAR_GRADIENT) {
            computeLinearGradientColors((float) w, (float) h,
                    paint.topLeft(), paint.topRight(), paint.angleDeg(), paint.offsetPx(), gradientTmp);
            cTL = gradientTmp[0];
            cTR = gradientTmp[1];
            cBR = gradientTmp[2];
            cBL = gradientTmp[3];
        }

        UiFastShapeParams params = UiFastShapeParams.squircle(box, fill ? UiStroke.NONE : stroke, fill);
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;
        double strokeOutset = !fill && stroke != null ? stroke.thickness() * 0.5 : 0.0;
        double qx = x - strokeOutset;
        double qy = y - strokeOutset;
        double qw = w + strokeOutset * 2.0;
        double qh = h + strokeOutset * 2.0;
        if (warped) {
            appendWarpedSdfGrid(mesh, qx, qy, qw, qh, cTL, cTR, cBR, cBL,
                    bounds, params.kind(), params.shape(), params.strokeWidth(), params.flags());
        } else {
            mesh.ensureQuadCapacity();
            int i1 = appendShapeVertex(mesh, qx, qy, cTL, bounds, params);
            int i2 = appendShapeVertex(mesh, qx, qy + qh, cBL, bounds, params);
            int i3 = appendShapeVertex(mesh, qx + qw, qy + qh, cBR, bounds, params);
            int i4 = appendShapeVertex(mesh, qx + qw, qy, cTR, bounds, params);
            mesh.quad(i1, i2, i3, i4);
        }
        endAutoBatch(auto);
    }

    private static int appendShapeVertex(MeshBuilder mesh, double x, double y, int argb,
                                         UiRect bounds, UiFastShapeParams p) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        mesh.vec2(x, y);
        return mesh.color(r, g, b, a)
                .vec4(bounds.x(), bounds.y(), bounds.width(), bounds.height())
                .vec4(p.kind(), p.shape(), p.strokeWidth(), p.flags()).next();
    }

    private void appendWarpedSdfGrid(
            MeshBuilder mesh,
            double x,
            double y,
            double width,
            double height,
            int cTopLeft,
            int cTopRight,
            int cBottomRight,
            int cBottomLeft,
            UiRect sdfBounds,
            float param0,
            float param1,
            float param2,
            float param3
    ) {
        int columns = warpedGridSegments(width);
        int rows = warpedGridSegments(height);
        int stride = columns + 1;
        int vertexCount = stride * (rows + 1);
        int indexCount = columns * rows * 6;
        mesh.ensureCapacity(vertexCount, indexCount);

        for (int row = 0; row <= rows; row++) {
            double v = row / (double) rows;
            double py = y + height * v;
            for (int column = 0; column <= columns; column++) {
                double u = column / (double) columns;
                double px = x + width * u;
                int argb = bilerpArgb(cTopLeft, cTopRight, cBottomRight, cBottomLeft, u, v);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                warpedShapeVertexTmp[row * stride + column] = mesh
                        .vec2(px, py)
                        .rawLocal2(px, py)
                        .color(r, g, b, a)
                        .vec4(sdfBounds.x(), sdfBounds.y(), sdfBounds.width(), sdfBounds.height())
                        .vec4(param0, param1, param2, param3)
                        .next();
            }
        }

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int topLeft = warpedShapeVertexTmp[row * stride + column];
                int topRight = warpedShapeVertexTmp[row * stride + column + 1];
                int bottomLeft = warpedShapeVertexTmp[(row + 1) * stride + column];
                int bottomRight = warpedShapeVertexTmp[(row + 1) * stride + column + 1];
                mesh.quad(topLeft, bottomLeft, bottomRight, topRight);
            }
        }
    }

    private static int warpedGridSegments(double extent) {
        return Math.max(4, Math.min(8, (int) Math.ceil(Math.abs(extent) / 18.0)));
    }

    private static int bilerpArgb(int topLeft, int topRight, int bottomRight, int bottomLeft,
                                  double u, double v) {
        int top = lerpArgb(topLeft, topRight, u);
        int bottom = lerpArgb(bottomLeft, bottomRight, u);
        return lerpArgb(top, bottom, v);
    }

    private static int lerpArgb(int from, int to, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int a = (int) Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * clamped);
        int r = (int) Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * clamped);
        int g = (int) Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * clamped);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public void polygon(double[] points, int pointCount, int argb) {
        if (textured) {
            throw new IllegalStateException("Polygon drawing is supported only on Renderer2D.COLOR.");
        }
        int safeCount = Math.min(pointCount, points != null ? points.length / 2 : 0);
        safeCount = Math.min(safeCount, Math.min(polygonIndexTmp.length, polygonVertexTmp.length));
        if (points == null || safeCount < 3) return;

        path(UiShape.polyline(points, safeCount, true), UiPaint.solid(argb), UiStroke.NONE, true);
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.COLORED, null, null);
        if (batch == null) {
            endAutoBatch(auto);
            return;
        }
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;
        appendPolygon(mesh, points, safeCount, polygonIndexTmp, polygonVertexTmp, argb);
        endAutoBatch(auto);
    }

    private void polygonGradient(double[] points, int pointCount,
                                 double x, double y, double width, double height,
                                 int startArgb, int endArgb, float angleDeg, float offsetPx) {
        if (textured) {
            throw new IllegalStateException("Polygon drawing is supported only on Renderer2D.COLOR.");
        }
        if (points == null || pointCount < 3) return;

        path(UiShape.polyline(points, pointCount, true), UiPaint.linear(startArgb, endArgb, angleDeg, offsetPx), UiStroke.NONE, true);
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.COLORED, null, null);
        if (batch == null) {
            endAutoBatch(auto);
            return;
        }
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;
        appendPolygonGradient(mesh, points, pointCount, polygonIndexTmp, polygonVertexTmp,
                x, y, width, height, startArgb, endArgb, angleDeg, offsetPx);
        endAutoBatch(auto);
    }

    private void polygonStroke(double[] points, int pointCount, double thickness, boolean closed, int argb) {
        polyline(points, pointCount, thickness, closed, argb, false);
    }

    private void polyline(double[] points, int pointCount, double thickness, boolean closed, int argb) {
        polyline(points, pointCount, thickness, closed, argb, false);
    }

    private void polyline(double[] points, int pointCount, double thickness, boolean closed, int argb, boolean roundCapsAndJoins) {
        if (textured) {
            throw new IllegalStateException("Polyline drawing is supported only on Renderer2D.COLOR.");
        }
        if (points == null || pointCount < 2) return;
        double t = Math.max(0.0, thickness);
        if (t <= 0.0) return;

        path(UiShape.polyline(points, pointCount, closed), UiPaint.solid(argb),
                roundCapsAndJoins ? UiStroke.of(t).withRoundCapsAndJoins() : UiStroke.of(t), false);
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.COLORED, null, null);
        if (batch == null) {
            endAutoBatch(auto);
            return;
        }
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;
        appendPolyline(mesh, points, pointCount, t, closed, argb, roundCapsAndJoins);
        endAutoBatch(auto);
    }

    private void polylineGradient(double[] points, int pointCount, double thickness, boolean closed,
                                  int startArgb, int endArgb, boolean roundCapsAndJoins) {
        if (textured) {
            throw new IllegalStateException("Polyline drawing is supported only on Renderer2D.COLOR.");
        }
        if (points == null || pointCount < 2) return;
        double t = Math.max(0.0, thickness);
        if (t <= 0.0) return;

        path(UiShape.polyline(points, pointCount, closed), UiPaint.corners(startArgb, endArgb, endArgb, startArgb),
                roundCapsAndJoins ? UiStroke.of(t).withRoundCapsAndJoins() : UiStroke.of(t), false);
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.COLORED, null, null);
        if (batch == null) {
            endAutoBatch(auto);
            return;
        }
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;
        appendPolylineGradient(mesh, points, pointCount, t, closed, startArgb, endArgb, roundCapsAndJoins);
        endAutoBatch(auto);
    }

    private void polylineLinearGradient(double[] points, int pointCount, double thickness, boolean closed,
                                        double x, double y, double width, double height,
                                        int startArgb, int endArgb, float angleDeg, float offsetPx,
                                        boolean roundCapsAndJoins) {
        if (textured) {
            throw new IllegalStateException("Polyline drawing is supported only on Renderer2D.COLOR.");
        }
        if (points == null || pointCount < 2) return;
        double t = Math.max(0.0, thickness);
        if (t <= 0.0) return;

        path(UiShape.polyline(points, pointCount, closed), UiPaint.linear(startArgb, endArgb, angleDeg, offsetPx),
                roundCapsAndJoins ? UiStroke.of(t).withRoundCapsAndJoins() : UiStroke.of(t), false);
        boolean auto = beginAutoBatch();
        DrawBatch batch = UI_BATCHER.getOrCreate(UiBatchType.COLORED, null, null);
        if (batch == null) {
            endAutoBatch(auto);
            return;
        }
        MeshBuilder mesh = batch.mesh;
        mesh.alpha = alpha;
        appendPolylineLinearGradient(mesh, points, pointCount, t, closed,
                x, y, width, height, startArgb, endArgb, angleDeg, offsetPx, roundCapsAndJoins);
        endAutoBatch(auto);
    }

    // ---- Engine integration and compatibility bridge --------------------------

    public static boolean isBatching() {
        return UI_BATCHER.isActive();
    }

    public static BatchStats getBatchStats() {
        return BATCH_STATS;
    }

    public static UiStatsSnapshot getUiStatsSnapshot() {
        return UiRenderDispatcher.statsSnapshot();
    }

    public static void beginDeferredExtractFrame() {
        UiDeferredScheduler.beginExtractFrame();
    }

    public static void endDeferredExtractFrame() {
        UiDeferredScheduler.endExtractFrame();
    }

    public static void drainDeferred2D(Deferred2DLayer layer) {
        UiDeferredScheduler.drain(layer);
    }

    public static boolean shouldDefer2DSubmit() {
        return UiDeferredScheduler.shouldDefer();
    }

    public static boolean isDeferredExtractRecording() {
        return UiDeferredScheduler.shouldDefer();
    }

    public static void withDeferredLayer(Deferred2DLayer layer, Runnable action) {
        UiDeferredScheduler.withLayer(layer, action);
    }

    public static void deferRenderThreadAction(Runnable action) {
        UiDeferredScheduler.deferAction(action);
    }

    public static void enqueueDeferred2D(Deferred2DSubmit submit) {
        UiDeferredScheduler.enqueue(submit);
    }

    public static Deferred2DLayer deferredLayerForCurrentPhase(boolean pureItemOverlaySubmit) {
        return UiDeferredScheduler.layerForCurrentPhase(pureItemOverlaySubmit);
    }

    public static OrderedUiBatcher obtainDeferredBatcher() {
        return UiDeferredScheduler.obtainBatcher();
    }

    public static void releaseDeferredBatcher(OrderedUiBatcher batcher) {
        UiDeferredScheduler.releaseBatcher(batcher);
    }

    public static ViewportContext snapshotViewport() {
        return UiDeferredScheduler.snapshotViewport();
    }

    private static void beginUiLayer() {
        UiRenderDispatcher.beginLayer();
    }

    private static void recordUi(UiCommand command) {
        UiRenderDispatcher.record(command);
    }

    public static void flushUiLayer() {
        UiRenderDispatcher.flushLayer();
    }

    public static void recordBackendCommand(UiBatchType type) {
        UiRenderDispatcher.recordBackendCommand(type);
    }

    public static void recordBackendCommand(String batchType) {
        UiRenderDispatcher.recordBackendCommand(batchType);
    }

    public static boolean enqueueTextMesh(String label,
                                          GlyphFont font,
                                          MeshBuilder sourceMesh,
                                          RenderPipeline pipeline,
                                          TextPlacementMode placement) {
        return UiRenderDispatcher.enqueueTextMesh(label, font, sourceMesh, pipeline, placement, false);
    }

    public static boolean enqueueLiquidGlassTextMesh(String label,
                                                     GlyphFont font,
                                                     MeshBuilder sourceMesh,
                                                     RenderPipeline pipeline,
                                                     TextPlacementMode placement) {
        return UiRenderDispatcher.enqueueTextMesh(label, font, sourceMesh, pipeline, placement, true);
    }

    public static boolean submitLiquidGlassTextMeshImmediate(String label,
                                                             GlyphFont font,
                                                             MeshBuilder sourceMesh,
                                                             RenderPipeline pipeline,
                                                             TextPlacementMode placement) {
        return UiRenderDispatcher.submitLiquidGlassTextMeshImmediate(label, font, sourceMesh, pipeline, placement);
    }

    public static RenderWarpStack.Scope pushWarp(RenderWarp warp) {
        return RenderWarpStack.push(warp);
    }

    public static RenderWarpStack.Scope pushAngularWarp(UiAngularPreset preset,
                                                        float x,
                                                        float y,
                                                        float width,
                                                        float height,
                                                        float cut) {
        return RenderWarpStack.push(preset != null
                ? preset.warp(x, y, width, height, cut)
                : RenderWarp.IDENTITY);
    }

    public static RenderWarpStack.Scope pushPerspectiveWarp(float x,
                                                            float y,
                                                            float width,
                                                            float height,
                                                            float yawDeg,
                                                            float pitchDeg,
                                                            float rollDeg,
                                                            float depth,
                                                            float perspective,
                                                            float scale) {
        return RenderWarpStack.push(RenderWarp.perspective(
                x, y, width, height, yawDeg, pitchDeg, rollDeg, depth, perspective, scale
        ));
    }

    /** Flushes pending UI work without ending the current ordered batch. */
    public static void flushBatch() {
        flushBatch(FlushReason.EXPLICIT);
    }

    public static void flushBatch(FlushReason reason) {
        UiRenderDispatcher.flushBatch(reason);
    }

    public static boolean isFlushingBatch() {
        return UiRenderDispatcher.isFlushingBatch();
    }

    public static float getUnscaledItemRatio() {
        float scaleFactor = ViewportContext.getScaleFactor();
        if (scaleFactor == 0.0f) {
            return 1.0f;
        }
        return ViewportContext.getUiScale() / scaleFactor;
    }

    public static TextureTarget ensureUiEffects(Minecraft mc) {
        return UiBlurResources.ensureEffects(mc);
    }

    public static TextureTarget ensureUiGlassSource(Minecraft mc) {
        return UiBlurResources.ensureGlassSource(mc);
    }

    public static boolean copyMainColorToGlassSource(RenderTarget source, TextureTarget target) {
        return UiBlurResources.copyMainColor(source, target);
    }

    public static void requestLiquidGlassBlur() {
        UiBlurResources.requestLiquidGlassBlur();
    }

    public static void requestLiquidGlassBlurBeforeNextShapeClip() {
        UiBlurResources.requestBeforeNextShapeClip();
    }

    public static void prepareLiquidGlassBlurBeforeShapeClipIfRequested() {
        UiBlurResources.prepareBeforeShapeClipIfRequested();
    }

    public static void captureWorldGlassSource() {
        UiBlurResources.captureWorldSource();
        uiGlassWorldSourceReady = UiBlurResources.isWorldSourceReady();
    }

    public static void invalidateWorldGlassSource() {
        UiBlurResources.invalidateWorldSource();
        uiGlassWorldSourceReady = false;
    }

    public static boolean isWorldGlassSourceReady() {
        return UiBlurResources.isWorldSourceReady();
    }

    public static TextureTarget ensureUiBlurEffects(Minecraft mc) {
        return UiBlurResources.ensureKawaseDown(mc, 0);
    }

    public static TextureTarget ensureUiBlurEffectsAlt(Minecraft mc) {
        return UiBlurResources.ensureKawaseUp(mc, 0);
    }

    public static TextureTarget ensureUiKawaseDown(Minecraft mc, int level) {
        return UiBlurResources.ensureKawaseDown(mc, level);
    }

    public static TextureTarget ensureUiKawaseUp(Minecraft mc, int level) {
        return UiBlurResources.ensureKawaseUp(mc, level);
    }

    public static MeshBuilder ensureUiCompositeMesh(int width, int height) {
        return UiBlurResources.ensureCompositeMesh(width, height);
    }

    public static long currentFrameBlurFrameId() {
        return UiBlurResources.currentFrameId();
    }

    public static RenderPhase currentFrameBlurPhase() {
        return UiBlurResources.currentPhase();
    }

    public static int blurScaleBits(float uiScale) {
        return UiBlurResources.scaleBits(uiScale);
    }

    public static @Nullable FrameBlurCacheEntry findReusableFrameBlur(
            long frameId,
            RenderPhase phase,
            @Nullable GpuTextureView sourceView,
            @Nullable GpuSampler sourceSampler,
            float screenW,
            float screenH,
            float uiScale,
            BlurQuality blurQuality,
            float offsetPx) {
        return UiBlurResources.findReusable(
                frameId, phase, sourceView, sourceSampler, screenW, screenH, uiScale, blurQuality, offsetPx
        );
    }

    public static void rememberFrameBlur(
            long frameId,
            RenderPhase phase,
            @Nullable GpuTextureView sourceView,
            @Nullable GpuSampler sourceSampler,
            @Nullable GpuTextureView blurredView,
            @Nullable GpuSampler blurredSampler,
            float screenW,
            float screenH,
            float uiScale,
            BlurQuality blurQuality,
            float offsetPx) {
        UiBlurResources.remember(
                frameId, phase, sourceView, sourceSampler, blurredView, blurredSampler,
                screenW, screenH, uiScale, blurQuality, offsetPx
        );
    }

    public enum FlushReason {
        UNKNOWN,
        EXPLICIT,
        PROJECTION,
        SCISSOR,
        VANILLA_TEXT,
        UI_TEXTURE,
        TEXTURED_DIRECT,
        AUTO_BATCH,
        RENDER_END
    }

    public enum LiquidGlassPreset {
        LIGHT(7.0f, -5.50f, 0.70f, 0.42f, 0.40f, 0.030f),
        BALANCED(11.0f, -7.50f, 0.78f, 0.52f, 0.42f, 0.040f),
        HEAVY(15.0f, -9.00f, 0.86f, 0.62f, 0.48f, 0.052f),
        HUD_SMALL(11.0f, -12.00f, 0.84f, 0.78f, 0.22f, 0.055f),
        HUD_LARGE(13.5f, -14.00f, 0.88f, 0.72f, 0.20f, 0.060f),
        HEALTH_BAR(3.25f, -18.00f, 0.52f, 0.96f, 0.055f, 0.0030f);

        private final float thicknessPx;
        private final float fresnelPower;
        private final float fresnelAlpha;
        private final float baseAlpha;
        private final float fresnelMix;
        private final float distortPx;

        LiquidGlassPreset(float thicknessPx,
                          float fresnelPower,
                          float fresnelAlpha,
                          float baseAlpha,
                          float fresnelMix,
                          float distortPx) {
            this.thicknessPx = thicknessPx;
            this.fresnelPower = fresnelPower;
            this.fresnelAlpha = fresnelAlpha;
            this.baseAlpha = baseAlpha;
            this.fresnelMix = fresnelMix;
            this.distortPx = distortPx;
        }
    }

    public static final class BlurComposite {
        private final MeshBuilder mesh;
        private final float globalAlpha;

        private BlurComposite(MeshBuilder mesh, float globalAlpha) {
            this.mesh = mesh;
            this.globalAlpha = globalAlpha;
        }

        public void rect(double x, double y, double w, double h,
                         float quality, float brightness, float alpha, int ignoredTintRgb) {
            roundedRect(x, y, w, h, 0.0f, quality, brightness, alpha, ignoredTintRgb);
        }

        public void roundedRect(double x, double y, double w, double h,
                                float radius, float quality, float brightness, float alpha, int ignoredTintRgb) {
            roundedRectCorners(x, y, w, h, radius, radius, radius, radius, quality, brightness, alpha, ignoredTintRgb);
        }

        public void roundedRectCorners(double x, double y, double w, double h,
                                       float radiusTL, float radiusTR, float radiusBR, float radiusBL,
                                       float quality, float brightness, float alpha, int ignoredTintRgb) {
            if (w <= 0.0 || h <= 0.0) return;
            float finalAlpha = clamp01(alpha * globalAlpha);
            if (finalAlpha <= 0.001f) return;

            mesh.ensureQuadCapacity();

            int a = Math.max(0, Math.min(255, Math.round(finalAlpha * 255.0f)));

            float smoothness = 2.0f;

            int i1 = mesh.vec2(x, y).local2(x, y).color(255, 255, 255, a)
                    .vec4(x, y, w, h)
                    .vec4(radiusTL, radiusTR, radiusBR, radiusBL)
                    .vec4(quality, brightness, smoothness, 0.0f)
                    .next();
            int i2 = mesh.vec2(x, y + h).local2(x, y + h).color(255, 255, 255, a)
                    .vec4(x, y, w, h)
                    .vec4(radiusTL, radiusTR, radiusBR, radiusBL)
                    .vec4(quality, brightness, smoothness, 0.0f)
                    .next();
            int i3 = mesh.vec2(x + w, y + h).local2(x + w, y + h).color(255, 255, 255, a)
                    .vec4(x, y, w, h)
                    .vec4(radiusTL, radiusTR, radiusBR, radiusBL)
                    .vec4(quality, brightness, smoothness, 0.0f)
                    .next();
            int i4 = mesh.vec2(x + w, y).local2(x + w, y).color(255, 255, 255, a)
                    .vec4(x, y, w, h)
                    .vec4(radiusTL, radiusTR, radiusBR, radiusBL)
                    .vec4(quality, brightness, smoothness, 0.0f)
                    .next();
            mesh.quad(i1, i2, i3, i4);
        }
    }

    public static final class QuadBatch {
        private final MeshBuilder mesh;

        private QuadBatch(MeshBuilder mesh) {
            this.mesh = mesh;
        }

        public void quad(double x, double y, double width, double height, int argb) {
            quad(x, y, width, height, argb, argb, argb, argb);
        }

        public void quad(double x, double y, double width, double height,
                         int cTopLeft, int cTopRight, int cBottomRight, int cBottomLeft) {
            appendColoredQuad(mesh, x, y, width, height, cTopLeft, cTopRight, cBottomRight, cBottomLeft);
        }
    }

    public static final class BatchStats extends UiBatchStats {
    }
}
