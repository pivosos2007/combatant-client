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
import net.minecraft.util.Mth;
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
import combatant.client.util.player.PlayerSpeedUtil;

import static combatant.client.features.theme.Theme.theme;

//todo Description
@HudElementInfo(
        id = "speed_bps",
        displayName = "Speed BPS",
        enabledByDefault = true,
        order = 90
)
public final class SpeedBps extends DraggableHudElement implements ScriptableHudStatWidget {

    private static final Identifier SPEED_ICON = Identifier.fromNamespaceAndPath("combatant", "textures/hud/elements/speedometer.png");
    private static final float SCALE_MULT = 0.68f;
    private static final float BOX_HEIGHT = 20f;
    private static final float BOX_RADIUS = 5f;
    private static final float BOX_SOFTNESS = 1.0f;
    private static final float STROKE_WIDTH = 0.55f;
    private static final float ICON_SIZE = 11f;
    private static final float ICON_X = 5f;
    private static final float DIVIDER_X = 21f;
    private static final float DIVIDER_Y = 4f;
    private static final float DIVIDER_WIDTH = 0.6f;
    private static final float TEXT_LEFT_X = 26f;
    private static final float RIGHT_PAD = 5f;
    private static final float KMH_MULT = 3.6f;
    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";

    private static final long SAMPLE_INTERVAL_MS = 75L;
    private static final int SAMPLE_COUNT = 20;

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final BooleanValue useBps = bool("speed_bps_bps", true);
    private final BooleanValue average = bool("speed_bps_average", false);
    private final NumberValue<Double> scale = num("speed_bps_scale", 2.37, 0.5, 5.0);
    private final ModeValue colorMode = mode("speed_bps_color_mode", "color_mode", "Theme", new String[]{COLOR_THEME, COLOR_CUSTOM});
    private final ModeValue panelStyle = visibleWhen(
            mode("speed_bps_panel_style", HudRenderUtil.PANEL_STYLE_DEFAULT,
                    new String[]{HudRenderUtil.PANEL_STYLE_DEFAULT, HudRenderUtil.PANEL_STYLE_ACCENT}),
            this::isThemeMode
    );
    private final RGBColorValue iconColor = visibleWhen(colorNoAlpha("speed_bps_icon_color", "#FFFFFF"), this::isCustomMode);
    private final RGBColorValue valueColor = visibleWhen(colorNoAlpha("speed_bps_value_color", "#FFFFFF"), this::isCustomMode);
    private final RGBColorValue metaColor = visibleWhen(colorNoAlpha("speed_bps_meta_color", "#9B9B9B"), this::isCustomMode);
    private final ModeValue bgEffect = mode("speed_bps_bg_effect", "bg_effect", "Blur", new String[]{EFFECT_NONE, EFFECT_BLUR});
    private final RGBAColorValue bg = visibleWhen(color("speed_bps_bg", "#F7343434"), () -> isCustomMode());
    private final RGBAColorValue bg2 = visibleWhen(color("speed_bps_bg_secondary", "#F7161616"), () -> isCustomMode());
    private final BooleanValue strokeEnabled = bool("speed_bps_stroke_enabled", false);
    private final RGBColorValue stroke = visibleWhen(colorNoAlpha("speed_bps_stroke", "#5A5A5A"),
            () -> strokeEnabled.get() && isCustomMode());
    private final NumberValue<Integer> strokeAlpha = visibleWhen(num("speed_bps_stroke_alpha", 160, 0, 255), strokeEnabled::get);
    private final BooleanValue strokeGradient = visibleWhen(bool("speed_bps_stroke_gradient", true),
            () -> strokeEnabled.get() && isThemeMode());
    private final BooleanValue shadowEnabled = bool("speed_bps_shadow_enabled", true);
    private final ModeValue shadowMode = visibleWhen(
            mode("speed_bps_shadow_mode", HudRenderUtil.SHADOW_MODE_BLACK,
                    new String[]{HudRenderUtil.SHADOW_MODE_BLACK, HudRenderUtil.SHADOW_MODE_THEME}),
            shadowEnabled::get
    );
    private final NumberValue<Integer> shadowAlpha = visibleWhen(num("speed_bps_shadow_alpha", 48, 0, 255), shadowEnabled::get);
    private final NumberValue<Integer> bgAlpha = visibleWhen(num("speed_bps_bg_alpha", 135, 0, 255), () -> isThemeMode());
    private final NumberValue<Integer> blurAlpha = visibleWhen(num("speed_bps_blur_alpha", 255, 0, 255), this::hasEffect);
    private final EnumValue<HudTextEffects.Effect> labelEffect =
            enumSetting("speed_bps_label_effect", HudTextEffects.Effect.MIX,
                    HudTextEffects.Effect.NONE, HudTextEffects.Effect.MIX, HudTextEffects.Effect.FLOW,
                    HudTextEffects.Effect.PULSE, HudTextEffects.Effect.STRIPE);
    private final NumberValue<Integer> labelEffectSpeed =
            visibleWhen(num("speed_bps_label_effect_speed", 60, 1, 60),
                    () -> labelEffect.get() != HudTextEffects.Effect.NONE);
    private final CompactHudStatModel scriptModel = new CompactHudStatModel("speed_bps");
    private final float[] samples = new float[SAMPLE_COUNT];
    private final StringBuilder valueBuilder = new StringBuilder(16);
    private int sampleIndex = 0;
    private int sampleCount = 0;
    private long lastSampleMs = 0L;
    private float displayedSpeed = 0f;
    private int lastTenth = Integer.MIN_VALUE;
    private String cachedNumber = "0.0";
    private float displayValueWidth = -1f;
    private int uiBgPrimary;
    private int uiBgSecondary;
    private int uiStroke;
    private int uiIconColor;
    private int uiValueColor;
    private int uiMetaColor;

