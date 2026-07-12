/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.nondraggable.impl;

import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import combatant.client.config.SettingDef;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.mixininterface.IRoundedHitbox;
import combatant.client.mixins.accessors.ClickableWidgetAccessor;
import combatant.client.mixins.accessors.TextIconButtonWidgetAccessor;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.RenderWarpStack;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.text.LegacyTextUtil;
import combatant.client.util.text.TextRenderUtil;

import java.util.*;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 10)
public final class BetterButtons extends AbstractHudElement {

    public static final BetterButtons INSTANCE = new BetterButtons();
    private static final float HOVER_SPEED_IN = 20.0f;
    private static final float HOVER_SPEED_OUT = 16.0f;
    private static final float BUTTON_RADIUS = 6.0f;
    private static final float BUTTON_SOFTNESS = 1.2f;
    private static final float TEXT_SCALE_BASE = 0.42f;
    private static final float TEXT_PADDING_X = 6.0f;
    private static final float TEXT_Y_OFFSET = 0.0f;
    private static final float PARALLAX_MAX_ANGLE = 7.0f;
    private static final float PARALLAX_DEPTH = 3.5f;
    private static final float PARALLAX_PERSPECTIVE = 1.0f;
    private static final float PARALLAX_SCALE_BOOST = 0.018f;
    private static final float PARALLAX_CONTENT_SHIFT = 1.35f;
    private static final Map<AbstractWidget, Float> HOVER = new WeakHashMap<>();
    private static final Map<AbstractWidget, Long> LAST_TICK = new WeakHashMap<>();
    private static final Map<AbstractWidget, Float> MOUSE_X = new WeakHashMap<>();
    private static final Map<AbstractWidget, Float> MOUSE_Y = new WeakHashMap<>();
    private static final List<RenderCall> QUEUE = new ArrayList<>();
    private static final Deque<ScissorRect> SCISSOR_STACK = new ArrayDeque<>();
    private static GuiGraphicsExtractor LAST_CTX;
    private static Screen LAST_SCREEN;
    private final EnumValue<Style> style =
            new EnumValue<>("style", Style.DEFAULT, Style.values());
    private final RGBAColorValue gradientBaseColor =
            new RGBAColorValue("gradient_base_color", "#FF2F2929");
    private final RGBAColorValue gradientColor =
            new RGBAColorValue("gradient_color", "#FF902F2F");
    private final NumberValue<Float> gradientAngle =
            new NumberValue<>("gradient_angle", 74.25f, 0.0f, 360.0f);
    private final NumberValue<Float> gradientIntensity =
            new NumberValue<>("gradient_intensity", 0.6f, 0.0f, 1.0f);
    private final RGBAColorValue customBg =
            new RGBAColorValue("custom_bg", "#FF1B1B1B");
    private final RGBAColorValue customBgHover =
            new RGBAColorValue("custom_bg_hover", "#FF2A2A2A");
    private final RGBAColorValue customBgDisabled =
            new RGBAColorValue("custom_bg_disabled", "#FF141414");
    private final RGBAColorValue customStroke =
            new RGBAColorValue("custom_stroke", "#FF9B5DB8");
    private final RGBAColorValue customStrokeHover =
            new RGBAColorValue("custom_stroke_hover", "#FFA710B2");
    private final RGBAColorValue customStrokeDisabled =
            new RGBAColorValue("custom_stroke_disabled", "#FF2A2A2A");
    private final RGBAColorValue customText =
            new RGBAColorValue("custom_text", "#FFECECEC");
    private final RGBAColorValue customTextDisabled =
            new RGBAColorValue("custom_text_disabled", "#FF7A7A7A");
    private final NumberValue<Float> customRadius =
            new NumberValue<>("custom_radius", 6.0f, 0.0f, 20.0f);
    private final NumberValue<Float> customSoftness =
            new NumberValue<>("custom_softness", 1.2f, 0.0f, 4.0f);
    private final NumberValue<Float> customStrokeThickness =
            new NumberValue<>("custom_stroke_thickness", 0.7f, 0.1f, 3.0f);
    private final BooleanValue customGradient =
            new BooleanValue("custom_gradient", true);
    private final RGBAColorValue customGradientColor =
            new RGBAColorValue("custom_gradient_color", "#73521BA7");
    private final NumberValue<Float> customGradientAngle =
            new NumberValue<>("custom_gradient_angle", 90.0f, 0.0f, 360.0f);
    private final NumberValue<Float> customGradientIntensity =
            new NumberValue<>("custom_gradient_intensity", 0.5f, 0.0f, 1.0f);
    private final NumberValue<Float> customTextScale =
            new NumberValue<>("custom_text_scale", 1.0f, 0.6f, 1.6f);
    private final NumberValue<Float> customTextPadX =
            new NumberValue<>("custom_text_pad_x", 6.0f, 0.0f, 16.0f);

    private BetterButtons() {
        super("vanilla_buttons", "Buttons", true);
    }

    public static BetterButtons get() {
        return INSTANCE;
    }

    public static void beginFrame(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc != null ? ClientScreen.current() : null;
        if (screen != LAST_SCREEN) {
            HOVER.clear();
            LAST_TICK.clear();
            MOUSE_X.clear();
            MOUSE_Y.clear();
            LAST_SCREEN = screen;
        }
        LAST_CTX = ctx;
        QUEUE.clear();
        SCISSOR_STACK.clear();
        BetterTooltips.beginTooltipFrame();
    }

