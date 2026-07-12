/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.nondraggable.impl;

import combatant.client.config.SettingDef;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.runtime.RuntimeGate;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.util.List;

@HudElementRegister(order = 25)
public final class SwapTooltip extends AbstractHudElement {

    public static final SwapTooltip INSTANCE = new SwapTooltip();

    private static final float PAD_X = 5.0f;
    private static final float PAD_Y = 3.8f;
    private static final float EXTRA_OFFSET_NO_BARS = 14.0f;
    private static final int GLASS_TINT = 0xFFFFFFFF;
    private static final int GLASS_VEIL = 0x2A141414;
    private static final float GLASS_THICKNESS = 11.0f;
    private static final float GLASS_FRESNEL_POWER = -18.0f;
    private static final float GLASS_FRESNEL_ALPHA = 1.0f;
    private static final float GLASS_BASE_ALPHA = 0.82f;
    private static final float GLASS_FRESNEL_MIX = 0.38f;
    private static final float GLASS_DISTORT = 0.135f;

    private static String text;
    private static int color;
    private static float targetAlpha;
    private static boolean hasBars;

    private final NumberValue<Float> scale =
            num("swap_scale", 0.24f, 0.2f, 1.0f);
    private final NumberValue<Float> offsetY =
            num("swap_offset_y", 66.6f, 30.0f, 120.0f);
    private final EnumValue<TextEffect> textEffect =
            enumSetting("swap_text_effect", TextEffect.MIX, TextEffect.values());
    private final NumberValue<Integer> textEffectSpeed =
            visibleWhen(num("swap_text_effect_speed", 18, 1, 60), () -> textEffect.get() != TextEffect.NONE);

    private SwapTooltip() {
        super("swap_tooltip", "Swap Tooltip", true);
    }

    public static SwapTooltip get() {
        return INSTANCE;
    }

    public static void capture(String value, int argb, float fade, boolean barsVisible) {
        if (value == null || value.isEmpty()) {
            clear();
            return;
        }
        text = value;
        color = argb;
        targetAlpha = Math.max(0.0f, Math.min(1.0f, fade));
        hasBars = barsVisible;
    }

    public static void clear() {
        targetAlpha = 0.0f;
    }

    public static boolean hasWork() {
        return text != null && INSTANCE.useSwapTooltip();
    }

    public static void render() {
        if (text == null) return;

        SwapTooltip settings = INSTANCE;
        if (!settings.useSwapTooltip()) {
            resetState();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            resetState();
            return;
        }

        TextRenderer tr = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, TextRenderer.get());
        float uiScale = HudScale.scale(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        float textScale = settings.scale.get() * uiScale * 1.1f;
        float padX = PAD_X * uiScale;
        float padY = PAD_Y * uiScale;

        tr.begin(textScale, false, false);
        float textW = (float) tr.getWidth(text, false);
        float textH = (float) tr.getHeight(false);
        tr.end();

        float totalW = textW + padX * 2.0f;
        float totalH = textH + padY * 2.0f;
        float radius = Math.max(4.5f, totalH * 0.28f);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float x = (screenW - totalW) * 0.5f;
        float y = screenH - settings.offsetY.get();
        if (!hasBars) {
            y += EXTRA_OFFSET_NO_BARS;
        }

        float alpha = Math.max(0.0f, Math.min(1.0f, targetAlpha));
        if (alpha <= 0.01f && targetAlpha <= 0.0f) {
            resetState();
            return;
        }

        float fbScale = ViewportContext.getScaleFactor();
        float xb = x * fbScale;
        float yb = y * fbScale;
        float wb = totalW * fbScale;
        float hb = totalH * fbScale;
        float rb = radius * fbScale;

        ViewportContext.unscaledProjection();
        drawGlass(xb, yb, wb, hb, rb, alpha);
        Renderer2D.COLOR.roundedRect(
                xb,
                yb,
                wb,
                hb,
                rb,
                1.0f,
                HudRenderUtil.scaleAlpha(GLASS_VEIL, alpha)
        );

        ViewportContext.scaledProjection();
        tr.begin(textScale, false, false);
        int textColor = HudRenderUtil.scaleAlpha(color != 0 ? color : 0xFFFFFFFF, alpha);
        renderText(tr, settings, x + padX, y + padY, textColor, alpha);
        tr.end();

        ViewportContext.unscaledProjection();
    }