    private static void appendOneDecimal(StringBuilder sb, int tenth) {
        if (tenth < 0) {
            sb.append('-');
            tenth = -tenth;
        }
        int intPart = tenth / 10;
        int frac = tenth - intPart * 10;
        sb.append(intPart);
        sb.append('.');
        sb.append((char) ('0' + frac));
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
        this.y = screenH - 48f;
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

        float currentBps = preview ? 0f : currentBps();
        updateSamples(currentBps);

        boolean showBps = useBps.get();
        boolean useAverage = average.get();
        float baseBps = useAverage ? averageBps(currentBps) : currentBps;
        displayedSpeed = Mth.lerp(0.35f, displayedSpeed, baseBps);

        float valueSpeed = showBps ? displayedSpeed : displayedSpeed * KMH_MULT;
        String value = getSpeedNumber(valueSpeed);
        String unit = showBps ? "b/s" : "km/h";
        updatePalette();

        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        TextRenderer valueRenderer = Fonts.renderer("Onest", FontInfo.Type.Regular, fallback);
        TextRenderer metaRenderer = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, valueRenderer);

        float drawScale = HudScale.scale(screenW, screenH)
                * (hud.getFontSize() / 18f)
                * SCALE_MULT
                * this.scale.get().floatValue();

        valueRenderer.begin(drawScale, true, false);
        float valueW = (float) valueRenderer.getWidth(value, false);
        float valueH = (float) valueRenderer.getHeight(false);
        valueRenderer.end();

        metaRenderer.begin(drawScale, true, false);
        float unitW = (float) metaRenderer.getWidth(unit, false);
        float unitH = (float) metaRenderer.getHeight(false);
        metaRenderer.end();

        float boxH = BOX_HEIGHT * drawScale;
        float iconX = x + ICON_X * drawScale;
        float iconSize = ICON_SIZE * drawScale;
        float unitGap = 2f * drawScale;
        displayValueWidth = smoothWidth(displayValueWidth, valueW);
        float valueX = x + TEXT_LEFT_X * drawScale;
        float boxW = (valueX - x) + displayValueWidth + unitGap + unitW + RIGHT_PAD * drawScale;
        width = boxW;
        height = boxH;

