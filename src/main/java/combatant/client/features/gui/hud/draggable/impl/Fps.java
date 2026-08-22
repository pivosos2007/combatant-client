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
import net.minecraft.resources.Identifier;
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
import combatant.client.util.FastFps;

import static combatant.client.features.theme.Theme.theme;

@HudElementInfo(
        id = "fps",
        displayName = "FPS",
        enabledByDefault = true,
        order = 120
)
public final class Fps extends DraggableHudElement implements ScriptableHudStatWidget {

    private static final Identifier FPS_ICON = Identifier.fromNamespaceAndPath("combatant", "textures/hud/elements/fps.png");
    private static final float SCALE_MULT = 0.68f;
    private static final float BOX_HEIGHT = 20f;
    private static final float BOX_RADIUS = 5f;
    private static final float BOX_SOFTNESS = 1.0f;
    private static final float STROKE_WIDTH = 0.55f;
    private static final float ICON_SIZE = 12.5f;
    private static final float ICON_X = 5f;
    private static final float DIVIDER_X = 21f;
    private static final float DIVIDER_Y = 4f;
    private static final float DIVIDER_WIDTH = 0.6f;
    private static final float TEXT_LEFT_X = 26f;
    private static final float VALUE_UNIT_GAP = 2f;
    private static final float RIGHT_PAD = 5f;
    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";
    private static final long DIGIT_ANIMATION_DURATION_MS = 200L;
    private static final float DIGIT_ANIMATION_OFFSET = 8.0f;

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final NumberValue<Double> scale = num("fps_scale", 2.37, 0.5, 5.0);
    private final ModeValue colorMode = mode("fps_color_mode", "color_mode", "Theme", new String[]{COLOR_THEME, COLOR_CUSTOM});
    private final ModeValue panelStyle = visibleWhen(
            mode("fps_panel_style", HudRenderUtil.PANEL_STYLE_DEFAULT,
                    new String[]{HudRenderUtil.PANEL_STYLE_DEFAULT, HudRenderUtil.PANEL_STYLE_ACCENT,
                            HudRenderUtil.PANEL_STYLE_GRADIENT}),
            this::isThemeMode
    );
    private final NumberValue<Integer> themeGradientStrength = visibleWhen(
            num("fps_theme_gradient_strength", 72, 0, 100), this::isGradientPanelStyle);
    private final RGBColorValue iconColor = visibleWhen(colorNoAlpha("fps_icon_color", "#FFFFFF"), this::isCustomMode);
    private final RGBColorValue valueColor = visibleWhen(colorNoAlpha("fps_value_color", "#FFFFFF"), this::isCustomMode);
    private final RGBColorValue metaColor = visibleWhen(colorNoAlpha("fps_meta_color", "#9B9B9B"), this::isCustomMode);
    private final ModeValue bgEffect = mode("fps_bg_effect", "bg_effect", "Blur", new String[]{EFFECT_NONE, EFFECT_BLUR});
    private final RGBAColorValue bg = visibleWhen(color("fps_bg", "#F7343434"), () -> isCustomMode());
    private final RGBAColorValue bg2 = visibleWhen(color("fps_bg_secondary", "#F7161616"), () -> isCustomMode());
    private final BooleanValue strokeEnabled = bool("fps_stroke_enabled", false);
    private final RGBColorValue stroke = visibleWhen(colorNoAlpha("fps_stroke", "#5A5A5A"), () -> strokeEnabled.get() && isCustomMode());
    private final NumberValue<Integer> strokeAlpha = visibleWhen(num("fps_stroke_alpha", 160, 0, 255), strokeEnabled::get);
    private final BooleanValue strokeGradient = visibleWhen(bool("fps_stroke_gradient", true), () -> strokeEnabled.get() && isThemeMode());
    private final BooleanValue shadowEnabled = bool("fps_shadow_enabled", true);
    private final ModeValue shadowMode = visibleWhen(
            mode("fps_shadow_mode", HudRenderUtil.SHADOW_MODE_BLACK,
                    new String[]{HudRenderUtil.SHADOW_MODE_BLACK, HudRenderUtil.SHADOW_MODE_THEME}),
            () -> shadowEnabled.get() && isThemeMode()
    );
    private final NumberValue<Integer> themeShadowStrength = visibleWhen(
            num("fps_theme_shadow_strength", 100, 0, 100), this::isThemeShadow);
    private final NumberValue<Integer> shadowAlpha = visibleWhen(num("fps_shadow_alpha", 48, 0, 255), shadowEnabled::get);
    private final NumberValue<Integer> bgAlpha = visibleWhen(num("fps_bg_alpha", 133, 0, 255), () -> isThemeMode());
    private final NumberValue<Integer> blurAlpha = visibleWhen(num("fps_blur_alpha", 255, 0, 255), this::hasEffect);
    private final EnumValue<HudTextEffects.Effect> labelEffect =
            enumSetting("fps_label_effect", HudTextEffects.Effect.FLOW,
                    HudTextEffects.Effect.NONE, HudTextEffects.Effect.MIX, HudTextEffects.Effect.FLOW,
                    HudTextEffects.Effect.PULSE, HudTextEffects.Effect.STRIPE);
    private final NumberValue<Integer> labelEffectSpeed =
            visibleWhen(num("fps_label_effect_speed", 18, 1, 60),
                    () -> labelEffect.get() != HudTextEffects.Effect.NONE);
    private final BooleanValue digitAnimation = bool("fps_digit_animation", false);
    private final NumberValue<Integer> digitPollMs =
            visibleWhen(num("fps_digit_poll_ms", 495, 50, 1000), digitAnimation::get);
    private final CompactHudStatModel scriptModel = new CompactHudStatModel("fps");