    public static GuiGraphicsExtractor getLastContext() {
        return LAST_CTX;
    }

    public static void pushScissorContext(int x1, int y1, int x2, int y2) {
        if (x2 <= x1 || y2 <= y1) return;
        SCISSOR_STACK.push(new ScissorRect(x1, y1, x2, y2));
    }

    public static void popScissorContext() {
        if (!SCISSOR_STACK.isEmpty()) {
            SCISSOR_STACK.pop();
        }
    }

    private static ScissorRect currentScissor() {
        return SCISSOR_STACK.peek();
    }

    public static void flush() {
        if (QUEUE.isEmpty()) return;
        GuiGraphicsExtractor ctx = LAST_CTX;
        if (ctx == null) {
            QUEUE.clear();
            return;
        }
        for (RenderCall call : QUEUE) {
            ScissorRect scissor = extractScissor(call);
            boolean pushed = pushScissor(scissor);
            call.render(ctx);
            if (pushed) {
                popScissor();
            }
        }
        QUEUE.clear();
    }

    private static ScissorRect extractScissor(RenderCall call) {
        if (call instanceof ButtonCall c) return c.scissor();
        if (call instanceof SliderCall c) return c.scissor();
        if (call instanceof TextIconCall c) return c.scissor();
        if (call instanceof CyclingCall c) return c.scissor();
        return null;
    }

    private static boolean pushScissor(ScissorRect scissor) {
        if (scissor == null) return false;
        float scale = ViewportContext.getScaleFactor();
        return ScissorFunction.pushScaledRect(scissor.x1, scissor.y1, scissor.x2, scissor.y2, scale);
    }

    private static void popScissor() {
        ScissorFunction.pop();
    }