        float baseX = x;
        float baseY = y;
        float iconY = baseY + (boxH - iconSize) * 0.5f;
        float dividerX = baseX + DIVIDER_X * drawScale;
        float dividerY = baseY + DIVIDER_Y * drawScale;
        float dividerW = Math.max(0.5f, DIVIDER_WIDTH * drawScale);
        float dividerH = Math.max(0.5f, boxH - 2f * DIVIDER_Y * drawScale);
        float textY = baseY + (boxH - Math.max(valueH, unitH)) * 0.5f;
        float unitX = valueX + displayValueWidth + unitGap;

        int iconColor = uiIconColor;
        int dividerColor = uiMetaColor;
        int valueColor = uiValueColor;
        int unitColor = uiMetaColor;
        boolean useLabelEffect = labelEffect.get() != HudTextEffects.Effect.NONE;
        float time = useLabelEffect ? (float) (Util.getMillis() / 1000.0) : 0.0f;
        if (useLabelEffect) {
            iconColor = HudTextEffects.animatedColor(iconColor, labelEffect.get(),
                    labelEffectSpeed.get(), time, 0.0f);
            dividerColor = HudTextEffects.animatedColor(dividerColor, labelEffect.get(),
                    labelEffectSpeed.get(), time, 0.15f);
        }

        float radius = BOX_RADIUS * drawScale;
        if (shadowEnabled.get()) {
            HudRenderUtil.drawHudShadow(
                    renderer, baseX, baseY, boxW, boxH, radius, drawScale,
                    HudRenderUtil.SHADOW_MODE_THEME.equals(shadowMode.get()), shadowAlpha.get(), 1.0f
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
        scriptModel.icon().texture(SPEED_ICON.toString(), iconX - baseX, iconY - baseY, iconSize, iconSize, iconColor);
        scriptModel.divider().set(dividerX - baseX, dividerY - baseY, dividerW, dividerH,
                HudRenderUtil.setAlpha(dividerColor, 0x52));
        scriptModel.value().set(value, "Onest", drawScale, valueX - baseX, textY - baseY, displayValueWidth, valueColor);
        scriptModel.unit().set(unit, "OnestMedium", drawScale, unitX - baseX, textY - baseY, unitW, unitColor);
        scriptModel.extra().hidden();
        scriptModel.animation().setLabelEffect(labelEffect.get().name(), labelEffectSpeed.get(), time)
                .setDigitAnimation(false, "", 1.0f, 0.0f);
        scriptModel.data("bps", currentBps)
                .data("displayedBps", displayedSpeed)
                .data("average", useAverage)
                .data("useBps", showBps)
                .data("valueText", value)
                .data("unitText", unit);
        scriptModel.data("shadowControlled", true);
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

    private float currentBps() {
        return PlayerSpeedUtil.getBps(mc != null ? mc.player : null);
    }

    private void updateSamples(float currentBps) {
        long now = System.currentTimeMillis();
        if (now - lastSampleMs < SAMPLE_INTERVAL_MS) return;
        lastSampleMs = now;
        samples[sampleIndex] = currentBps;
        sampleIndex = (sampleIndex + 1) % SAMPLE_COUNT;
        if (sampleCount < SAMPLE_COUNT) sampleCount++;
    }

    private float averageBps(float fallback) {
        if (sampleCount <= 0) return fallback;
        float sum = 0f;
        for (int i = 0; i < sampleCount; i++) {
            sum += samples[i];
        }
        return sum / sampleCount;
    }

    private String getSpeedNumber(float speed) {
        int tenth = Math.round(speed * 10f);
        if (tenth == lastTenth) return cachedNumber;
        lastTenth = tenth;
        valueBuilder.setLength(0);
        appendOneDecimal(valueBuilder, tenth);
        cachedNumber = valueBuilder.toString();
        return cachedNumber;
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

    private boolean isThemeMode() {
        return COLOR_THEME.equals(colorMode.get());
    }

    private boolean isAccentPanelStyle() {
        return isThemeMode() && HudRenderUtil.PANEL_STYLE_ACCENT.equals(panelStyle.get());
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