    private String shownFps = "0";
    private String previousShownFps = "";
    private long digitAnimStartMs = 0L;
    private long lastDigitPollMs = 0L;
    private float displayValueWidth = -1f;

    private int uiBgPrimary;
    private int uiBgSecondary;
    private int uiStroke;
    private int uiIconColor;
    private int uiValueColor;
    private int uiMetaColor;

    private static String widerText(String a, String b) {
        return a != null && b != null && a.length() < b.length() ? b : a;
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
        this.y = 20f;
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

        int fps = Math.max(0, FastFps.getFps());
        long nowMs = Util.getMillis();
        String value = resolveDisplayedFps(Integer.toString(fps), nowMs);
        String unit = "FPS";
        float digitAnimProgress = 1.0f;
        if (digitAnimation.get() && digitAnimStartMs > 0L) {
            digitAnimProgress = AnimationUtility.clamp01(
                    (nowMs - digitAnimStartMs) / (float) DIGIT_ANIMATION_DURATION_MS
            );
        }
        String widthValue = digitAnimation.get() ? widerText(value, previousShownFps) : value;
        updatePalette();

        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        TextRenderer valueRenderer = Fonts.renderer("Onest", FontInfo.Type.Regular, fallback);
        TextRenderer metaRenderer = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, valueRenderer);

        float drawScale = HudScale.scale(screenW, screenH)
                * (hud.getFontSize() / 18f)
                * SCALE_MULT
                * scale.get().floatValue();

        valueRenderer.begin(drawScale, true, false);
        float valueW = (float) valueRenderer.getWidth(widthValue, false);
        float valueH = (float) valueRenderer.getHeight(false);
        valueRenderer.end();

        metaRenderer.begin(drawScale, true, false);
        float unitW = (float) metaRenderer.getWidth(unit, false);
        float unitH = (float) metaRenderer.getHeight(false);
        metaRenderer.end();

        float boxH = BOX_HEIGHT * drawScale;
        float iconSize = ICON_SIZE * drawScale;
        float valueX = x + TEXT_LEFT_X * drawScale;
        float unitGap = VALUE_UNIT_GAP * drawScale;
        displayValueWidth = smoothWidth(displayValueWidth, valueW);
        float boxW = (valueX - x) + displayValueWidth + unitGap + unitW + RIGHT_PAD * drawScale;
        width = boxW;
        height = boxH;

        float baseX = x;
        float baseY = y;
        float iconX = baseX + ICON_X * drawScale;
        float iconY = baseY + (boxH - iconSize) * 0.5f;
        float dividerX = baseX + DIVIDER_X * drawScale;
        float dividerY = baseY + DIVIDER_Y * drawScale;
        float dividerW = Math.max(0.5f, DIVIDER_WIDTH * drawScale);
        float dividerH = Math.max(0.5f, boxH - 2f * DIVIDER_Y * drawScale);
        float textY = baseY + (boxH - Math.max(valueH, unitH)) * 0.5f;
        float unitX = valueX + displayValueWidth + unitGap;

        int resolvedIconColor = uiIconColor;
        boolean useLabelEffect = labelEffect.get() != HudTextEffects.Effect.NONE;
        float time = useLabelEffect ? (float) (Util.getMillis() / 1000.0) : 0.0f;
        if (useLabelEffect) {
            resolvedIconColor = HudTextEffects.animatedColor(resolvedIconColor, labelEffect.get(),
                    labelEffectSpeed.get(), time, 0.0f);
        }

        float radius = BOX_RADIUS * drawScale;
        if (shadowEnabled.get()) {
            HudRenderUtil.drawHudShadow(
                    renderer, baseX, baseY, boxW, boxH, radius, drawScale,
                    isThemeShadow(), shadowAlpha.get(), 1.0f,
                    themeShadowStrength.get() / 100.0f
            );
        }