    public static void captureTooltip(AbstractWidget widget, int mouseX, int mouseY) {
        if (widget == null) return;
        BetterTooltips tooltips = BetterTooltips.get();
        if (tooltips == null || !tooltips.useCustomGuiTooltips()) {
            return;
        }
        WidgetTooltipHolder state = ((ClickableWidgetAccessor) widget).combatant$getTooltipState();
        if (state == null) return;
        Tooltip tooltip = state.get();
        if (tooltip == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        List<FormattedCharSequence> lines = tooltip.toCharSequence(mc);
        if (lines == null || lines.isEmpty()) return;
        BetterTooltips.captureTooltipOrdered(lines, DefaultTooltipPositioner.INSTANCE, mouseX, mouseY);
    }

    public static boolean hasPending() {
        return !QUEUE.isEmpty();
    }

    public static boolean hasTooltip() {
        return BetterTooltips.hasTooltip();
    }

    public static void enqueueButton(AbstractWidget widget,
                                     Component text,
                                     float hover,
                                     boolean enabled,
                                     boolean focused) {
        QUEUE.add(new ButtonCall(widget, text, hover, enabled, focused, currentScissor()));
    }

    public static void enqueueSlider(AbstractWidget widget,
                                     float value,
                                     boolean dragging,
                                     float hover) {
        QUEUE.add(new SliderCall(widget, value, dragging, hover, currentScissor()));
    }

    public static void enqueueTextIconButton(SpriteIconButton widget,
                                             boolean iconOnly,
                                             float hover,
                                             boolean enabled,
                                             boolean focused) {
        QUEUE.add(new TextIconCall(widget, iconOnly, hover, enabled, focused, currentScissor()));
    }

    public static void enqueueCyclingButton(CycleButton<?> widget,
                                            Identifier icon,
                                            boolean showLabel,
                                            float hover,
                                            boolean enabled,
                                            boolean focused) {
        QUEUE.add(new CyclingCall(widget, icon, showLabel, hover, enabled, focused, currentScissor()));
    }

    public static float updateHover(AbstractWidget widget, boolean hovered) {
        if (widget != null) {
            return updateHover(widget, hovered, widget.getX() + widget.getWidth() * 0.5f, widget.getY() + widget.getHeight() * 0.5f);
        }
        return 0.0f;
    }

    public static float updateHover(AbstractWidget widget, boolean hovered, float mouseX, float mouseY) {
        if (widget == null) return 0.0f;
        if (hovered) {
            MOUSE_X.put(widget, mouseX);
            MOUSE_Y.put(widget, mouseY);
        }

        float target = hovered ? 1.0f : 0.0f;
        float current = HOVER.getOrDefault(widget, 0.0f);

        long now = Util.getMillis();
        long last = LAST_TICK.getOrDefault(widget, now);
        LAST_TICK.put(widget, now);

        float dt = Math.min(0.05f, (now - last) / 1000.0f);
        float speed = hovered ? HOVER_SPEED_IN : HOVER_SPEED_OUT;
        float lerp = 1.0f - (float) Math.exp(-speed * dt);
        current = current + (target - current) * lerp;

        HOVER.put(widget, current);
        return current;
    }

    private static float smoothHover(float hover) {
        float h = Math.max(0.0f, Math.min(1.0f, hover));
        return h * h * (3.0f - 2.0f * h);
    }

    private static ButtonParallax computeParallax(AbstractWidget widget, float hover) {
        if (widget == null || hover <= 0.001f || !widget.isActive()) {
            return ButtonParallax.NONE;
        }

        float w = Math.max(1.0f, widget.getWidth());
        float h = Math.max(1.0f, widget.getHeight());
        float mx = MOUSE_X.getOrDefault(widget, widget.getX() + w * 0.5f);
        float my = MOUSE_Y.getOrDefault(widget, widget.getY() + h * 0.5f);
        float nx = clamp((mx - widget.getX()) / w * 2.0f - 1.0f, -1.0f, 1.0f);
        float ny = clamp((my - widget.getY()) / h * 2.0f - 1.0f, -1.0f, 1.0f);
        float ease = smoothHover(hover);
        if (ease <= 0.001f) {
            return ButtonParallax.NONE;
        }

        return new ButtonParallax(
                -nx * PARALLAX_MAX_ANGLE * ease,
                ny * PARALLAX_MAX_ANGLE * ease,
                0.0f,
                1.0f + PARALLAX_SCALE_BOOST * ease,
                nx * PARALLAX_CONTENT_SHIFT * ease,
                ny * PARALLAX_CONTENT_SHIFT * ease,
                true
        );
    }

    private static RenderWarpStack.Scope pushParallaxWarp(ButtonParallax parallax, float x, float y, float w, float h) {
        if (parallax == null || !parallax.active || Math.abs(w) <= 0.0001f || Math.abs(h) <= 0.0001f) {
            return Renderer2D.pushWarp(null);
        }
        return Renderer2D.pushPerspectiveWarp(
                x, y, w, h,
                parallax.yawDeg, parallax.pitchDeg, parallax.rollDeg,
                PARALLAX_DEPTH, PARALLAX_PERSPECTIVE, parallax.scale
        );
    }

    private static float parallaxShiftX(AbstractWidget widget, float hover) {
        return computeParallax(widget, hover).shiftX;
    }

    private static float parallaxShiftY(AbstractWidget widget, float hover) {
        return computeParallax(widget, hover).shiftY;
    }

    public static void renderButton(GuiGraphicsExtractor ctx,
                                    AbstractWidget widget,
                                    Component text,
                                    float hover,
                                    boolean enabled,
                                    boolean focused) {
        renderButtonInternal(ctx, widget, text, hover, enabled, focused, ViewportContext.getScaleFactor(), true, true);
    }

    public static void renderButtonUnscaled(GuiGraphicsExtractor ctx,
                                            AbstractWidget widget,
                                            Component text,
                                            float hover,
                                            boolean enabled,
                                            boolean focused) {
        renderButtonInternal(ctx, widget, text, hover, enabled, focused, 1.0f, false, true);
    }

    private static void renderButtonInternal(GuiGraphicsExtractor ctx,
                                             AbstractWidget widget,
                                             Component text,
                                             float hover,
                                             boolean enabled,
                                             boolean focused,
                                             float scale,
                                             boolean manageProjection,
                                             boolean allowParallax) {
        if (ctx == null || widget == null) return;

        float x = widget.getX();
        float y = widget.getY();
        float w = widget.getWidth();
        float h = widget.getHeight();

        BetterButtons cfg = INSTANCE;
        Style mode = cfg.style.get();
        float radiusBase = BUTTON_RADIUS;
        float softness = 0.0f;
        float strokeSoft = 0.0f;
        if (mode == Style.GRADIENT) {
            softness = BUTTON_SOFTNESS;
            strokeSoft = BUTTON_SOFTNESS;
        } else if (mode == Style.CUSTOM) {
            radiusBase = cfg.customRadius.get();
            softness = cfg.customSoftness.get();
            strokeSoft = cfg.customSoftness.get();
        }

        float radius = Math.min(radiusBase, h * 0.5f);
        markRounded(widget, radius);

        float alpha = widget.getAlpha();
        float hoverEase = smoothHover(hover);

        float xb = x * scale;
        float yb = y * scale;
        float wb = w * scale;
        float hb = h * scale;
        float rb = radius * scale;
        float softnessPx = softness * scale;
        float strokeSoftPx = strokeSoft * scale;
        ButtonParallax parallax = allowParallax ? computeParallax(widget, hover) : ButtonParallax.NONE;

        if (manageProjection) {
            ViewportContext.beginUnscaled(null);
        }
        boolean batching = Renderer2D.isBatching();
        if (!batching) {
            Renderer2D.COLOR.begin();
        }

        try (RenderWarpStack.Scope ignored = pushParallaxWarp(parallax, xb, yb, wb, hb)) {
            if (mode == Style.DEFAULT) {
                int baseBg = enabled ? 0xFF1B1B1B : 0xFF141414;
                int hoverBg = enabled ? 0xFF2A2A2A : 0xFF141414;
                int accent = theme().accent();

                int darkBase = HudRenderUtil.mixColor(baseBg, 0xFF000000, 0.45f);
                float hoverMix = enabled ? (0.08f + 0.22f * hoverEase) : 0.0f;
                int bgBase = HudRenderUtil.mixColor(darkBase, hoverBg, hoverMix);
                bgBase = HudRenderUtil.mixColor(bgBase, accent, 0.18f * hoverEase);
                int strokeBase = HudRenderUtil.mixColor(bgBase, 0xFFFFFFFF, 0.18f);
                strokeBase = HudRenderUtil.mixColor(strokeBase, accent, 0.16f * hoverEase + (focused ? 0.06f : 0.0f));

                int bg = HudRenderUtil.scaleAlpha(bgBase, alpha);
                int stroke = HudRenderUtil.scaleAlpha(strokeBase, 0.82f * alpha);

                Renderer2D.COLOR.roundedRect(xb, yb, wb, hb, rb, 0.0f, bg);

                int gradLight = HudRenderUtil.mixColor(bg, 0xFFFFFFFF, 0.36f + 0.10f * hoverEase);
                int gradMid = HudRenderUtil.mixColor(bg, 0xFF000000, 0.03f);
                int gradDark = HudRenderUtil.mixColor(bg, 0xFF000000, 0.20f + 0.10f * hoverEase);
                if (hoverEase > 0.001f) {
                    int accentTop = HudRenderUtil.mixColor(theme().accent(), bg, 0.65f);
                    int accentBottom = HudRenderUtil.mixColor(theme().accentSoft(), bg, 0.75f);
                    float t = Math.min(1.0f, hoverEase * 0.6f);
                    gradLight = HudRenderUtil.mixColor(gradLight, accentTop, t);
                    gradDark = HudRenderUtil.mixColor(gradDark, accentBottom, t * 0.85f);
                }
                Renderer2D.COLOR.roundedRectGradientQuad(xb, yb, wb, hb, rb, 0.0f, gradLight, gradMid, gradDark, gradMid);

                float strokeThickness = 0.7f * scale;
                int strokeInner = HudRenderUtil.scaleAlpha(strokeBase, 0.9f * alpha);
                int strokeTop = HudRenderUtil.mixColor(strokeInner, 0xFFFFFFFF, 0.2f);
                int strokeBottom = HudRenderUtil.mixColor(strokeInner, 0xFF000000, 0.25f);
                Renderer2D.COLOR.roundedRectStrokeGradient(xb, yb, wb, hb, rb, 0.0f, strokeThickness, strokeTop, strokeBottom, 90f);

                float halo = (focused ? 0.45f : 0.25f) * hoverEase;
                if (enabled && halo > 0.01f) {
                    int haloColor = HudRenderUtil.scaleAlpha(strokeBase, 0.35f * halo * alpha);
                    Renderer2D.COLOR.roundedRectStroke(xb, yb, wb, hb, rb, 0.0f, strokeThickness + 1.1f * scale, haloColor);
                }
            } else if (mode == Style.GRADIENT) {
                int base = cfg.gradientBaseColor.getArgb();
                int grad = cfg.gradientColor.getArgb();
                float intensity = clamp01(cfg.gradientIntensity.get());
                float mix = clamp01(intensity + (enabled ? hoverEase * 0.2f : 0.0f));

                int start = HudRenderUtil.mixColor(base, grad, mix);
                int end = HudRenderUtil.mixColor(base, grad, intensity * 0.2f);
                if (!enabled) {
                    start = HudRenderUtil.mixColor(start, 0xFF000000, 0.2f);
                    end = HudRenderUtil.mixColor(end, 0xFF000000, 0.2f);
                }

                int strokeBase = HudRenderUtil.mixColor(base, 0xFFFFFFFF, 0.18f);
                strokeBase = HudRenderUtil.mixColor(strokeBase, grad, 0.12f * hoverEase);
                if (!enabled) {
                    strokeBase = HudRenderUtil.mixColor(strokeBase, 0xFF000000, 0.25f);
                }

                int bgStart = HudRenderUtil.scaleAlpha(start, alpha);
                int bgEnd = HudRenderUtil.scaleAlpha(end, alpha);
                int stroke = HudRenderUtil.scaleAlpha(strokeBase, 0.85f * alpha);

                Renderer2D.COLOR.roundedRectGradient(xb, yb, wb, hb, rb, softnessPx, bgStart, bgEnd, cfg.gradientAngle.get());

                float strokeThickness = 0.7f * scale;
                Renderer2D.COLOR.roundedRectStroke(xb, yb, wb, hb, rb, strokeSoftPx, strokeThickness, stroke);

                float halo = (focused ? 0.45f : 0.25f) * hoverEase;
                if (enabled && halo > 0.01f) {
                    int haloColor = HudRenderUtil.scaleAlpha(strokeBase, 0.35f * halo * alpha);
                    Renderer2D.COLOR.roundedRectStroke(xb, yb, wb, hb, rb, strokeSoftPx, strokeThickness + 1.1f * scale, haloColor);
                }
            } else {
                int base = cfg.customBg.getArgb();
                int hoverBg = cfg.customBgHover.getArgb();
                int disabledBg = cfg.customBgDisabled.getArgb();
                int bg = enabled ? HudRenderUtil.mixColor(base, hoverBg, hoverEase) : disabledBg;

                int strokeBase = enabled
                        ? HudRenderUtil.mixColor(cfg.customStroke.getArgb(), cfg.customStrokeHover.getArgb(), hoverEase)
                        : cfg.customStrokeDisabled.getArgb();

                float strokeThickness = cfg.customStrokeThickness.get() * scale;

                if (cfg.customGradient.get()) {
                    float t = clamp01(cfg.customGradientIntensity.get());
                    int grad = cfg.customGradientColor.getArgb();
                    int start = HudRenderUtil.mixColor(bg, grad, t);
                    int end = HudRenderUtil.mixColor(bg, grad, t * 0.2f);
                    int bgStart = HudRenderUtil.scaleAlpha(start, alpha);
                    int bgEnd = HudRenderUtil.scaleAlpha(end, alpha);
                    Renderer2D.COLOR.roundedRectGradient(xb, yb, wb, hb, rb, softnessPx, bgStart, bgEnd, cfg.customGradientAngle.get());
                } else {
                    int fill = HudRenderUtil.scaleAlpha(bg, alpha);
                    Renderer2D.COLOR.roundedRect(xb, yb, wb, hb, rb, softnessPx, fill);
                }

                int stroke = HudRenderUtil.scaleAlpha(strokeBase, 0.85f * alpha);
                Renderer2D.COLOR.roundedRectStroke(xb, yb, wb, hb, rb, strokeSoftPx, strokeThickness, stroke);

                float halo = (focused ? 0.45f : 0.25f) * hoverEase;
                if (enabled && halo > 0.01f) {
                    int haloColor = HudRenderUtil.scaleAlpha(strokeBase, 0.35f * halo * alpha);
                    Renderer2D.COLOR.roundedRectStroke(xb, yb, wb, hb, rb, strokeSoftPx, strokeThickness + 1.1f * scale, haloColor);
                }
            }

        }

        if (!batching) {
            Renderer2D.COLOR.render();
        }
        if (manageProjection) {
            ViewportContext.end(null);
        }

        if (text != null && !text.getString().isEmpty()) {
            try (RenderWarpStack.Scope ignored = pushParallaxWarp(parallax, x, y, w, h)) {
                renderCenteredText(text, x, y, w, h, alpha, enabled, hover, !parallax.active);
            }
        }
    }

    public static void renderSlider(GuiGraphicsExtractor ctx,
                                    AbstractWidget widget,
                                    float value,
                                    boolean dragging,
                                    float hover) {
        if (ctx == null || widget == null) return;

        float x = widget.getX();
        float y = widget.getY();
        float w = widget.getWidth();
        float h = widget.getHeight();
        float scale = ViewportContext.getScaleFactor();

        float alpha = widget.getAlpha();
        float radius = Math.min(4.0f, h * 0.5f);
        markRounded(widget, radius);

        renderButtonInternal(ctx, widget, null, hover, widget.isActive(), widget.isFocused(), scale, true, false);

        float trackInset = 8f;
        float trackX = x + trackInset;
        float trackW = Math.max(12f, w - trackInset * 2f);
        float trackH = Math.max(4f, h * 0.24f);
        float trackY = y + (h - trackH) * 0.5f;
        float fillW = trackW * value;

        float hoverEase = smoothHover(hover);
        int track = HudRenderUtil.mixColor(forceOpaque(theme().surface()), 0xFF000000, 0.65f);
        int fill = HudRenderUtil.mixColor(forceOpaque(theme().accentSoft()), forceOpaque(theme().accent()), 0.25f + 0.35f * hoverEase);
        int stroke = HudRenderUtil.mixColor(forceOpaque(theme().strokeSoft()), 0xFF000000, 0.55f);
        stroke = HudRenderUtil.mixColor(stroke, theme().accent(), 0.08f * hoverEase);

        track = HudRenderUtil.scaleAlpha(track, alpha);
        fill = HudRenderUtil.scaleAlpha(fill, alpha);
        stroke = HudRenderUtil.scaleAlpha(stroke, 0.6f * alpha);

        float trackXb = trackX * scale;
        float trackYb = trackY * scale;
        float trackWb = trackW * scale;
        float trackHb = trackH * scale;
        float fillWb = fillW * scale;

        ViewportContext.beginUnscaled(null);
        boolean batching = Renderer2D.isBatching();
        if (!batching) {
            Renderer2D.COLOR.begin();
        }
        float barRadius = Math.min(2.5f * scale, trackHb * 0.5f);
        Renderer2D.COLOR.roundedRect(trackXb, trackYb, trackWb, trackHb, barRadius, scale, track);
        if (fillW > 1f) {
            Renderer2D.COLOR.roundedRect(trackXb, trackYb, fillWb, trackHb, barRadius, scale, fill);
        }
        Renderer2D.COLOR.roundedRectStroke(trackXb, trackYb, trackWb, trackHb, barRadius, scale, scale, stroke);
        if (!batching) {
            Renderer2D.COLOR.render();
        }
        ViewportContext.end(null);

        Component text = widget.getMessage();
        renderCenteredText(text, x, y, w, h, alpha, widget.isActive(), hover);
    }

    public static void renderTextIconButton(GuiGraphicsExtractor ctx,
                                            SpriteIconButton widget,
                                            boolean iconOnly,
                                            float hover,
                                            boolean enabled,
                                            boolean focused) {
        if (ctx == null || widget == null) return;
        renderButton(ctx, widget, null, hover, enabled, focused);

        TextIconButtonWidgetAccessor accessor = (TextIconButtonWidgetAccessor) widget;
        int texW = accessor.combatant$getTextureWidth();
        int texH = accessor.combatant$getTextureHeight();
        int iconX;
        int iconY = widget.getY() + widget.getHeight() / 2 - texH / 2;
        if (iconOnly) {
            iconX = widget.getX() + widget.getWidth() / 2 - texW / 2;
        } else {
            iconX = widget.getX() + widget.getWidth() - texW - 2;
        }
        boolean hoverState = widget.isActive() && (widget.isHoveredOrFocused() || focused || hover > 0.001f);
        Identifier spriteId = accessor.combatant$getTexture().get(widget.isActive(), hoverState);
        float sx = parallaxShiftX(widget, hover);
        float sy = parallaxShiftY(widget, hover);
        drawGuiSprite(spriteId, Math.round(iconX + sx), Math.round(iconY + sy), texW, texH, widget.getAlpha());

        if (!iconOnly) {
            float left = widget.getX() + 2f;
            float right = widget.getX() + widget.getWidth() - texW - 4f;
            float top = widget.getY();
            float bottom = widget.getY() + widget.getHeight();
            Component text = widget.getMessage();
            ButtonParallax parallax = computeParallax(widget, hover);
            try (RenderWarpStack.Scope ignored = pushParallaxWarp(parallax, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight())) {
                renderLeftAlignedText(text, left, top, right, bottom, widget.getAlpha(), enabled, hover, !parallax.active);
            }
        }

    }

    public static void renderCyclingButton(GuiGraphicsExtractor ctx,
                                           CycleButton<?> widget,
                                           Identifier icon,
                                           boolean showLabel,
                                           float hover,
                                           boolean enabled,
                                           boolean focused) {
        if (ctx == null || widget == null) return;
        renderButton(ctx, widget, null, hover, enabled, focused);

        if (icon != null) {
            int size = Math.min(widget.getWidth(), widget.getHeight()) - 6;
            float sx = parallaxShiftX(widget, hover);
            float sy = parallaxShiftY(widget, hover);
            int ix = Math.round(widget.getX() + (widget.getWidth() - size) / 2 + sx);
            int iy = Math.round(widget.getY() + (widget.getHeight() - size) / 2 + sy);
            ctx.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, icon, ix, iy, size, size, widget.getAlpha());
        }

        if (showLabel) {
            Component text = widget.getMessage();
            ButtonParallax parallax = computeParallax(widget, hover);
            try (RenderWarpStack.Scope ignored = pushParallaxWarp(parallax, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight())) {
                renderCenteredText(text, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(),
                        widget.getAlpha(), enabled, hover, !parallax.active);
            }
        }
    }

