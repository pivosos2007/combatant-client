/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.config.values.RGBColorValue;
import combatant.client.features.gui.hud.HudElementInfo;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.Timer;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;

import static combatant.client.features.theme.Theme.theme;

@HudElementInfo(
        id = "timer_indicator",
        displayName = "Timer",
        enabledByDefault = false,
        order = 190
)
public final class TimerIndicator extends DraggableHudElement {

    {
        defaultLayout(1242.2065f, 889.5111f);
    }


    private static final float TH_BASE_W = 60f;
    private static final float TH_BASE_H = 10f;
    private static final float TH_RADIUS = 3f;

    private final HudGlobalConfig hud = HudGlobalConfig.get();

    private final NumberValue<Double> scale = num("scale", 1.0, 0.5, 5.0);
    private final BooleanValue alwaysShow = bool("always_show", true);
    private final BooleanValue syncTheme = bool("sync_theme", true);
    private final BooleanValue blur = bool("blur", false);
    private final NumberValue<Integer> blurAlpha = visibleWhen(num("blur_alpha", 140, 0, 255), blur::get);
    private final NumberValue<Integer> blurBgAlpha = visibleWhen(num("blur_bg_alpha", 128, 0, 255), blur::get);
    private final BooleanValue gradient = visibleWhen(bool("gradient", true), syncTheme::get);
    private final RGBAColorValue bgColor = visibleWhen(color("bg", "#B012151B"), () -> !syncTheme.get());
    private final RGBColorValue barStart = visibleWhen(colorNoAlpha("bar_start", "#5CC8E7"), () -> !syncTheme.get());
    private final RGBColorValue barEnd = visibleWhen(colorNoAlpha("bar_end", "#55FFFF"), () -> !syncTheme.get());
    private final RGBColorValue textColor = visibleWhen(colorNoAlpha("text_color", "#FFFFFF"), () -> !syncTheme.get());

    private static String percentText(float value) {
        int pct = Math.round(Mth.clamp(value, 0f, 1f) * 100f);
        if (pct >= 100) return "100%";
        return pct + "%";
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        this.x = 16f;
        this.y = 42f;
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx,
                             float tickDelta,
                             int screenW,
                             int screenH) {
        boolean preview = DraggableHudElementRegistry.isForceVisible();
        boolean forced = alwaysShow.get();
        boolean timerReady = isTimerIndicatorActive();
        if (!preview && !forced && (!isEnabled() || !timerReady)) {
            width = 0f;
            height = 0f;
            return;
        }

        float charge = Timer.getEnergy01();
        renderThunder(renderer, textRenderer, screenW, screenH, charge);
    }

    private void renderThunder(Renderer2D renderer,
                               TextRenderer textRenderer,
                               int screenW,
                               int screenH,
                               float charge) {
        float baseScale = HudScale.scale(screenW, screenH) * (hud.getFontSize() / 18f);
        float drawScale = baseScale * scale.get().floatValue();

        float boxW = TH_BASE_W * drawScale;
        float boxH = TH_BASE_H * drawScale;
        float radius = TH_RADIUS * drawScale;

        boolean useSync = syncTheme.get();
        boolean blurEnabled = blur.get();

        int bg = resolveBgColor(useSync, blurEnabled);
        if (blurEnabled) {
            drawBlur(x, y, boxW, boxH, radius, bg);
        }

        HudRenderUtil.drawHudBackground(renderer, x, y, boxW, boxH, radius, 1.0f, bg, useSync && gradient.get());

        int start = resolveBarStart(useSync);
        int end = resolveBarEnd(useSync);
        float fillW = boxW * Mth.clamp(charge, 0f, 1f);
        if (fillW > 0.5f) {
            renderer.roundedRectGradient(x, y, fillW, boxH, radius, 1.0f, start, end, 0f);
        }

        String text = percentText(charge);
        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        TextRenderer font = Fonts.renderer("Iosevka", FontInfo.Type.Bold, fallback);
        float textScale = drawScale * 0.9f;
        font.begin(textScale, false, false);
        float textW = (float) font.getWidth(text, false);
        float textH = (float) font.getHeight(false);
        float textX = x + (boxW - textW) * 0.5f;
        float textY = y + (boxH - textH) * 0.5f;
        font.render(text, textX, textY, new RenderColor(resolveTextColor(useSync)), false);
        font.end();

        width = boxW;
        height = boxH;
    }

    private int resolveBgColor(boolean useSync, boolean blurEnabled) {
        if (useSync) {
            if (blurEnabled) {
                int alpha = blurBgAlpha.get();
                return (alpha << 24) | (theme().windowBg() & 0x00FFFFFF);
            }
            return theme().windowBg();
        }

        if (!blurEnabled) {
            return bgColor.getArgb();
        }

        int alpha = blurBgAlpha.get();
        return (alpha << 24) | (bgColor.getArgb() & 0x00FFFFFF);
    }

    private int resolveBarStart(boolean useSync) {
        if (useSync) {
            return HudRenderUtil.setAlpha(theme().accent(), 0xFF);
        }
        return (barStart.getArgb() & 0x00FFFFFF) | 0xFF000000;
    }

    private int resolveBarEnd(boolean useSync) {
        if (useSync) {
            return HudRenderUtil.setAlpha(theme().accentSoft(), 0xFF);
        }
        return (barEnd.getArgb() & 0x00FFFFFF) | 0xFF000000;
    }

    private int resolveTextColor(boolean useSync) {
        if (useSync) {
            return theme().textPrimary();
        }
        return (textColor.getArgb() & 0x00FFFFFF) | 0xFF000000;
    }

    private void drawBlur(float x, float y, float w, float h, float radius, int tintRgb) {
        float quality = hud.getBlurRadius();
        float brightness = 1.0f;
        float alpha = blurAlpha.get() / 255f;
        Renderer2D.COLOR.blurRect(x, y, w, h, radius, quality, brightness, alpha, 0xFFFFFF);
    }

    private boolean isTimerIndicatorActive() {
        Timer timer = Modules.get(Timer.class);
        if (timer == null || !timer.isEnabled()) return false;
        Timer.Mode mode = timer.getMode();
        return mode != null && mode != Timer.Mode.NORMAL;
    }
}
