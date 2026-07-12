/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;


import combatant.client.features.theme.Theme;
import com.mojang.blaze3d.platform.Window;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import combatant.client.config.SettingDef;
import combatant.client.events.impl.RenderPrewarmCollectEvent;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.theme.Themes;
import combatant.client.features.module.HudPhase;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.ModuleStateListener;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.wav.CustomSoundEngine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@HudElementRegister(order = 80)
public final class HudNotifier extends DraggableHudElement implements ModuleStateListener {

    private static final HudNotifier INSTANCE = new HudNotifier();

    private static final int MAX_TOASTS = 6;
    private static final float BASE_MARGIN = 9f;
    private static final float BASE_GAP = 3f;
    private static final float BASE_BOTTOM_OFFSET = 160f;

    private static final float BASE_RADIUS = 4f;
    private static final float BASE_BOX_HEIGHT = 16f;
    private static final float BASE_LEFT_PAD = 7f;
    private static final float BASE_RIGHT_PAD = 9f;
    private static final float BASE_ICON_PIPE_GAP = 5f;
    private static final float BASE_TOGGLE_PIPE_GAP = 4.5f;
    private static final float BASE_PIPE_TEXT_GAP = 6f;
    private static final float BASE_TOGGLE_W = 20f;
    private static final float BASE_TOGGLE_H = 12f;
    private static final float BASE_PROGRESS_H = 1.35f;
    private static final float GLASS_BLUR_ALPHA = 255f;
    private static final float GLASS_PASS_ALPHA = 255f;
    private static final float TEXT_ALPHA_FACTOR = 0.88f;
    private static final float PIPE_ALPHA_FACTOR = 0.72f;
    private static final float TEXT_DARKEN_MIN = 0.78f;
    private static final float TEXT_DARKEN_MAX = 1.00f;
    private static final float TEXT_ANIM_SPEED = 2.2f;
    private static final float LAYOUT_SMOOTH_SPEED = 14.0f;
    private static final float BASE_TOAST_SLIDE = 7.0f;
    private static final float LAYOUT_SNAP_EPS = 0.12f;

    private static final String ICON_NO = "I";
    private static final String ICON_INFO = "J";
    private static final String ICON_YES = "K";
    private static final String ICON_WARN = "L";

    private static final int ENTER_MS = 400;
    private static final int DEFAULT_EXIT_MS = 260;
    private static final long PREVIEW_ROTATE_MS = 1000L;
    private static final int TOGGLE_TRANSITION_MS = 420;
    private static final String PREVIEW_KEY = "preview";
    private static final PreviewToast[] PREVIEW_EXAMPLES = new PreviewToast[]{
            new PreviewToast("Module - enabled!", NotifyType.YES, ToastVisual.MODULE_TOGGLE, true),
            new PreviewToast("Module - disabled!", NotifyType.NO, ToastVisual.MODULE_TOGGLE, false)
    };

    private final NumberValue<Integer> durationMs = new NumberValue<>("duration_ms", 2000, 500, 6000);
    private final NumberValue<Integer> fadeMs = new NumberValue<>("fade_ms", 250, 0, 1500);
    private final NumberValue<Double> scaleValue = new NumberValue<>("scale", 1.35, 0.5, 3.0);
    private final BooleanValue soundEnabled = new BooleanValue("sound", true);
    private final ModeValue soundMode = new ModeValue("sound_mode", "2", "1", "2", "3");
    private final NumberValue<Double> soundVolume = new NumberValue<>("sound_volume", 0.47, 0.05, 1.0);

    private final Minecraft mc = Minecraft.getInstance();
    private final List<Toast> toasts = new ArrayList<>();
    private final RenderColor colorTmp = new RenderColor(0xFFFFFFFF);
    private boolean stackLayoutReady;
    private float stackWidthAnim;
    private float stackHeightAnim;

    private HudNotifier() {
        super("hud_notifier", "HudNotifier", true);
        ModuleManager.addListener(this);
    }

    public static void pushMessage(String text) {
        INSTANCE.enqueue(null, text, null, null);
    }

    public static void pushMessage(String text, NotifyType type) {
        INSTANCE.enqueue(null, text, null, type);
    }

    public static void pushState(String text, boolean enabled) {
        INSTANCE.enqueue(null, text, enabled, null);
    }

    public static void pushOrUpdateMessage(String key, String text) {
        INSTANCE.enqueue(key, text, null, null);
    }

    public static void pushOrUpdateMessage(String key, String text, NotifyType type) {
        INSTANCE.enqueue(key, text, null, type);
    }

    public static void clearMessage(String key) {
        INSTANCE.clearKey(key);
    }

    public static void renderEngine(HudPhase phase,
                                    Renderer2D renderer,
                                    TextRenderer textRenderer,
                                    GuiGraphicsExtractor ctx,
                                    float tickDelta) {
        INSTANCE.renderEngineInternal(renderer, textRenderer);
    }