    public static void renderCenteredText(Component text,
                                          float x, float y, float w, float h,
                                          float alpha, boolean enabled, float hover) {
        renderCenteredText(text, x, y, w, h, alpha, enabled, hover, true);
    }

    private static void renderCenteredText(Component text,
                                           float x, float y, float w, float h,
                                           float alpha, boolean enabled, float hover,
                                           boolean snapToPixel) {
        TextRenderer tr = Fonts.renderer("Monsterrat", FontInfo.Type.Regular, TextRenderer.get());
        if (tr == null) return;
        if (text == null || text.getString().isEmpty()) return;

        float padX = resolveTextPaddingX();
        float scale = resolveTextScaleBase() * (h / 20.0f);
        int baseText = resolveTextColor(enabled, hover);

        List<TextRenderUtil.FlatPart> parts = flattenLegacy(text, baseText);
        parts = trimParts(tr, parts, w - padX * 2f, scale, baseText);
        if (parts.isEmpty()) return;

        double tw = measurePartsWidth(tr, parts, scale);
        double th = measureHeight(tr, scale);

        float tx = (float) (x + (w - tw) * 0.5f);
        float ty = (float) (y + (h - th) * 0.5f + TEXT_Y_OFFSET);
        if (snapToPixel) {
            tx = Math.round(tx);
            ty = Math.round(ty);
        }

        tr.begin(scale, false, false);
        float cx = tx;
        for (TextRenderUtil.FlatPart part : parts) {
            if (part.text().isEmpty()) continue;
            int color = HudRenderUtil.scaleAlpha(part.color(), alpha);
            tr.render(part.text(), cx, ty, new RenderColor(color), false);
            cx += (float) tr.getWidth(part.text(), false);
        }
        tr.end();
    }

