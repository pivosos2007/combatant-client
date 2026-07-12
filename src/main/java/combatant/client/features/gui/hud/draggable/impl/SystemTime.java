/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.values.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import combatant.client.features.gui.hud.HudElementInfo;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.HudTextEffects;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.script.CompactHudStatModel;
import combatant.client.features.gui.hud.script.ScriptableHudStatWidget;
import combatant.client.features.gui.hud.script.ScriptedCompactHudStatRenderer;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;

import java.time.LocalTime;

import static combatant.client.features.theme.Theme.theme;

@HudElementInfo(
        id = "system_time",
        displayName = "System Time",
        enabledByDefault = false,
        order = 170
)
public final class SystemTime extends DraggableHudElement implements ScriptableHudStatWidget {

    private static final float SCALE_MULT = 0.68f;
    private static final float BOX_HEIGHT = 20f;
    private static final float BOX_RADIUS = 5f;
    private static final float BOX_SOFTNESS = 1.0f;
    private static final float STROKE_WIDTH = 0.55f;
    private static final float ICON_X = 5f;
    private static final float DIVIDER_X = 21f;
    private static final float DIVIDER_Y = 4f;
    private static final float DIVIDER_WIDTH = 0.6f;
    private static final float TEXT_LEFT_X = 26f;
    private static final float RIGHT_PAD = 5f;
    private static final float ICON_SCALE = 0.88f;
    private static final String WIDTH_TEMPLATE = "00:00";
    private static final String WIDTH_TEMPLATE_SECONDS = "00:00:00";
    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";
    private static final String EFFECT_GLASS = "Glass";
    private static final String[] CLOCK_ICONS = {
            iconString(0xEA10),
            iconString(0xEA14),
            iconString(0xEA15),
            iconString(0xEA16),
            iconString(0xEA17),
            iconString(0xEA18),
            iconString(0xEA19),
            iconString(0xEA1A),
            iconString(0xEA10),
            iconString(0xEA11),
            iconString(0xEA12),
            iconString(0xEA13)
    };

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final BooleanValue showSeconds = bool("system_time_seconds", false);
    private final NumberValue<Double> scale = num("system_time_scale", 2.37, 0.5, 5.0);
    private final ModeValue colorMode = mode("system_time_color_mode", "color_mode", "Theme", new String[]{COLOR_THEME, COLOR_CUSTOM});
    private final RGBColorValue iconColor = visibleWhen(colorNoAlpha("system_time_icon_color", "#FFFFFF"), this::isCustomMode);
    private final RGBColorValue valueColor = visibleWhen(colorNoAlpha("system_time_value_color", "#FFFFFF"), this::isCustomMode);
    private final ModeValue bgEffect = mode("system_time_bg_effect", "bg_effect", "Blur", new String[]{EFFECT_NONE, EFFECT_BLUR, EFFECT_GLASS});
    private final RGBAColorValue bg = visibleWhen(color("system_time_bg", "#EB111318"), () -> isCustomMode() && !isGlassEffect());
    private final RGBAColorValue bg2 = visibleWhen(color("system_time_bg_secondary", "#F7161616"), () -> isCustomMode() && !isGlassEffect());
    private final RGBColorValue stroke = visibleWhen(colorNoAlpha("system_time_stroke", "#5A5A5A"), () -> isCustomMode() && !isGlassEffect());
    private final NumberValue<Integer> bgAlpha = visibleWhen(num("system_time_bg_alpha", 235, 0, 255), () -> isThemeMode() && !isGlassEffect());
    private final NumberValue<Integer> blurAlpha = visibleWhen(num("system_time_blur_alpha", 140, 0, 255), this::hasEffect);
    private final EnumValue<HudTextEffects.Effect> iconEffect =
            enumSetting("system_time_icon_effect", HudTextEffects.Effect.NONE,
                    HudTextEffects.Effect.NONE, HudTextEffects.Effect.MIX, HudTextEffects.Effect.FLOW,
                    HudTextEffects.Effect.PULSE, HudTextEffects.Effect.STRIPE);
    private final NumberValue<Integer> iconEffectSpeed =
            visibleWhen(num("system_time_icon_effect_speed", 18, 1, 60),
                    () -> iconEffect.get() != HudTextEffects.Effect.NONE);
    private final CompactHudStatModel scriptModel = new CompactHudStatModel("system_time");

    private int uiBgPrimary;
    private int uiBgSecondary;
    private int uiStroke;
    private int uiIconColor;
    private int uiValueColor;
    private float displayTimeWidth = -1f;

    private static String iconString(int codepoint) {
        return new String(Character.toChars(codepoint));
    }

    private static TextRenderer getWeatherIcons() {
        return Fonts.renderer("WeatherIcons", FontInfo.Type.Regular, TextRenderer.get());
    }