    public static void renderPreview(Renderer2D renderer,
                                     TextRenderer textRenderer,
                                     float x,
                                     float y,
                                     float w,
                                     float h) {
        INSTANCE.renderPreviewInternal(renderer, textRenderer, x, y, w, h);
    }

    private static ToastPosition toastSlideOffset(MessengerSide side, float anim, float baseScale) {
        float offset = (1.0f - clamp01(anim)) * BASE_TOAST_SLIDE * baseScale;
        return switch (side) {
            case LEFT -> new ToastPosition(-offset, 0f);
            case TOP -> new ToastPosition(0f, -offset);
            case BOTTOM -> new ToastPosition(0f, offset);
            default -> new ToastPosition(offset, 0f);
        };
    }

    private static PreviewToast previewExample(long now) {
        int index = (int) ((Math.max(0L, now) / PREVIEW_ROTATE_MS) % PREVIEW_EXAMPLES.length);
        return PREVIEW_EXAMPLES[index];
    }

    private static long previewStart(long now) {
        long safeNow = Math.max(0L, now);
        return safeNow - safeNow % PREVIEW_ROTATE_MS;
    }

    private static ToastMetrics measureToast(Toast toast,
                                             TextRenderer lineRenderer,
                                             TextRenderer iconRenderer,
                                             float lineScale,
                                             float iconScale,
                                             float baseScale) {
        boolean moduleToggle = toast.visual == ToastVisual.MODULE_TOGGLE;
        String icon = iconFor(toast.type);
        float iconW = moduleToggle ? BASE_TOGGLE_W * baseScale : measureWidth(iconRenderer, icon, iconScale);
        float iconH = moduleToggle ? BASE_TOGGLE_H * baseScale : measureHeight(iconRenderer, iconScale);
        float pipeW = measureWidth(lineRenderer, "|", lineScale);
        float textW = measureWidth(lineRenderer, toast.line, lineScale);
        if (PREVIEW_KEY.equals(toast.key)) {
            textW = Math.max(textW, previewMaxTextWidth(lineRenderer, lineScale));
        }
        float textH = measureHeight(lineRenderer, lineScale);

        float leftPad = BASE_LEFT_PAD * baseScale;
        float rightPad = BASE_RIGHT_PAD * baseScale;
        float iconPipeGap = (moduleToggle ? BASE_TOGGLE_PIPE_GAP : BASE_ICON_PIPE_GAP) * baseScale;
        float pipeTextGap = BASE_PIPE_TEXT_GAP * baseScale;

        float boxW = leftPad + iconW + iconPipeGap + pipeW + pipeTextGap + textW + rightPad;
        float boxH = Math.max(BASE_BOX_HEIGHT * baseScale, Math.max(textH, iconH) + (6.0f * baseScale));
        float radius = boxH * 0.5f;
        float iconX = leftPad;
        float iconY = (boxH - iconH) * 0.5f;
        float pipeX = iconX + iconW + iconPipeGap;
        float textX = pipeX + pipeW + pipeTextGap;
        float textY = (boxH - textH) * 0.5f + (0.8f * baseScale);

        return new ToastMetrics(boxW, boxH, radius, textScale(lineScale), iconScale, iconX, iconY, iconW, iconH, pipeX, textX, textY);
    }

    private static float previewMaxTextWidth(TextRenderer renderer, float scale) {
        float max = 0f;
        for (PreviewToast preview : PREVIEW_EXAMPLES) {
            max = Math.max(max, measureWidth(renderer, preview.line(), scale));
        }
        return max;
    }

    private static float textScale(float value) {
        return value;
    }

    private static ToastPalette paletteFor() {
        int progressTrack = 0x33FFFFFF;
        int progressFill = 0xC4FFFFFF;
        int bg = 0x52000000;
        return new ToastPalette(
                bg,
                bg,
                0xFFF7FAFF,
                progressTrack,
                progressFill,
                0xFFFFFFFF
        );
    }

    private static NotifyType resolveType(String text, Boolean enabled) {
        if (enabled != null) return enabled ? NotifyType.YES : NotifyType.NO;
        String lower = text.toLowerCase();
        if (lower.contains("warn") || lower.contains("warning") || lower.contains("cooldown")) {
            return NotifyType.WARN;
        }
        if (lower.contains("error") || lower.contains("failed")
                || lower.contains("fail") || lower.contains("denied")
                || lower.contains("blocked") || lower.contains("disabled")) {
            return NotifyType.NO;
        }
        return NotifyType.INFO;
    }

    private static String iconFor(NotifyType type) {
        return switch (type) {
            case YES -> ICON_YES;
            case NO -> ICON_NO;
            case WARN -> ICON_WARN;
            case INFO -> ICON_INFO;
        };
    }

