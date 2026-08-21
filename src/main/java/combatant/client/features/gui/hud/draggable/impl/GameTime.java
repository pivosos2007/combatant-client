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
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.biome.Biome;
import combatant.client.features.gui.hud.HudElementInfo;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.HudTextEffects;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.script.CompactHudStatModel;
import combatant.client.features.gui.hud.script.ScriptableHudStatWidget;
import combatant.client.features.gui.hud.script.ScriptedCompactHudStatRenderer;
import combatant.client.features.module.modules.visuals.WorldTweaks;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;

import static combatant.client.features.theme.Theme.theme;

@HudElementInfo(
        id = "game_time",
        displayName = "Game Time",
        enabledByDefault = false,
        order = 180
)
public final class GameTime extends DraggableHudElement implements ScriptableHudStatWidget {

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
    private static final float ICON_SCALE = 0.92f;
    private static final String WIDTH_TEMPLATE = "00:00";
    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";
    private static final long SUNRISE_START = 23000L;
    private static final long SUNRISE_END = 1000L;
    private static final long SUNSET_START = 12000L;
    private static final long SUNSET_END = 13000L;
    private static final int ICON_SUNRISE = 0xEA0E;
    private static final int ICON_SUN = 0xEA04;
    private static final int ICON_SUNSET = 0xEA0F;
    private static final int ICON_RAIN_DAY = 0xEA01;
    private static final int ICON_RAIN_NIGHT = 0xEA09;
    private static final int ICON_THUNDER_DAY = 0xEA05;
    private static final int ICON_THUNDER_NIGHT = 0xEA0C;
    private static final int ICON_SNOW_DAY = 0xEA03;
    private static final int ICON_SNOW_NIGHT = 0xEA0B;
    private static final int ICON_THUNDER_SNOW_DAY = 0xEA02;
    private static final int ICON_THUNDER_SNOW_NIGHT = 0xEA0A;

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final BooleanValue showWeather = bool("game_time_weather", "weather", true);
    private final NumberValue<Double> scale = num("game_time_scale", 2.37, 0.5, 5.0);
    private final ModeValue colorMode = mode("game_time_color_mode", "color_mode", "Theme", new String[]{COLOR_THEME, COLOR_CUSTOM});
    private final ModeValue panelStyle = visibleWhen(
            mode("game_time_panel_style", HudRenderUtil.PANEL_STYLE_DEFAULT,
                    new String[]{HudRenderUtil.PANEL_STYLE_DEFAULT, HudRenderUtil.PANEL_STYLE_ACCENT}),
            this::isThemeMode
    );
    private final RGBColorValue iconColor = visibleWhen(colorNoAlpha("game_time_icon_color", "#FFFFFF"), this::isCustomMode);
    private final RGBColorValue valueColor = visibleWhen(colorNoAlpha("game_time_value_color", "#FFFFFF"), this::isCustomMode);
    private final ModeValue bgEffect = mode("game_time_bg_effect", "bg_effect", "None", new String[]{EFFECT_NONE, EFFECT_BLUR});
    private final RGBAColorValue bg = visibleWhen(color("game_time_bg", "#EB111318"), () -> isCustomMode());
    private final RGBAColorValue bg2 = visibleWhen(color("game_time_bg_secondary", "#F7161616"), () -> isCustomMode());
    private final BooleanValue strokeEnabled = bool("game_time_stroke_enabled", false);
    private final RGBColorValue stroke = visibleWhen(colorNoAlpha("game_time_stroke", "#5A5A5A"),
            () -> strokeEnabled.get() && isCustomMode());
    private final NumberValue<Integer> strokeAlpha = visibleWhen(num("game_time_stroke_alpha", 160, 0, 255), strokeEnabled::get);
    private final BooleanValue strokeGradient = visibleWhen(bool("game_time_stroke_gradient", true),
            () -> strokeEnabled.get() && isThemeMode());
    private final BooleanValue shadowEnabled = bool("game_time_shadow_enabled", true);
    private final ModeValue shadowMode = visibleWhen(
            mode("game_time_shadow_mode", HudRenderUtil.SHADOW_MODE_BLACK,
                    new String[]{HudRenderUtil.SHADOW_MODE_BLACK, HudRenderUtil.SHADOW_MODE_THEME}),
            shadowEnabled::get
    );
    private final NumberValue<Integer> shadowAlpha = visibleWhen(num("game_time_shadow_alpha", 48, 0, 255), shadowEnabled::get);
    private final NumberValue<Integer> bgAlpha = visibleWhen(num("game_time_bg_alpha", 235, 0, 255), () -> isThemeMode());
    private final NumberValue<Integer> blurAlpha = visibleWhen(num("game_time_blur_alpha", 140, 0, 255), this::hasEffect);
    private final EnumValue<HudTextEffects.Effect> iconEffect =
            enumSetting("game_time_icon_effect", HudTextEffects.Effect.MIX,
                    HudTextEffects.Effect.NONE, HudTextEffects.Effect.MIX, HudTextEffects.Effect.FLOW,
                    HudTextEffects.Effect.PULSE, HudTextEffects.Effect.STRIPE);
    private final NumberValue<Integer> iconEffectSpeed =
            visibleWhen(num("game_time_icon_effect_speed", 18, 1, 60),
                    () -> iconEffect.get() != HudTextEffects.Effect.NONE);
    private final CompactHudStatModel scriptModel = new CompactHudStatModel("game_time");

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