    public static void renderLeftAlignedText(Component text,
                                             float x0, float y0,
                                             float x1, float y1,
                                             float alpha, boolean enabled, float hover) {
        renderLeftAlignedText(text, x0, y0, x1, y1, alpha, enabled, hover, true);
    }

    private static void renderLeftAlignedText(Component text,
                                              float x0, float y0,
                                              float x1, float y1,
                                              float alpha, boolean enabled, float hover,
                                              boolean snapToPixel) {
        TextRenderer tr = Fonts.renderer("Monsterrat", FontInfo.Type.Regular, TextRenderer.get());
        if (tr == null) return;
        if (text == null || text.getString().isEmpty()) return;

        float w = x1 - x0;
        float h = y1 - y0;
        float padX = resolveTextPaddingX();
        float scale = resolveTextScaleBase() * (h / 20.0f);
        int baseText = resolveTextColor(enabled, hover);

        List<TextRenderUtil.FlatPart> parts = flattenLegacy(text, baseText);
        parts = trimParts(tr, parts, w - padX, scale, baseText);
        if (parts.isEmpty()) return;

        double th = measureHeight(tr, scale);
        float tx = x0 + padX * 0.5f;
        float ty = (float) (y0 + (h - th) * 0.5f + TEXT_Y_OFFSET);
        if (snapToPixel) {
            tx = Math.round(tx);
            ty = Math.round(ty);
        }

        tr.begin(scale, false, false);
        float cx = tx;
        for (TextRenderUtil.FlatPart part : parts) {
            if (part.text().isEmpty()) continue;
            int color = HudRenderUtil.scaleAlpha(part.color(), alpha);
            tr.render(part.text(), cx, ty, new RenderColor(color), false);
            cx += (float) tr.getWidth(part.text(), false);
        }
        tr.end();
    }