    private static String formatTime(LocalTime time, boolean showSeconds) {
        if (showSeconds) {
            return String.format("%02d:%02d:%02d", time.getHour(), time.getMinute(), time.getSecond());
        }
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    private static float smoothWidth(float current, float target) {
        if (current < 0.0f) return target;
        if (target >= current) return target;
        float next = AnimationUtility.approach(current, target, AnimationUtility.deltaTime(), 12.0f);
        return AnimationUtility.snap(next, target, 0.25f);
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        this.x = 16f;
        this.y = 48f;
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
        if (mc == null && !preview) {
            width = 0f;
            height = 0f;
            scriptModel.setVisible(false);
            return;
        }
        if (!preview && !isEnabled()) {
            width = 0f;
            height = 0f;
            scriptModel.setVisible(false);
            return;
        }

        LocalTime now = LocalTime.now();
        String icon = CLOCK_ICONS[now.getHour() % 12];
        String timeText = formatTime(now, showSeconds.get());
        updatePalette();

        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        TextRenderer iconRenderer = getWeatherIcons();
        if (iconRenderer == null) iconRenderer = fallback;
        TextRenderer valueRenderer = Fonts.renderer("Onest", FontInfo.Type.Regular, fallback);

        float drawScale = HudScale.scale(screenW, screenH)
                * (hud.getFontSize() / 18f)
                * SCALE_MULT
                * scale.get().floatValue();

        float iconScale = drawScale * ICON_SCALE;
        iconRenderer.begin(iconScale, true, false);
        float iconW = (float) iconRenderer.getWidth(icon, false);
        float iconH = (float) iconRenderer.getHeight(false);
        iconRenderer.end();

        valueRenderer.begin(drawScale, true, false);
        float timeW = (float) valueRenderer.getWidth(timeText, false);
        float timeH = (float) valueRenderer.getHeight(false);
        String widthTemplate = showSeconds.get() ? WIDTH_TEMPLATE_SECONDS : WIDTH_TEMPLATE;
        float reservedTimeW = (float) valueRenderer.getWidth(widthTemplate, false);
        valueRenderer.end();

        displayTimeWidth = Math.max(smoothWidth(displayTimeWidth, timeW), reservedTimeW);
        float boxH = BOX_HEIGHT * drawScale;
        float boxW = TEXT_LEFT_X * drawScale + displayTimeWidth + RIGHT_PAD * drawScale;
        width = boxW;
        height = boxH;

        float baseX = x;
        float baseY = y;
        float iconX = baseX + ICON_X * drawScale;
        float iconY = baseY + (boxH - iconH) * 0.5f;
        float dividerX = baseX + DIVIDER_X * drawScale;
        float dividerY = baseY + DIVIDER_Y * drawScale;
        float dividerW = Math.max(0.5f, DIVIDER_WIDTH * drawScale);
        float dividerH = Math.max(0.5f, boxH - 2f * DIVIDER_Y * drawScale);
        float textX = baseX + TEXT_LEFT_X * drawScale;
        float textY = baseY + (boxH - timeH) * 0.5f;

        int resolvedIconColor = uiIconColor;
        boolean useIconEffect = iconEffect.get() != HudTextEffects.Effect.NONE;
        float nowSec = useIconEffect ? (float) (Util.getMillis() / 1000.0) : 0.0f;
        if (useIconEffect) {
            resolvedIconColor = HudTextEffects.animatedColor(resolvedIconColor, iconEffect.get(),
                    iconEffectSpeed.get(), nowSec, 0.0f);
        }

        float radius = BOX_RADIUS * drawScale;
        scriptModel.setVisible(true);
        scriptModel.setRoot(baseX, baseY, boxW, boxH, radius, drawScale);
        scriptModel.background().set(bgEffect.get(), isThemeMode(), blurAlpha.get() / 255.0f,
                uiBgPrimary, uiBgSecondary, uiStroke, Math.max(0.5f, STROKE_WIDTH * drawScale), BOX_SOFTNESS);
        scriptModel.icon().glyph(icon, "WeatherIcons", iconX - baseX, iconY - baseY, iconW, iconH, iconScale, resolvedIconColor);
        scriptModel.divider().set(dividerX - baseX, dividerY - baseY, dividerW, dividerH,
                HudRenderUtil.setAlpha(uiValueColor, 0x52));
        scriptModel.value().set(timeText, "Onest", drawScale, textX - baseX, textY - baseY, displayTimeWidth, uiValueColor);
        scriptModel.unit().hidden();
        scriptModel.extra().hidden();
        scriptModel.animation().setLabelEffect(iconEffect.get().name(), iconEffectSpeed.get(), nowSec)
                .setDigitAnimation(false, "", 1.0f, 0.0f);
        scriptModel.data("timeText", timeText)
                .data("clockIcon", icon)
                .data("showSeconds", showSeconds.get())
                .data("hour", now.getHour())
                .data("minute", now.getMinute())
                .data("second", now.getSecond());
        ScriptedCompactHudStatRenderer.INSTANCE.render(scriptModel, renderer, fallback, ctx, tickDelta);
    }

    @Override
    public CompactHudStatModel compactStatModel() {
        return scriptModel;
    }

    private void updatePalette() {
        if (isThemeMode()) {
            int panelAlpha = bgAlpha.get();
            uiBgPrimary = HudRenderUtil.setAlpha(theme().windowBg(), panelAlpha);
            uiBgSecondary = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().surface(), theme().windowHeader(), 0.35f),
                    panelAlpha
            );
            uiStroke = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().windowStroke(), theme().strokeSoft(), 0.4f),
                    Math.min(panelAlpha, 190)
            );
            uiIconColor = theme().textPrimary();
            uiValueColor = theme().textPrimary();
            return;
        }

        uiBgPrimary = bg.getArgb();
        uiBgSecondary = bg2.getArgb();
        uiStroke = stroke.getArgb();
        uiIconColor = iconColor.getArgb();
        uiValueColor = valueColor.getArgb();
    }

    private boolean isThemeMode() {
        return COLOR_THEME.equals(colorMode.get());
    }

    private boolean isCustomMode() {
        return COLOR_CUSTOM.equals(colorMode.get());
    }

    private boolean isGlassEffect() {
        return EFFECT_GLASS.equals(bgEffect.get());
    }

    private boolean hasEffect() {
        return !EFFECT_NONE.equals(bgEffect.get());
    }
}