    private static String formatTime(long time) {
        int hours = (int) ((time / 1000 + 6) % 24);
        int minutes = (int) ((time % 1000) * 60 / 1000);
        return String.format("%02d:%02d", hours, minutes);
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
        this.y = 76f;
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
        if ((mc == null || mc.level == null) && !preview) {
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

        long worldTime = mc != null && mc.level != null ? (WorldTweaks.getServerTimeOfDay() % 24000L) : 6000L;
        String icon = iconString(resolveIcon(worldTime));
        String timeText = formatTime(worldTime);
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
        float reservedTimeW = (float) valueRenderer.getWidth(WIDTH_TEMPLATE, false);
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
        scriptModel.icon().glyph(icon, "WeatherIcons", iconX - baseX, iconY - baseY, iconW, iconH, iconScale, resolvedIconColor);
        scriptModel.divider().set(dividerX - baseX, dividerY - baseY, dividerW, dividerH,
                HudRenderUtil.setAlpha(uiValueColor, 0x52));
        scriptModel.value().set(timeText, "Onest", drawScale, textX - baseX, textY - baseY, displayTimeWidth, uiValueColor);
        scriptModel.unit().hidden();
        scriptModel.extra().hidden();
        scriptModel.animation().setLabelEffect(iconEffect.get().name(), iconEffectSpeed.get(), nowSec)
                .setDigitAnimation(false, "", 1.0f, 0.0f);
        scriptModel.data("worldTime", worldTime)
                .data("timeText", timeText)
                .data("weatherIcon", icon)
                .data("showWeather", showWeather.get());
        scriptModel.data("shadowControlled", true);
        ScriptedCompactHudStatRenderer.INSTANCE.render(scriptModel, renderer, fallback, ctx, tickDelta);
    }

    @Override
    public CompactHudStatModel compactStatModel() {
        return scriptModel;
    }

    private int resolveIcon(long time) {
        if (!showWeather.get()) return resolveClearIcon(time);
        if (mc == null || mc.level == null || mc.player == null) return resolveClearIcon(time);

        boolean thunder = WorldTweaks.isServerThundering(mc.level);
        boolean rain = WorldTweaks.isServerRaining(mc.level);
        if (!thunder && !rain) return resolveClearIcon(time);

        boolean day = time < 12000L;
        boolean snow = isSnowingAtPlayer();
        if (thunder) {
            return snow ? (day ? ICON_THUNDER_SNOW_DAY : ICON_THUNDER_SNOW_NIGHT)
                    : (day ? ICON_THUNDER_DAY : ICON_THUNDER_NIGHT);
        }
        if (snow) return day ? ICON_SNOW_DAY : ICON_SNOW_NIGHT;
        return day ? ICON_RAIN_DAY : ICON_RAIN_NIGHT;
    }

    private int resolveClearIcon(long time) {
        if (time >= SUNRISE_START || time < SUNRISE_END) return ICON_SUNRISE;
        if (time >= SUNSET_START && time < SUNSET_END) return ICON_SUNSET;
        return ICON_SUN;
    }

    private boolean isSnowingAtPlayer() {
        if (mc == null || mc.level == null || mc.player == null) return false;
        BlockPos pos = mc.player.blockPosition();
        Biome.Precipitation precipitation = mc.level.getBiome(pos).value()
                .getPrecipitationAt(pos, mc.level.getSeaLevel());
        return precipitation == Biome.Precipitation.SNOW;
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