        scriptModel.setVisible(true);
        scriptModel.setRoot(baseX, baseY, boxW, boxH, radius, drawScale);
        scriptModel.background().set(bgEffect.get(), isThemeMode(), blurAlpha.get() / 255.0f,
                        uiBgPrimary, uiBgSecondary, uiStroke, Math.max(0.5f, STROKE_WIDTH * drawScale), BOX_SOFTNESS)
                .setStrokeControls(
                        strokeEnabled.get(), strokeAlpha.get() / 255.0f,
                        isThemeMode() && strokeGradient.get(),
                        resolveStrokeGradientStart(), resolveStrokeGradientEnd()
                );
        scriptModel.icon().texture(FPS_ICON.toString(), iconX - baseX, iconY - baseY, iconSize, iconSize, resolvedIconColor);
        scriptModel.divider().set(dividerX - baseX, dividerY - baseY, dividerW, dividerH,
                HudRenderUtil.setAlpha(uiMetaColor, 0x52));
        scriptModel.value().set(value, "Onest", drawScale, valueX - baseX, textY - baseY, displayValueWidth, uiValueColor);
        scriptModel.unit().set(unit, "OnestMedium", drawScale, unitX - baseX, textY - baseY, unitW, uiMetaColor);
        scriptModel.extra().hidden();
        scriptModel.animation().setLabelEffect(labelEffect.get().name(), labelEffectSpeed.get(), time)
                .setDigitAnimation(digitAnimation.get(), previousShownFps, digitAnimProgress, DIGIT_ANIMATION_OFFSET);
        scriptModel.data("fps", fps)
                .data("valueText", value)
                .data("unitText", unit)
                .data("shadowControlled", true);
        ScriptedCompactHudStatRenderer.INSTANCE.render(scriptModel, renderer, fallback, ctx, tickDelta);
    }

    @Override
    public boolean supportsWidgetAnchoring() {
        return true;
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
            if (isAccentPanelStyle()) {
                uiBgPrimary = HudRenderUtil.accentSurface(uiBgPrimary, 0.20f);
                uiBgSecondary = HudRenderUtil.accentSurface(uiBgSecondary, 0.28f);
            } else if (isGradientPanelStyle()) {
                float strength = themeGradientStrength.get() / 100.0f;
                HudRenderUtil.ThemeGradient gradient = HudRenderUtil.themePanelGradient(255);
                uiBgPrimary = HudRenderUtil.gradientSurface(uiBgPrimary, gradient.start(), strength);
                uiBgSecondary = HudRenderUtil.gradientSurface(uiBgSecondary, gradient.end(), strength);
            }
            uiStroke = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().windowStroke(), theme().strokeSoft(), 0.4f),
                    Math.min(panelAlpha, 190)
            );
            uiIconColor = theme().textPrimary();
            uiValueColor = theme().textPrimary();
            uiMetaColor = theme().textMuted();
            return;
        }

        uiBgPrimary = bg.getArgb();
        uiBgSecondary = bg2.getArgb();
        uiStroke = stroke.getArgb();
        uiIconColor = iconColor.getArgb();
        uiValueColor = valueColor.getArgb();
        uiMetaColor = metaColor.getArgb();
    }

    private String resolveDisplayedFps(String rawValue, long nowMs) {
        if (!digitAnimation.get()) {
            shownFps = rawValue;
            previousShownFps = rawValue;
            digitAnimStartMs = 0L;
            lastDigitPollMs = nowMs;
            return rawValue;
        }

        if (shownFps == null || shownFps.isEmpty()) {
            shownFps = rawValue;
            previousShownFps = rawValue;
            digitAnimStartMs = 0L;
            lastDigitPollMs = nowMs;
            return shownFps;
        }

        if (nowMs - lastDigitPollMs < digitPollMs.get()) {
            return shownFps;
        }

        lastDigitPollMs = nowMs;
        if (!rawValue.equals(shownFps)) {
            previousShownFps = shownFps;
            shownFps = rawValue;
            digitAnimStartMs = nowMs;
        }
        return shownFps;
    }

    private boolean isThemeMode() {
        return COLOR_THEME.equals(colorMode.get());
    }

    private boolean isAccentPanelStyle() {
        return isThemeMode() && HudRenderUtil.PANEL_STYLE_ACCENT.equals(panelStyle.get());
    }

    private boolean isGradientPanelStyle() {
        return isThemeMode() && HudRenderUtil.PANEL_STYLE_GRADIENT.equals(panelStyle.get());
    }

    private boolean isThemeShadow() {
        return shadowEnabled.get() && isThemeMode()
                && HudRenderUtil.SHADOW_MODE_THEME.equals(shadowMode.get());
    }

    private int resolveStrokeGradientStart() {
        if (!isThemeMode()) return stroke.getArgb();
        return HudRenderUtil.themeAccentGradient(255).start();
    }

    private int resolveStrokeGradientEnd() {
        if (!isThemeMode()) return stroke.getArgb();
        return HudRenderUtil.themeAccentGradient(255).end();
    }

    private boolean isCustomMode() {
        return COLOR_CUSTOM.equals(colorMode.get());
    }


    private boolean hasEffect() {
        return !EFFECT_NONE.equals(bgEffect.get());
    }
}