    private static float resolveTextScaleBase() {
        BetterButtons cfg = INSTANCE;
        if (cfg != null && cfg.style.get() == Style.CUSTOM) {
            return TEXT_SCALE_BASE * cfg.customTextScale.get();
        }
        return TEXT_SCALE_BASE;
    }

    private static float resolveTextPaddingX() {
        BetterButtons cfg = INSTANCE;
        if (cfg != null && cfg.style.get() == Style.CUSTOM) {
            return cfg.customTextPadX.get();
        }
        return TEXT_PADDING_X;
    }

    private static int resolveTextColor(boolean enabled, float hover) {
        BetterButtons cfg = INSTANCE;
        if (cfg != null && cfg.style.get() == Style.CUSTOM) {
            return enabled ? cfg.customText.getArgb() : cfg.customTextDisabled.getArgb();
        }
        return enabled ? theme().textPrimary() : theme().textMuted();
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<TextRenderUtil.FlatPart> flattenLegacy(Component text, int defaultColor) {
        Component converted = LegacyTextUtil.convertLegacyCodes(text);
        return TextRenderUtil.flatten(converted, defaultColor);
    }

    private static List<TextRenderUtil.FlatPart> trimParts(TextRenderer tr,
                                                           List<TextRenderUtil.FlatPart> parts,
                                                           float maxWidth,
                                                           float scale,
                                                           int defaultColor) {
        if (parts.isEmpty()) return parts;
        tr.begin(scale, true, false);
        try {
            double total = 0.0;
            for (TextRenderUtil.FlatPart part : parts) {
                if (!part.text().isEmpty()) {
                    total += tr.getWidth(part.text(), false);
                }
            }
            if (total <= maxWidth) return parts;

            String ellipsis = "...";
            double ellipsisWidth = tr.getWidth(ellipsis, false);
            if (maxWidth <= ellipsisWidth) {
                List<TextRenderUtil.FlatPart> out = new ArrayList<>();
                out.add(new TextRenderUtil.FlatPart(ellipsis, defaultColor));
                return out;
            }

            List<TextRenderUtil.FlatPart> out = new ArrayList<>();
            double width = 0.0;
            for (TextRenderUtil.FlatPart part : parts) {
                String s = part.text();
                if (s.isEmpty()) continue;

                double prev = 0.0;
                int keep = 0;
                for (int i = 0; i < s.length(); i++) {
                    double cur = tr.getWidth(s, i + 1, false);
                    double delta = cur - prev;
                    if (width + delta + ellipsisWidth > maxWidth) {
                        if (keep > 0) {
                            out.add(new TextRenderUtil.FlatPart(s.substring(0, keep), part.color()));
                        }
                        out.add(new TextRenderUtil.FlatPart(ellipsis, defaultColor));
                        return out;
                    }
                    width += delta;
                    prev = cur;
                    keep = i + 1;
                }

                out.add(new TextRenderUtil.FlatPart(s.substring(0, keep), part.color()));
            }

            out.add(new TextRenderUtil.FlatPart(ellipsis, defaultColor));
            return out;
        } finally {
            tr.end();
        }
    }

    private static double measurePartsWidth(TextRenderer tr, List<TextRenderUtil.FlatPart> parts, float scale) {
        tr.begin(scale, true, false);
        try {
            double width = 0.0;
            for (TextRenderUtil.FlatPart part : parts) {
                if (!part.text().isEmpty()) {
                    width += tr.getWidth(part.text(), false);
                }
            }
            return width;
        } finally {
            tr.end();
        }
    }

    private static double measureHeight(TextRenderer tr, float scale) {
        tr.begin(scale, true, false);
        try {
            return tr.getHeight(false);
        } finally {
            tr.end();
        }
    }

    private static void markRounded(AbstractWidget widget, float radius) {
        if (widget instanceof IRoundedHitbox rounded) {
            rounded.combatant$setRoundedHitbox(radius);
        }
    }

    private static int forceOpaque(int argb) {
        return (argb & 0x00FFFFFF) | 0xFF000000;
    }

    private static void drawGuiSprite(Identifier spriteId, int x, int y, int w, int h, float alpha) {
        if (spriteId == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        TextureAtlas atlas = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
        if (atlas == null) return;
        TextureAtlasSprite sprite = atlas.getSprite(spriteId);
        if (sprite == null) return;
        Identifier atlasId = atlas.location();
        if (atlasId == null) return;

        float scale = ViewportContext.getScaleFactor();
        float xb = x * scale;
        float yb = y * scale;
        float wb = w * scale;
        float hb = h * scale;
        int argb = HudRenderUtil.scaleAlpha(0xFFFFFFFF, alpha);

        ViewportContext.beginUnscaled(null);
        Renderer2D.TEXTURE.begin();
        Renderer2D.TEXTURE.texQuad(xb, yb, wb, hb, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), argb);
        Renderer2D.TEXTURE.end();
        Renderer2D.TEXTURE.render(atlasId);
        ViewportContext.end(null);
    }

    public static void renderTooltip() {
        BetterTooltips.renderTooltipWithContext(LAST_CTX);
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.mode(style));
        defs.add(SettingDef.color(gradientBaseColor).visibleWhen(this::isGradientStyle));
        defs.add(SettingDef.color(gradientColor).visibleWhen(this::isGradientStyle));
        defs.add(SettingDef.number(gradientAngle).visibleWhen(this::isGradientStyle));
        defs.add(SettingDef.number(gradientIntensity).visibleWhen(this::isGradientStyle));

        defs.add(SettingDef.color(customBg).visibleWhen(this::isCustomStyle));
        defs.add(SettingDef.color(customBgHover).visibleWhen(this::isCustomStyle));
        defs.add(SettingDef.color(customBgDisabled).visibleWhen(this::isCustomStyle));
        defs.add(SettingDef.color(customStroke).visibleWhen(this::isCustomStyle));
        defs.add(SettingDef.color(customStrokeHover).visibleWhen(this::isCustomStyle));
        defs.add(SettingDef.color(customStrokeDisabled).visibleWhen(this::isCustomStyle));
        defs.add(SettingDef.color(customText).visibleWhen(this::isCustomStyle));
        defs.add(SettingDef.color(customTextDisabled).visibleWhen(this::isCustomStyle));
        defs.add(SettingDef.number(customRadius).visibleWhen(this::isCustomStyle));
        defs.add(SettingDef.number(customSoftness).visibleWhen(this::isCustomStyle));
        defs.add(SettingDef.number(customStrokeThickness).visibleWhen(this::isCustomStyle));
        defs.add(SettingDef.bool(customGradient).visibleWhen(this::isCustomStyle));
        defs.add(SettingDef.color(customGradientColor).visibleWhen(this::isCustomGradientStyle));
        defs.add(SettingDef.number(customGradientAngle).visibleWhen(this::isCustomGradientStyle));
        defs.add(SettingDef.number(customGradientIntensity).visibleWhen(this::isCustomGradientStyle));
        defs.add(SettingDef.number(customTextScale).visibleWhen(this::isCustomStyle));
        defs.add(SettingDef.number(customTextPadX).visibleWhen(this::isCustomStyle));
    }