    private static void renderText(TextRenderer tr,
                                   SwapTooltip settings,
                                   float x,
                                   float y,
                                   int textColor,
                                   float alpha) {
        TextEffect effect = settings.textEffect.get();
        if (effect == TextEffect.NONE || alpha < 0.98f) {
            tr.render(text, x, y, new RenderColor(textColor), true);
            return;
        }

        int textAlpha = (textColor >>> 24) & 0xFF;
        int baseColor = (textColor & 0x00FFFFFF) | (textAlpha << 24);
        int darkGray = (textAlpha << 24) | 0x00181B1F;
        int lightGray = (textAlpha << 24) | 0x00F0F2F5;
        int darkColor = HudRenderUtil.mixColor(baseColor, darkGray, 0.22f);
        int lightColor = HudRenderUtil.mixColor(baseColor, lightGray, 0.20f);
        float time = (float) (Util.getMillis() / 1000.0);
        float speed = Math.max(0.1f, settings.textEffectSpeed.get() * 0.08f);
        float basePhase = time * speed;

        tr.renderGradient(text, x, y, (index, codePoint, glyphX, out) -> {
            float t0;
            float t1;
            switch (effect) {
                case FLOW -> {
                    float phase = basePhase + index * 0.35f;
                    t0 = 0.5f + 0.5f * (float) Math.sin(phase);
                    t1 = 0.5f + 0.5f * (float) Math.sin(phase + 1.6f);
                }
                case PULSE -> {
                    float pulse = 0.5f + 0.5f * (float) Math.sin(basePhase * 1.6f);
                    t0 = pulse;
                    t1 = pulse;
                }
                case STRIPE -> {
                    float stripe = 0.5f + 0.5f * (float) Math.sin((basePhase + index * 0.55f) * 2.4f);
                    t0 = stripe;
                    t1 = 1.0f - stripe;
                }
                case MIX -> {
                    float phase = basePhase + index * 0.55f;
                    t0 = 0.5f + 0.5f * (float) Math.sin(phase);
                    t1 = 0.5f + 0.5f * (float) Math.sin(phase + 0.85f);
                }
                default -> {
                    t0 = 0.0f;
                    t1 = 0.0f;
                }
            }
            out[0] = HudRenderUtil.mixColor(darkColor, lightColor, t0);
            out[1] = HudRenderUtil.mixColor(darkColor, lightColor, t1);
        }, true);
    }

    private static void drawGlass(float x, float y, float w, float h, float radius, float alpha) {
        if (alpha <= 0.001f || w <= 0.0f || h <= 0.0f) return;
        Renderer2D.COLOR.liquidGlassRect(
                x,
                y,
                w,
                h,
                radius,
                GLASS_THICKNESS,
                GLASS_TINT,
                alpha,
                GLASS_FRESNEL_POWER,
                GLASS_FRESNEL_ALPHA,
                GLASS_BASE_ALPHA,
                GLASS_FRESNEL_MIX,
                GLASS_DISTORT,
                0.0f
        );
    }

    private static void resetState() {
        text = null;
        targetAlpha = 0.0f;
        hasBars = false;
    }

    private boolean useSwapTooltip() {
        return !RuntimeGate.isPanic() && isEnabled();
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(0, enabledSettingDef());
    }

    public enum TextEffect implements EnumValue.IdProvider {
        NONE("None"),
        MIX("Mix"),
        FLOW("Flow"),
        PULSE("Pulse"),
        STRIPE("Stripe");

        private final String id;

        TextEffect(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }
}