    private static int colorFor(NotifyType type) {
        return switch (type) {
            case YES -> 0xFF6DFF56;
            case NO -> 0xFFFF5656;
            case WARN -> 0xFFF5FF5E;
            case INFO -> 0xFF4AA3FF;
        };
    }

    private static int moduleToggleColor(float progress, float anim) {
        Themes.Theme theme = Theme.theme();
        int off = theme != null ? HudRenderUtil.mixColor(theme.textMuted(), theme.textPrimary(), 0.18f) : 0xFFC6D2CD;
        int on = theme != null ? theme.accent() : 0xFF5CC8E7;
        int color = HudRenderUtil.mixColor(off, on, AnimationUtility.clamp01(progress));
        return applyAlpha(color, anim);
    }

    private static String resolveLine(String text, Boolean enabled) {
        if (enabled != null) {
            return text + " - " + (enabled ? "enabled!" : "disabled!");
        }
        return text;
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float p = t - 1f;
        return 1f + c3 * p * p * p + c1 * p * p;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 0xFF) * alpha);
        return (argb & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    private static float measureWidth(TextRenderer renderer, String text, float scale) {
        if (renderer == null || text == null || text.isEmpty()) return 0f;
        renderer.begin(scale, true, false);
        float width = (float) renderer.getWidth(text, false);
        renderer.end();
        return width;
    }

    private static float measureHeight(TextRenderer renderer, float scale) {
        if (renderer == null) return 0f;
        renderer.begin(scale, true, false);
        float height = (float) renderer.getHeight(false);
        renderer.end();
        return height;
    }

    private static int scaleAlpha(int argb, float factor) {
        factor = AnimationUtility.clamp01(factor);
        int a = (argb >>> 24) & 0xFF;
        int scaledA = Math.round(a * factor);
        return (argb & 0x00FFFFFF) | ((scaledA & 0xFF) << 24);
    }

    private static int darken(int argb, float factor) {
        factor = AnimationUtility.clamp(factor, 0f, 1f);
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        r = Math.round(r * factor);
        g = Math.round(g * factor);
        b = Math.round(b * factor);

        return ((a & 0xFF) << 24)
                | ((r & 0xFF) << 16)
                | ((g & 0xFF) << 8)
                | (b & 0xFF);
    }

    private static void applyColor(RenderColor out, int argb) {
        out.a = (argb >>> 24) & 0xFF;
        out.r = (argb >>> 16) & 0xFF;
        out.g = (argb >>> 8) & 0xFF;
        out.b = argb & 0xFF;
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.number(durationMs));
        defs.add(SettingDef.number(fadeMs));
        defs.add(SettingDef.number(scaleValue));
        defs.add(SettingDef.bool(soundEnabled));
        defs.add(SettingDef.mode(soundMode).visibleWhen(soundEnabled::get));
        defs.add(SettingDef.number(soundVolume).visibleWhen(soundEnabled::get));
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        HudGlobalConfig hud = HudGlobalConfig.get();
        float scale = HudScale.scale(screenW, screenH);
        if (hud != null) {
            scale *= hud.getFontSize() / 18f;
        }
        scale *= getScale();

        float margin = BASE_MARGIN * scale;
        float width = 170f * scale;
        float height = BASE_BOX_HEIGHT * scale;
        // Position side is intentionally automatic now; the draggable position itself defines slide direction.
        this.x = Math.max(margin, screenW - margin - width);
        this.y = Math.max(margin, screenH - margin - BASE_BOTTOM_OFFSET * scale - height);
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.AFTER_SUBTITLES;
    }

    @Override
    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx,
                             float tickDelta,
                             int screenW,
                             int screenH) {
        renderEngineInternal(renderer, textRenderer);
    }

    private void renderEngineInternal(Renderer2D renderer,
                                      TextRenderer textRenderer) {
        if (RuntimeGate.isPanic()) return;
        if (mc == null || mc.level == null || mc.player == null) return;

        HudGlobalConfig hud = HudGlobalConfig.get();
        if (hud == null) return;

        boolean preview = DraggableHudElementRegistry.isForceVisible() || ClientScreen.current() instanceof ChatScreen;
        if (!enabledValue().get() && !preview) return;
        if (hud.getHeaderMode() == HudGlobalConfig.HeaderMode.NEVER && !preview) return;

        int duration = Math.max(1, getDurationMs());
        int fade = Math.max(0, getFadeMs());
        long now = System.currentTimeMillis();

        pruneExpiredToasts(now, duration);

        boolean hasRealToasts = !toasts.isEmpty();
        boolean sample = preview && !hasRealToasts;
        List<Toast> source = sample ? previewToasts(now) : toasts;

        if (source.isEmpty()) {
            resetStackLayout();
            setBounds(x, y, 0f, 0f);
            return;
        }

        Window window = mc.getWindow();
        int fbw = window.getWidth();
        int fbh = window.getHeight();
        int screenW = Math.max(1, Math.round(HudScale.virtualWidth(fbw, fbh)));
        int screenH = Math.max(1, Math.round(HudScale.virtualHeight(fbw, fbh)));

        float baseScale = HudScale.scale(screenW, screenH) * (hud.getFontSize() / 18f) * getScale();
        float gap = BASE_GAP * baseScale;

        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        TextRenderer lineRenderer = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, fallback);
        TextRenderer iconRenderer = Fonts.renderer("Icons", FontInfo.Type.Regular, fallback);
        if (lineRenderer == null) lineRenderer = fallback;
        if (iconRenderer == null) iconRenderer = fallback;
        float lineScale = baseScale * 0.95f;
        float iconScale = baseScale * 1.08f;

        List<ToastDraw> draws = new ArrayList<>();
        float maxW = 0f;
        float totalH = 0f;

        for (int i = source.size() - 1; i >= 0; i--) {
            Toast toast = source.get(i);
            float anim = sample ? 1.0f : toast.anim(now, duration, fade);
            if (anim <= 0.001f) continue;

            ToastMetrics metrics = measureToast(toast, lineRenderer, iconRenderer, lineScale, iconScale, baseScale);
            float remaining = sample ? 0.45f : toast.remaining(now, duration);
            draws.add(new ToastDraw(toast, metrics, remaining, anim, 0f, 0f));
            maxW = Math.max(maxW, metrics.boxW);
            totalH += metrics.boxH * anim;
            if (i > 0) {
                totalH += gap * anim;
            }
        }

        if (draws.isEmpty()) {
            resetStackLayout();
            setBounds(x, y, 0f, 0f);
            return;
        }

        LayoutSize layout = resolveStackLayout(maxW, totalH, sample);
        float layoutW = Math.max(maxW, layout.width());
        float layoutH = Math.max(totalH, layout.height());

        MessengerSide side = getSide();
        float cursorY = y;
        for (int i = 0; i < draws.size(); i++) {
            ToastDraw draw = draws.get(i);
            ToastMetrics metrics = draw.metrics;
            float targetX = switch (side) {
                case LEFT -> x;
                case TOP, BOTTOM -> x + (layoutW - metrics.boxW) * 0.5f;
                default -> x + (layoutW - metrics.boxW);
            };
            float targetY = cursorY;
            ToastPosition pos = resolveToastPosition(draw.toast, targetX, targetY, sample, baseScale);
            ToastPosition slide = toastSlideOffset(side, draw.anim, baseScale);
            draws.set(i, new ToastDraw(draw.toast, metrics, draw.remaining, draw.anim, pos.x() + slide.x(), pos.y() + slide.y()));
            cursorY += (metrics.boxH + gap) * draw.anim;
        }

        setBounds(x, y, layoutW, Math.max(1f, layoutH));

        drawGlassBlurBatch(draws, baseScale);
        for (ToastDraw draw : draws) {
            drawToastCard(
                    renderer,
                    draw.toast,
                    lineRenderer,
                    iconRenderer,
                    draw.x,
                    draw.y,
                    draw.metrics,
                    draw.remaining,
                    draw.anim,
                    baseScale
            );
        }
    }

    private void pruneExpiredToasts(long now, int duration) {
        Iterator<Toast> it = toasts.iterator();
        while (it.hasNext()) {
            Toast t = it.next();
            if (t.isExpired(now, duration)) {
                it.remove();
            }
        }
    }

    private LayoutSize resolveStackLayout(float targetW, float targetH, boolean sample) {
        if (sample) {
            resetStackLayout();
            return new LayoutSize(targetW, targetH);
        }

        if (!stackLayoutReady) {
            stackWidthAnim = targetW;
            stackHeightAnim = targetH;
            stackLayoutReady = true;
            return new LayoutSize(targetW, targetH);
        }

        float dt = AnimationUtility.deltaTime();
        stackWidthAnim = AnimationUtility.approach(stackWidthAnim, targetW, dt, LAYOUT_SMOOTH_SPEED);
        stackHeightAnim = AnimationUtility.approach(stackHeightAnim, targetH, dt, LAYOUT_SMOOTH_SPEED);
        stackWidthAnim = AnimationUtility.snap(stackWidthAnim, targetW, LAYOUT_SNAP_EPS);
        stackHeightAnim = AnimationUtility.snap(stackHeightAnim, targetH, LAYOUT_SNAP_EPS);
        return new LayoutSize(stackWidthAnim, stackHeightAnim);
    }

    private ToastPosition resolveToastPosition(Toast toast, float targetX, float targetY, boolean sample, float baseScale) {
        if (toast == null || sample) {
            return new ToastPosition(targetX, targetY);
        }

        if (!toast.layoutReady) {
            toast.layoutX = targetX;
            toast.layoutY = targetY;
            toast.layoutReady = true;
            return new ToastPosition(targetX, targetY);
        }

        float dt = AnimationUtility.deltaTime();
        float eps = Math.max(0.05f, LAYOUT_SNAP_EPS * baseScale);
        toast.layoutX = AnimationUtility.approach(toast.layoutX, targetX, dt, LAYOUT_SMOOTH_SPEED);
        toast.layoutY = AnimationUtility.approach(toast.layoutY, targetY, dt, LAYOUT_SMOOTH_SPEED);
        toast.layoutX = AnimationUtility.snap(toast.layoutX, targetX, eps);
        toast.layoutY = AnimationUtility.snap(toast.layoutY, targetY, eps);
        return new ToastPosition(toast.layoutX, toast.layoutY);
    }

    private void resetStackLayout() {
        stackLayoutReady = false;
        stackWidthAnim = 0f;
        stackHeightAnim = 0f;
    }

    private List<Toast> previewToasts(long now) {
        return List.of(previewToast(now));
    }

    private Toast previewToast(long now) {
        PreviewToast preview = previewExample(now);
        long start = previewStart(now);
        return new Toast(PREVIEW_KEY, preview.line(), preview.type(), start, preview.visual(), preview.enabled());
    }

    private void renderPreviewInternal(Renderer2D renderer,
                                       TextRenderer textRenderer,
                                       float originX,
                                       float originY,
                                       float width,
                                       float height) {
        if (renderer == null) return;
        HudGlobalConfig hud = HudGlobalConfig.get();
        if (hud == null) return;
        if (width <= 2f || height <= 2f) return;

        int screenW = Math.max(1, Math.round(width));
        int screenH = Math.max(1, Math.round(height));

        float baseScale = HudScale.scale(screenW, screenH) * (hud.getFontSize() / 18f) * getScale();
        float margin = BASE_MARGIN * baseScale;
        float gap = BASE_GAP * baseScale;

        MessengerSide side = getSide();
        float cursorY = margin;

        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        TextRenderer lineRenderer = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, fallback);
        TextRenderer iconRenderer = Fonts.renderer("Icons", FontInfo.Type.Regular, fallback);
        if (lineRenderer == null) lineRenderer = fallback;
        if (iconRenderer == null) iconRenderer = fallback;
        float lineScale = baseScale * 0.95f;
        float iconScale = baseScale * 1.08f;

        long now = System.currentTimeMillis();
        int duration = Math.max(1, getDurationMs());
        int fade = Math.max(0, getFadeMs());
        pruneExpiredToasts(now, duration);

        boolean sampleMode = toasts.isEmpty();
        List<Toast> source = sampleMode ? previewToasts(now) : toasts;

        List<ToastDraw> draws = new ArrayList<>();
        float maxW = 0f;
        for (int i = source.size() - 1; i >= 0; i--) {
            Toast toast = source.get(i);
            float anim = sampleMode ? 1.0f : toast.anim(now, duration, fade);
            if (anim <= 0.001f) continue;

            ToastMetrics metrics = measureToast(toast, lineRenderer, iconRenderer, lineScale, iconScale, baseScale);
            float remaining = sampleMode ? 0.45f : toast.remaining(now, duration);
            maxW = Math.max(maxW, metrics.boxW);
            draws.add(new ToastDraw(toast, metrics, remaining, anim, 0f, 0f));
        }

        for (int i = 0; i < draws.size(); i++) {
            ToastDraw draw = draws.get(i);
            ToastMetrics metrics = draw.metrics;
            float targetX = switch (side) {
                case LEFT -> originX + margin;
                case TOP, BOTTOM -> originX + (screenW - metrics.boxW) * 0.5f;
                default -> originX + screenW - margin - metrics.boxW;
            };
            float targetY = originY + cursorY;
            ToastPosition slide = toastSlideOffset(side, draw.anim, baseScale);
            draws.set(i, new ToastDraw(draw.toast, metrics, draw.remaining, draw.anim, targetX + slide.x(), targetY + slide.y()));
            cursorY += (metrics.boxH + gap) * draw.anim;
        }

        drawGlassBlurBatch(draws, baseScale);
        for (ToastDraw draw : draws) {
            drawToastCard(
                    renderer,
                    draw.toast,
                    lineRenderer,
                    iconRenderer,
                    draw.x,
                    draw.y,
                    draw.metrics,
                    draw.remaining,
                    draw.anim,
                    baseScale
            );
        }
    }

    @Override
    public void onModuleStateChanged(String name, boolean enabled) {
        Module module = ModuleManager.get(name);
        // HudNotifier module-state info logging disabled; keep only warnings/errors here.
        if (module == null) {
            // HudNotifier module-state info logging disabled; keep only warnings/errors here.
            return;
        }
        if (!module.isShownInModuleList()) {
            // HudNotifier module-state info logging disabled; keep only warnings/errors here.
            return;
        }
        enqueueModuleState(module.getDisplayName(), enabled);
    }

    private void enqueueModuleState(String text, boolean enabled) {
        enqueueInternal(null, text, enabled, enabled ? NotifyType.YES : NotifyType.NO, ToastVisual.MODULE_TOGGLE);
    }

    private void enqueue(String key, String text, Boolean enabled, NotifyType forcedType) {
        NotifyType type = forcedType != null ? forcedType : resolveType(text, enabled);
        enqueueInternal(key, text, enabled, type, ToastVisual.STANDARD);
    }

    private void enqueueInternal(String key, String text, Boolean enabled, NotifyType type, ToastVisual visual) {
        if (text == null || text.isBlank()) return;

        HudGlobalConfig hud = HudGlobalConfig.get();
        if (hud == null) return;
        if (!enabledValue().get()) return;
        if (hud.getHeaderMode() == HudGlobalConfig.HeaderMode.NEVER) return;

        String line = resolveLine(text, enabled);
        long now = System.currentTimeMillis();

        if (key != null) {
            for (Toast toast : toasts) {
                if (key.equals(toast.key)) {
                    toast.line = line;
                    toast.type = type;
                    toast.visual = visual == null ? ToastVisual.STANDARD : visual;
                    toast.stateEnabled = enabled;
                    toast.startMs = now;
                    return;
                }
            }
        }

        toasts.add(new Toast(key, line, type, now, visual, enabled));
        if (toasts.size() > MAX_TOASTS) {
            toasts.remove(0);
        }

        if (enabled != null) {
            playSound(enabled);
        }
    }

    private void drawToastCard(Renderer2D renderer,
                               Toast toast,
                               TextRenderer lineRenderer,
                               TextRenderer iconRenderer,
                               float x,
                               float y,
                               ToastMetrics metrics,
                               float remaining,
                               float anim,
                               float baseScale) {
        ToastPalette palette = paletteFor();

        int glassTint = applyAlpha(palette.glassTint, anim);
        int bgLeft = applyAlpha(palette.bgLeft, anim);
        int bgRight = applyAlpha(palette.bgRight, anim);
        long now = System.currentTimeMillis();
        float wave = 0.5f + 0.5f * (float) Math.sin(now / 1000.0f * TEXT_ANIM_SPEED);
        float darkenFactor = AnimationUtility.lerp(TEXT_DARKEN_MIN, TEXT_DARKEN_MAX, wave);

        int text = scaleAlpha(applyAlpha(palette.text, anim), TEXT_ALPHA_FACTOR);
        text = darken(text, darkenFactor);

        int progressTrack = applyAlpha(palette.progressTrack, anim);
        int progress = applyAlpha(palette.progressFill, anim);
        int accent = applyAlpha(colorFor(toast.type), anim);

        int pipe = scaleAlpha(applyAlpha(HudRenderUtil.mixColor(palette.text, palette.progressTrack, 0.55f), anim), PIPE_ALPHA_FACTOR);
        pipe = darken(pipe, darkenFactor * 0.96f);
        float sideBottomR = Math.max(1f, metrics.boxH * 0.5f);
        float sideTopR = 0f;

        drawGlassPill(
                renderer,
                x,
                y,
                metrics.boxW,
                metrics.boxH,
                sideTopR,
                sideTopR,
                sideBottomR,
                sideBottomR,
                glassTint,
                anim
        );

        int bgMixed = HudRenderUtil.mixColor(bgLeft, bgRight, 0.5f);
        int bgA = (bgMixed >>> 24) & 0xFF;
        int bgSolid = HudRenderUtil.glassBackground(anim);
        renderer.roundedRectCornersQuad(
                x,
                y,
                metrics.boxW,
                metrics.boxH,
                sideTopR,
                sideTopR,
                sideBottomR,
                sideBottomR,
                1.0f,
                bgSolid,
                bgSolid,
                bgSolid,
                bgSolid
        );

        float progressInsetX = 0.45f * baseScale;
        float progressInsetY = 0.08f * baseScale;
        float progressH = Math.max(1f, BASE_PROGRESS_H * baseScale);
        float progressY = y + progressInsetY;
        float progressX = x + progressInsetX;
        float progressAreaW = Math.max(0f, metrics.boxW - progressInsetX * 2f);
        float progressW = Math.max(0f, progressAreaW * clamp01(remaining));
        float progressTopR = 0f;
        float progressBottomR = 0.02f * baseScale;
        renderer.roundedRectCornersQuad(
                progressX,
                progressY,
                progressAreaW,
                progressH,
                progressTopR,
                progressTopR,
                progressBottomR,
                progressBottomR,
                1.0f,
                progressTrack,
                progressTrack,
                progressTrack,
                progressTrack
        );
        if (progressW > 0f) {
            renderer.roundedRectCornersQuad(
                    progressX,
                    progressY,
                    progressW,
                    progressH,
                    progressTopR,
                    progressTopR,
                    progressBottomR,
                    progressBottomR,
                    1.0f,
                    progress,
                    progress,
                    progress,
                    progress
            );
        }

        if (toast.visual == ToastVisual.MODULE_TOGGLE) {
            drawModuleToggle(renderer, toast, x + metrics.iconX, y + metrics.iconY, metrics.iconW, metrics.iconH, accent, anim, now);
        } else {
            iconRenderer.begin(metrics.iconScale, false, false);
            applyColor(colorTmp, accent);
            iconRenderer.render(iconFor(toast.type), x + metrics.iconX, y + metrics.iconY, colorTmp, false);
            iconRenderer.end();
        }

        lineRenderer.begin(metrics.textScale, false, false);
        applyColor(colorTmp, pipe);
        lineRenderer.render("|", x + metrics.pipeX, y + metrics.textY, colorTmp, false);
        applyColor(colorTmp, text);
        lineRenderer.render(toast.line, x + metrics.textX, y + metrics.textY, colorTmp, false);
        lineRenderer.end();
    }

    private void drawModuleToggle(Renderer2D renderer,
                                  Toast toast,
                                  float x,
                                  float y,
                                  float w,
                                  float h,
                                  int fallbackAccent,
                                  float anim,
                                  long now) {
        if (renderer == null || w <= 0.1f || h <= 0.1f) return;

        float progress = toast.toggleProgress(now);
        int tint = moduleToggleColor(progress, anim);
        if (((tint >>> 24) & 0xFF) <= 0) {
            tint = fallbackAccent;
        }

        float svgY = y + (h - w) * 0.5f;
        SvgRenderOptions options = SvgRenderOptions.overrideColor(tint);
        if (progress <= 0.001f) {
            renderer.svg("toggle-left", x, svgY, w, w, options);
            return;
        }
        if (progress >= 0.999f) {
            renderer.svg("toggle-right", x, svgY, w, w, options);
            return;
        }

        renderer.svg("toggle-track", x, svgY, w, w, options);

        float thumbTravel = w * (3f / 24f);
        float thumbX = x + AnimationUtility.lerp(-thumbTravel, thumbTravel, progress);
        renderer.svg("toggle-thumb", thumbX, svgY, w, w, options);
    }

    private void drawGlassBlurBatch(List<ToastDraw> draws, float baseScale) {
        if (draws == null || draws.isEmpty()) return;
        HudGlobalConfig hud = HudGlobalConfig.get();
        if (hud == null) return;

        Renderer2D.COLOR.blurComposite(composite -> {
            for (ToastDraw draw : draws) {
                if (draw == null || draw.metrics == null || draw.anim <= 0.001f) continue;
                float a = clamp01(draw.anim);
                float sideBottomR = Math.max(1f, draw.metrics.boxH * 0.5f);
                composite.roundedRectCorners(
                        draw.x,
                        draw.y,
                        draw.metrics.boxW,
                        draw.metrics.boxH,
                        0f,
                        0f,
                        sideBottomR,
                        sideBottomR,
                        hud.getBlurRadius(),
                        1.0f,
                        (GLASS_BLUR_ALPHA / 255.0f) * a,
                        0xFFFFFF
                );
            }
        });
    }

    private void drawGlassPill(Renderer2D renderer,
                               float x,
                               float y,
                               float w,
                               float h,
                               float radiusTL,
                               float radiusTR,
                               float radiusBR,
                               float radiusBL,
                               int tintArgb,
                               float anim) {
        if (renderer == null) return;
        float a = clamp01(anim);
        if (a <= 0.001f) return;

        float glassAlpha = (GLASS_PASS_ALPHA / 255.0f) * a;
        float glassScale = Math.max(0.50f, Math.min(1.25f, h / 24.0f));
        HudRenderUtil.drawLiquidGlassCorners(
                x,
                y,
                w,
                h,
                radiusTL,
                radiusTR,
                radiusBR,
                radiusBL,
                glassScale,
                false,
                glassAlpha,
                tintArgb
        );
    }

    private int getDurationMs() {
        return durationMs.get();
    }

    private int getFadeMs() {
        return fadeMs.get();
    }

    private MessengerSide getSide() {
        Window window = mc == null ? null : mc.getWindow();
        if (window == null) return MessengerSide.RIGHT;
        int fbw = Math.max(1, window.getWidth());
        int fbh = Math.max(1, window.getHeight());
        float screenW = HudScale.virtualWidth(fbw, fbh);
        float screenH = HudScale.virtualHeight(fbw, fbh);
        float cx = x + Math.max(1f, getWidth()) * 0.5f;
        float cy = y + Math.max(1f, getHeight()) * 0.5f;
        if (cy < screenH * 0.18f) return MessengerSide.TOP;
        if (cy > screenH * 0.82f) return MessengerSide.BOTTOM;
        return cx < screenW * 0.50f ? MessengerSide.LEFT : MessengerSide.RIGHT;
    }

    private float getScale() {
        return scaleValue.get().floatValue();
    }

    private float getSoundVolume() {
        return soundVolume.get().floatValue();
    }

    private void playSound(boolean enabled) {
        if (ModuleManager.isToggleSoundSuppressed()) return;
        if (!soundEnabled.get()) return;
        Identifier snd = resolveSound(enabled, soundMode.get());
        if (snd == null) return;
        double vol = Math.min(1.0, getSoundVolume());
        CustomSoundEngine.get().play(snd, vol, 1.0, false, true);
    }

    private void clearKey(String key) {
        if (key == null) return;
        toasts.removeIf(t -> key.equals(t.key));
    }

    private Identifier resolveSound(boolean enabled, String mode) {
        String base = switch (mode) {
            case "2" -> "enable/enable2";
            case "3" -> "enable/enable3";
            default -> "enable/enable1";
        };
        if (!enabled) {
            base = base.replace("enable/", "disable/").replace("enable", "disable");
        }
        return Identifier.fromNamespaceAndPath("combatant", "sounds/" + base + ".wav");
    }

    private enum MessengerSide {
        RIGHT,
        LEFT,
        TOP,
        BOTTOM;

        static MessengerSide from(String name) {
            if (name == null) return RIGHT;
            return switch (name.toLowerCase()) {
                case "left" -> LEFT;
                case "top" -> TOP;
                case "bottom" -> BOTTOM;
                default -> RIGHT;
            };
        }
    }

    public enum NotifyType {
        YES,
        NO,
        WARN,
        INFO
    }

    private enum ToastVisual {
        STANDARD,
        MODULE_TOGGLE
    }

    private static final class Toast {
        private final String key;
        private String line;
        private NotifyType type;
        private ToastVisual visual;
        private Boolean stateEnabled;
        private long startMs;
        private boolean layoutReady;
        private float layoutX;
        private float layoutY;

        private Toast(String key, String line, NotifyType type, long startMs) {
            this(key, line, type, startMs, ToastVisual.STANDARD, null);
        }

        private Toast(String key, String line, NotifyType type, long startMs, ToastVisual visual, Boolean stateEnabled) {
            this.key = key;
            this.line = line;
            this.type = type;
            this.visual = visual == null ? ToastVisual.STANDARD : visual;
            this.stateEnabled = stateEnabled;
            this.startMs = startMs;
        }

        boolean isExpired(long now, int duration) {
            return now - startMs > duration;
        }

        float remaining(long now, int duration) {
            if (duration <= 0) return 0f;
            long age = Math.max(0L, now - startMs);
            return 1f - clamp01((float) age / (float) duration);
        }

        float anim(long now, int duration, int fade) {
            long age = Math.max(0L, now - startMs);
            if (age >= duration) return 0f;

            float in = clamp01((float) age / (float) ENTER_MS);
            float enter = easeOutBack(in);

            int outMs = Math.max(1, fade > 0 ? fade : DEFAULT_EXIT_MS);
            float out = 1f;
            if (age > duration - outMs) {
                out = clamp01((float) (duration - age) / (float) outMs);
            }

            return clamp01(enter * out);
        }

        float toggleProgress(long now) {
            boolean enabled = stateEnabled != null ? stateEnabled : type == NotifyType.YES;
            long age = Math.max(0L, now - startMs);
            float t = AnimationUtility.easeInOutCubic((float) age / (float) TOGGLE_TRANSITION_MS);
            return enabled ? t : 1f - t;
        }
    }

    private record PreviewToast(String line, NotifyType type, ToastVisual visual, Boolean enabled) {
    }

    private record LayoutSize(float width, float height) {
    }

    private record ToastPosition(float x, float y) {
    }

    private record ToastDraw(Toast toast,
                             ToastMetrics metrics,
                             float remaining,
                             float anim,
                             float x,
                             float y) {
    }

    private record ToastMetrics(float boxW,
                                float boxH,
                                float radius,
                                float textScale,
                                float iconScale,
                                float iconX,
                                float iconY,
                                float iconW,
                                float iconH,
                                float pipeX,
                                float textX,
                                float textY) {
    }

    private record ToastPalette(int bgLeft,
                                int bgRight,
                                int text,
                                int progressTrack,
                                int progressFill,
                                int glassTint) {
    }
    @Override
    public void collectRenderPrewarm(RenderPrewarmCollectEvent event) {
        super.collectRenderPrewarm(event);
        event.svg("toggle-left")
                .svg("toggle-right")
                .svg("toggle-track")
                .svg("toggle-thumb");
    }

}