    public boolean useUiButtons() {
        return !RuntimeGate.isPanic() && isEnabled();
    }

    private boolean isGradientStyle() {
        return style.get() == Style.GRADIENT;
    }

    private boolean isCustomStyle() {
        return style.get() == Style.CUSTOM;
    }

    private boolean isCustomGradientStyle() {
        return isCustomStyle() && customGradient.get();
    }

    public enum Style implements EnumValue.IdProvider {
        DEFAULT("Default"),
        GRADIENT("Gradient"),
        CUSTOM("Custom");

        private final String id;

        Style(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private interface RenderCall {
        void render(GuiGraphicsExtractor ctx);
    }

    private record ButtonParallax(float yawDeg,
                                  float pitchDeg,
                                  float rollDeg,
                                  float scale,
                                  float shiftX,
                                  float shiftY,
                                  boolean active) {
        private static final ButtonParallax NONE = new ButtonParallax(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, false);
    }

    private record ButtonCall(AbstractWidget widget,
                              Component text,
                              float hover,
                              boolean enabled,
                              boolean focused,
                              ScissorRect scissor) implements RenderCall {
        @Override
        public void render(GuiGraphicsExtractor ctx) {
            BetterButtons.renderButton(ctx, widget, text, hover, enabled, focused);
        }
    }

    private record SliderCall(AbstractWidget widget,
                              float value,
                              boolean dragging,
                              float hover,
                              ScissorRect scissor) implements RenderCall {
        @Override
        public void render(GuiGraphicsExtractor ctx) {
            BetterButtons.renderSlider(ctx, widget, value, dragging, hover);
        }
    }

    private record TextIconCall(SpriteIconButton widget,
                                boolean iconOnly,
                                float hover,
                                boolean enabled,
                                boolean focused,
                                ScissorRect scissor) implements RenderCall {
        @Override
        public void render(GuiGraphicsExtractor ctx) {
            BetterButtons.renderTextIconButton(ctx, widget, iconOnly, hover, enabled, focused);
        }
    }

    private record CyclingCall(CycleButton<?> widget,
                               Identifier icon,
                               boolean showLabel,
                               float hover,
                               boolean enabled,
                               boolean focused,
                               ScissorRect scissor) implements RenderCall {
        @Override
        public void render(GuiGraphicsExtractor ctx) {
            BetterButtons.renderCyclingButton(ctx, widget, icon, showLabel, hover, enabled, focused);
        }
    }

    private record ScissorRect(int x1, int y1, int x2, int y2) {
    }
}


