/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;


import combatant.client.features.theme.Theme;
import combatant.client.config.values.*;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import combatant.client.config.SettingDef;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.theme.Themes;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.AutoAnchor;
import combatant.client.features.module.modules.combat.AutoBed;
import combatant.client.features.module.modules.combat.AutoCrystal;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.profiler.RenderProfiler2D;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.util.player.PlayerHealthResolver;
import combatant.client.util.player.PlayerSkinResolver;
import combatant.client.util.player.effect.StatusEffectTracker;
import combatant.client.util.player.effect.StatusEffectView;
import combatant.client.util.pvp.opponents.TotemPopCounter;
import combatant.client.util.pvp.opponents.TotemPopSnapshot;
import combatant.client.util.target.TargetingUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 60)
public final class TargetHud extends DraggableHudElement {


    {
        defaultLayout(7.6799927f, 721.12f, "CENTER", "FREE");
    }


    private static final float BASE_WIDTH = 112.0f;
    private static final float BASE_HEIGHT = 40.0f;
    private static final float FACE_SIZE = 24.0f;
    private static final float FACE_X = 9.0f;
    private static final float FACE_Y = 8.0f;
    private static final float FACE_RADIUS = 4.0f;
    private static final float CONTENT_GAP = 6.0f;
    private static final float CONTENT_X_OFFSET = -2.0f;
    private static final float CONTENT_Y_OFFSET = -1.0f;
    private static final float PANEL_INSET = 2.0f;
    private static final float PANEL_RADIUS = 6.0f;
    private static final float NAME_Y = 13.0f;
    private static final float BAR_Y_OFFSET = 12.0f;
    private static final float BAR_WIDTH = 64.0f;
    private static final float BAR_HEIGHT = 4.0f;
    private static final float BAR_RADIUS = 2.0f;
    private static final float NAME_TEXT_SCALE = 0.34f;
    private static final float DISTANCE_TEXT_SCALE = 0.32f;
    private static final float HP_TEXT_SCALE = 0.34f;
    private static final float ARC_HP_TEXT_SCALE = 0.28f;
    private static final float DISTANCE_Y_OFFSET = 8.0f;
    private static final float ARC_CENTER_Y = 18.0f;
    private static final float ARC_RIGHT_INSET = 13.8f;
    private static final float ARC_RADIUS = 9.9f;
    private static final float ARC_THICKNESS = 3.5f;
    private static final float ARC_RESERVED_WIDTH = 34.0f;
    private static final float PLACEHOLDER_TEXT_SCALE = 0.62f;
    private static final float BASE_NAME_SCROLL_SPEED = 22.0f;
    private static final float BASE_NAME_SCROLL_GAP = 14.0f;
    private static final float BASE_NAME_FADE = 12.0f;
    private static final float NAME_SCROLL_PAUSE_SEC = 0.8f;
    private static final float EFFECTS_PANEL_GAP = 3.0f;
    private static final float EFFECTS_CHIP_HEIGHT = 10.0f;
    private static final float EFFECTS_CHIP_RADIUS = 3.0f;
    private static final float EFFECTS_CHIP_PAD_X = 3.0f;
    private static final float EFFECTS_CHIP_GAP = 2.0f;
    private static final float EFFECTS_ICON_SIZE = 8.0f;
    private static final float EFFECTS_TEXT_GAP = 2.0f;
    private static final float EFFECTS_TEXT_SCALE = 0.22f;
    private static final float EQUIPMENT_TOP_GAP = 2.0f;
    private static final float EQUIPMENT_BOTTOM_PAD = 2.0f;
    private static final float EQUIPMENT_SLOT = 11.0f;
    private static final float EQUIPMENT_GAP = 2.0f;
    private static final float EQUIPMENT_TEXT_SCALE = 0.22f;
    private static final float EQUIPMENT_ROW_Y_OFFSET = 1.15f;
    private static final long HIT_FLASH_MS = 220L;
    private static final float HEALTH_SPEED = 6.0f;
    private static final float BAR_SPEED = 4.0f;
    private static final float BAR_TAIL_FADE_LENGTH = BAR_HEIGHT;
    private static final float ARC_TAIL_FADE_DEGREES = 20.0f;
    private static final float PROGRESS_SNAP_EPSILON = 0.0005f;
    private static final float DISTANCE_SPEED = 7.0f;
    private static final float EQUIPMENT_SPEED = 7.0f;
    private static final float HEALTH_TEXT_STEP = 0.25f;
    private static final float HEALTH_TEXT_SMALL_STEP = 0.10f;
    private static final float HEALTH_ZERO_CLAMP = 0.05f;
    private static final float HEALTH_ZERO_SNAP_THRESHOLD = 0.60f;
    private static final float TOTEM_BADGE_BOTTOM_PAD = 1.45f;
    private static final float TOTEM_BADGE_ICON_SIZE = 8.0f;
    private static final float TOTEM_BADGE_TEXT_GAP = 1.25f;
    private static final float TOTEM_BADGE_TEXT_SCALE = 0.24f;
    private static final float TOTEM_BADGE_ANIM_SPEED = 10.0f;
    private static final float TOTEM_BADGE_PULSE_SPEED = 8.0f;
    private static ItemStack totemBadgeStack;

    private static ItemStack totemBadgeStack() {
        if (totemBadgeStack == null || totemBadgeStack.isEmpty()) {
            totemBadgeStack = new ItemStack(Items.TOTEM_OF_UNDYING);
        }
        return totemBadgeStack;
    }

    private static final float FACE_U0 = 8f / 64f;
    private static final float FACE_V0 = 8f / 64f;
    private static final float FACE_U1 = 16f / 64f;
    private static final float FACE_V1 = 16f / 64f;
    private static final float HAT_U0 = 40f / 64f;
    private static final float HAT_V0 = 8f / 64f;
    private static final float HAT_U1 = 48f / 64f;
    private static final float HAT_V1 = 16f / 64f;

    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";
    private static final String EFFECT_GLASS = "Glass";
    private static final String EFFECTS_OFF = "Off";
    private static final String EFFECTS_COMPACT = "Compact";
    private static final String EQUIPMENT_OFF = "Off";
    private static final String EQUIPMENT_COMPACT = "Compact";
    private static final String HP_MODE_LINE = "Line";
    private static final String HP_MODE_ARC = "Arc";

    private static final ConcurrentHashMap<Integer, Long> HIT_FLASH_MS_BY_ID = new ConcurrentHashMap<>();

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();

    private final NumberValue<Double> scale =
            new NumberValue<>("target_scale", 3.28, 0.5, 4.0);
    private final NumberValue<Integer> holdMs =
            new NumberValue<>("target_hold_ms", 1000, 0, 5000);
    private final BooleanValue totemPopCounter = TotemPopCounter.enabledValue();
    private final NumberValue<Integer> totemPopResetSeconds = TotemPopCounter.resetAfterSecondsValue();
    private final RGBColorValue totemPopTextColor =
            new RGBColorValue("target_totem_pop_text_color", "RNB#FFFFFF");
    private final BooleanValue marquee =
            new BooleanValue("target_marquee", true);
    private final BooleanValue playersOnly =
            new BooleanValue("target_players_only", false);
    private final BooleanValue crosshairTargets =
            new BooleanValue("target_crosshair_targets", true);
    private final ModeValue effectsMode =
            new ModeValue("target_effects_mode", "Off", EFFECTS_OFF, EFFECTS_COMPACT);
    private final BooleanValue effectsSelf =
            new BooleanValue("target_effects_self", true);
    private final ModeValue equipmentMode =
            new ModeValue("target_equipment_mode", "Off", EQUIPMENT_OFF, EQUIPMENT_COMPACT);
    private final ModeValue hpMode =
            new ModeValue("target_hp_mode", "Arc", HP_MODE_LINE, HP_MODE_ARC);
    private final ModeValue colorMode =
            new ModeValue("target_color_mode", "Theme", COLOR_THEME, COLOR_CUSTOM);
    private final ModeValue panelStyle =
            new ModeValue("target_panel_style", HudRenderUtil.PANEL_STYLE_DEFAULT,
                    HudRenderUtil.PANEL_STYLE_DEFAULT, HudRenderUtil.PANEL_STYLE_ACCENT);
    private final BooleanValue strokeEnabled =
            new BooleanValue("target_stroke_enabled", false);
    private final NumberValue<Integer> strokeAlpha =
            new NumberValue<>("target_stroke_alpha", 160, 0, 255);
    private final BooleanValue strokeGradient =
            new BooleanValue("target_stroke_gradient", true);
    private final BooleanValue shadowEnabled =
            new BooleanValue("target_shadow_enabled", false);
    private final ModeValue shadowMode =
            new ModeValue("target_shadow_mode", HudRenderUtil.SHADOW_MODE_BLACK,
                    HudRenderUtil.SHADOW_MODE_BLACK, HudRenderUtil.SHADOW_MODE_THEME);
    private final NumberValue<Integer> shadowAlpha =
            new NumberValue<>("target_shadow_alpha", 96, 0, 255);
    private final RGBAColorValue bg =
            new RGBAColorValue("target_bg", "#F7343434");
    private final RGBAColorValue bg2 =
            new RGBAColorValue("target_bg_secondary", "#F7161616");
    private final RGBColorValue stroke =
            new RGBColorValue("target_stroke", "#5A5A5A");
    private final NumberValue<Integer> blurAlpha =
            new NumberValue<>("target_blur_alpha", 255, 0, 255);
    private final NumberValue<Integer> bgAlpha =
            new NumberValue<>("target_bg_alpha", 162, 0, 255);
    private final RGBColorValue text =
            new RGBColorValue("target_text", "#FFFFFF");
    private final RGBColorValue textSecondary =
            new RGBColorValue("target_text_secondary", "#D7D7D7");
    private final RGBColorValue barBg =
            new RGBColorValue("target_bar_bg", "#1E1E1E");
    private final RGBColorValue healthStart =
            new RGBColorValue("target_health_start", "#F3F3F3");
    private final RGBColorValue healthEnd =
            new RGBColorValue("target_health_end", "#9E9E9E");
    private final RGBColorValue trail =
            new RGBColorValue("target_trail", "#373737");
    private final RGBColorValue absorptionStart =
            new RGBColorValue("target_absorption_start", "#FFC300");
    private final RGBColorValue absorptionEnd =
            new RGBColorValue("target_absorption_end", "#FFD96B");
    private final RGBColorValue hitOuter =
            new RGBColorValue("target_hit_outer", "#FF5A5A");
    private final RGBColorValue hitInner =
            new RGBColorValue("target_hit_inner", "#FF7373");
    private final ModeValue bgEffect =
            new ModeValue("target_bg_effect", "Glass", EFFECT_NONE, EFFECT_BLUR, EFFECT_GLASS);
    private final BooleanValue hitAnim =
            new BooleanValue("target_hit_anim", true);
    private final NumberValue<Double> hitPulse =
            new NumberValue<>("target_hit_pulse", 1.0, 0.0, 2.0);

    private LivingEntity displayTarget;
    private float visibilityAnimation;
    private float healthAnimation;
    private float trailAnimation;
    private float absorptionAnimation;
    private float effectsAnimation;
    private float equipmentAnimation;
    private float totemPopAnimation;
    private float totemPopTextPulse;
    private int totemPopLastCount;
    private UUID totemPopTargetId;
    private float displayedHealth;
    private float displayedDistance;
    private long lastSeenMs;
    private long waveStartMs = System.currentTimeMillis();

    private int uiBgPrimary;
    private int uiBgSecondary;
    private int uiStroke;
    private int uiTextPrimary;
    private int uiTextSecondary;
    private int uiBarBackground;
    private int uiHealthStart;
    private int uiHealthEnd;
    private float uiHealthGradientAngle;
    private int uiTrail;
    private int uiAbsorptionStart;
    private int uiAbsorptionEnd;
    private float uiAbsorptionGradientAngle;
    private int uiHitOuter;
    private int uiHitInner;

    public TargetHud() {
        super("target_hud", "TargetHUD", true);
    }

    public static void notifyHit(Entity target) {
        if (target == null) return;
        HIT_FLASH_MS_BY_ID.put(target.getId(), System.currentTimeMillis());
    }

    private static float snapToStep(float value, float step) {
        if (step <= 0.0f) return value;
        return Math.round(value / step) * step;
    }

    private static float formatDisplayHealth(float health) {
        float clamped = Math.max(0.0f, health);
        if (clamped <= HEALTH_ZERO_CLAMP) {
            return 0.0f;
        }
        float step = clamped < 1.0f ? HEALTH_TEXT_SMALL_STEP : HEALTH_TEXT_STEP;
        float snapped = snapToStep(clamped, step);
        return snapped <= HEALTH_ZERO_CLAMP ? 0.0f : snapped;
    }

    private static String formatHealth(float health) {
        if (health >= 100.0f) {
            return Integer.toString(Math.round(health));
        }
        if (health >= 10.0f) {
            return String.format(Locale.ROOT, "%.1f", health);
        }
        return String.format(Locale.ROOT, "%.2f", health);
    }

    private static String formatDistance(float distance) {
        if (distance >= 100.0f) {
            return Integer.toString(Math.round(distance));
        }
        return String.format(Locale.ROOT, "%.1f", distance);
    }

    private static int[] buildWaveColors(long elapsedMs,
                                         float waveSpeedMs,
                                         float alphaFactor,
                                         int startColor,
                                         int endColor) {
        float phase = (elapsedMs % (long) waveSpeedMs) / waveSpeedMs * (float) Math.PI * 2.0f;
        int[] colors = new int[4];
        for (int i = 0; i < 2; i++) {
            float wave = ((float) Math.sin(phase - i * 1.5f) + 1.0f) * 0.5f;
            int mixed = HudRenderUtil.mixColor(startColor, endColor, wave);
            colors[i * 2] = HudRenderUtil.scaleAlpha(mixed, alphaFactor);
            colors[i * 2 + 1] = colors[i * 2];
        }
        return colors;
    }

    private static void drawAnimatedArcGradient(Renderer2D renderer,
                                                float centerX,
                                                float centerY,
                                                float radius,
                                                float thickness,
                                                float startAngle,
                                                float sweep,
                                                float softness,
                                                float alphaFactor,
                                                int startColor,
                                                int endColor,
                                                float baseAngle,
                                                float speedMs,
                                                long elapsedMs,
                                                float phaseOffset) {
        float safeSpeed = Math.max(1.0f, speedMs);
        float phase = (elapsedMs / safeSpeed) * (float) Math.PI * 2.0f + phaseOffset;
        float animatedAngle = baseAngle
                + (elapsedMs / safeSpeed) * 92.0f
                + (float) Math.sin(phase * 0.63f) * 26.0f;
        float pulseA = ((float) Math.sin(phase) + 1.0f) * 0.5f;
        float pulseB = ((float) Math.sin(phase + 1.95f) + 1.0f) * 0.5f;

        int c0 = HudRenderUtil.mixColor(startColor, endColor, pulseA * 0.24f);
        int c1 = HudRenderUtil.mixColor(endColor, startColor, pulseB * 0.18f);
        int drawStart = HudRenderUtil.scaleAlpha(c0, alphaFactor);
        int drawEnd = HudRenderUtil.scaleAlpha(c1, alphaFactor);
        float offsetPx = (float) Math.sin(phase * 0.71f) * radius * 0.58f;

        renderer.arcStrokeGradient(
                centerX,
                centerY,
                radius,
                thickness,
                startAngle,
                startAngle + sweep,
                softness,
                drawStart,
                drawEnd,
                animatedAngle,
                offsetPx
        );

    }

    private static float tailFade(float visibleLength, float fadeLength) {
        if (fadeLength <= 0.0f) return visibleLength > 0.0f ? 1.0f : 0.0f;
        return AnimationUtility.smoothstep(AnimationUtility.clamp01(visibleLength / fadeLength));
    }

    private static float measureText(TextRenderer renderer, String text, float scale) {
        renderer.begin(scale, false, false);
        try {
            return (float) renderer.getWidth(text, false);
        } finally {
            renderer.end();
        }
    }

    private static String trimText(TextRenderer renderer, String text, float maxWidth, float scale) {
        if (text == null || text.isEmpty()) return "";
        renderer.begin(scale, false, false);
        try {
            if (renderer.getWidth(text, false) <= maxWidth) return text;
            String value = text;
            while (!value.isEmpty()) {
                String candidate = value + "...";
                if (renderer.getWidth(candidate, false) <= maxWidth) {
                    return candidate;
                }
                value = value.substring(0, value.length() - 1);
            }
            return "";
        } finally {
            renderer.end();
        }
    }

    private static int rgba(int red, int green, int blue, int alpha) {
        int r = Math.max(0, Math.min(255, red));
        int g = Math.max(0, Math.min(255, green));
        int b = Math.max(0, Math.min(255, blue));
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.number(scale));
        defs.add(SettingDef.number(holdMs));
        defs.add(SettingDef.bool(totemPopCounter)
                .common(CommonSettingSchemas.TOTEM_POP_COUNTER.commonI18nKey()));
        defs.add(SettingDef.number(totemPopResetSeconds)
                .common(CommonSettingSchemas.TOTEM_POP_RESET_SECONDS.commonI18nKey())
                .visibleWhen(totemPopCounter::get));
        defs.add(SettingDef.colorNoAlpha(totemPopTextColor)
                .visibleWhen(totemPopCounter::get));
        defs.add(SettingDef.bool(marquee));
        defs.add(SettingDef.bool(playersOnly));
        defs.add(SettingDef.bool(crosshairTargets));
        defs.add(SettingDef.mode(effectsMode));
        defs.add(SettingDef.bool(effectsSelf).visibleWhen(this::isEffectsEnabled));
        defs.add(SettingDef.mode(equipmentMode));
        defs.add(SettingDef.mode(hpMode));
        defs.add(SettingDef.mode(colorMode));
        defs.add(SettingDef.mode(panelStyle).visibleWhen(this::isThemeMode));
        defs.add(SettingDef.color(bg).visibleWhen(() -> isCustomMode() && !isGlassEffect()));
        defs.add(SettingDef.color(bg2).visibleWhen(() -> isCustomMode() && !isGlassEffect()));
        defs.add(SettingDef.number(bgAlpha).visibleWhen(() -> isThemeMode() && !isGlassEffect()));
        defs.add(SettingDef.bool(strokeEnabled));
        defs.add(SettingDef.colorNoAlpha(stroke).visibleWhen(() -> strokeEnabled.get() && isCustomMode()));
        defs.add(SettingDef.number(strokeAlpha).visibleWhen(strokeEnabled::get));
        defs.add(SettingDef.bool(strokeGradient).visibleWhen(() -> strokeEnabled.get() && isThemeMode()));
        defs.add(SettingDef.bool(shadowEnabled));
        defs.add(SettingDef.mode(shadowMode).visibleWhen(shadowEnabled::get));
        defs.add(SettingDef.number(shadowAlpha).visibleWhen(shadowEnabled::get));
        defs.add(SettingDef.colorNoAlpha(text).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(textSecondary).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(barBg).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(healthStart).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(healthEnd).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(trail).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(absorptionStart).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(absorptionEnd).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(hitOuter).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(hitInner).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.mode(bgEffect));
        defs.add(SettingDef.number(blurAlpha).visibleWhen(this::hasEffect));
        defs.add(SettingDef.bool(hitAnim));
        defs.add(SettingDef.number(hitPulse).visibleWhen(hitAnim::get));
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        float hudScale = HudScale.scale(screenW, screenH) * scale.get().floatValue();
        this.x = screenW / 2f - (BASE_WIDTH * hudScale) * 0.5f;
        this.y = screenH / 2f + BASE_HEIGHT * hudScale;
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
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("2d:target_hud:main");
             RenderProfiler2D.Section ignoredSection = RenderProfiler2D.section("target_hud:main")) {
            boolean preview = DraggableHudElementRegistry.isForceVisible();
            if (mc == null || (mc.player == null && !preview)) return;

            long now = System.currentTimeMillis();
            LivingEntity target = resolveTarget(preview);
            updateDisplayTarget(target, now);

            boolean shouldShow = shouldShow(target, now);
            float dt = AnimationUtility.deltaTime();
            visibilityAnimation = HudRenderUtil.animateVisibility(visibilityAnimation, shouldShow);

            if (!shouldShow && visibilityAnimation <= 0.001f) {
                displayTarget = null;
                width = 0f;
                height = 0f;
                return;
            }
            if (displayTarget == null) return;

            updatePalette();

            float hudScale = HudScale.scale(screenW, screenH) * scale.get().floatValue();
            float alphaFactor = AnimationUtility.easeOutCubic(visibilityAnimation);
            float hitPulseFactor = computeHitPulse(now);
            EffectLayout boundsEffects = buildEffectLayout(displayTarget, preview, textRenderer, hudScale);
            boolean hasEffects = !boundsEffects.chips().isEmpty();
            float effectsTarget = hasEffects ? 1.0f : 0.0f;
            effectsAnimation = AnimationUtility.approach(effectsAnimation, effectsTarget, dt, EQUIPMENT_SPEED);
            effectsAnimation = AnimationUtility.snap(effectsAnimation, effectsTarget, 0.001f);
            EquipmentLayout boundsEquipment = buildEquipmentLayout(displayTarget, preview, textRenderer, hudScale);
            boolean hasEquipment = !boundsEquipment.entries().isEmpty();
            float equipmentTarget = hasEquipment ? 1.0f : 0.0f;
            equipmentAnimation = AnimationUtility.approach(equipmentAnimation, equipmentTarget, dt, EQUIPMENT_SPEED);
            equipmentAnimation = AnimationUtility.snap(equipmentAnimation, equipmentTarget, 0.001f);
            float scalePulse = hitAnim.get()
                    ? 1.0f + hitPulseFactor * 0.018f * hitPulse.get().floatValue()
                    : 1.0f;
            float widgetScale = HudRenderUtil.visibilityScale(visibilityAnimation);

            float panelWidthLogical = BASE_WIDTH;
            float panelHeightLogical = BASE_HEIGHT;

            float targetWidth = panelWidthLogical * hudScale;
            float targetHeight = panelHeightLogical * hudScale;
            width = HudRenderUtil.animateDimension(width, targetWidth);
            height = HudRenderUtil.animateDimension(height, targetHeight);

            float widthFactor = targetWidth <= 0.0f ? 1.0f : width / targetWidth;
            float heightFactor = targetHeight <= 0.0f ? 1.0f : height / targetHeight;
            float dimensionFactor = Math.max(0.0f, Math.min(widthFactor, heightFactor));
            float scaleFactor = hudScale * dimensionFactor * widgetScale * scalePulse;

            float drawW = panelWidthLogical * scaleFactor;
            float drawH = panelHeightLogical * scaleFactor;
            float panelDrawW = panelWidthLogical * scaleFactor;
            float panelDrawH = panelHeightLogical * scaleFactor;
            float drawX = x + (width - drawW) * 0.5f;
            float drawY = y + (height - drawH) * 0.5f;

            float panelBaseX = drawX + (drawW - panelDrawW) * 0.5f;
            float panelBaseY = drawY;
            float panelX = panelBaseX + PANEL_INSET * scaleFactor;
            float panelY = panelBaseY + PANEL_INSET * scaleFactor;
            float panelW = panelDrawW - PANEL_INSET * 2f * scaleFactor;
            float panelH = panelDrawH - PANEL_INSET * 2f * scaleFactor;
            float panelRadius = PANEL_RADIUS * scaleFactor;

            boolean glassEnabled = isGlassEffect();
            boolean blurEnabled = isBlurEffect();
            TotemPopSnapshot totemPopSnapshot = resolveTotemPopSnapshot(displayTarget);
            updateTotemPopAnimation(displayTarget, totemPopSnapshot, dt);

            if (shadowEnabled.get()) {
                HudRenderUtil.drawHudShadow(
                        renderer, panelX, panelY, panelW, panelH, panelRadius, scaleFactor,
                        HudRenderUtil.SHADOW_MODE_THEME.equals(shadowMode.get()),
                        shadowAlpha.get(), alphaFactor
                );
            }

            if (glassEnabled) {
                drawGlass(panelX, panelY, panelW, panelH, panelRadius, alphaFactor);
            } else if (blurEnabled) {
                drawBlur(panelX, panelY, panelW, panelH, panelRadius, uiBgPrimary, alphaFactor);
            }

            drawBackground(renderer, panelX, panelY, panelW, panelH, panelRadius, alphaFactor, glassEnabled);
            if (strokeEnabled.get()) {
                HudRenderUtil.drawHudStroke(
                        renderer, panelX, panelY, panelW, panelH, panelRadius, 1.0f,
                        Math.max(0.5f, 0.55f * scaleFactor), uiStroke,
                        isThemeMode() && strokeGradient.get(), strokeAlpha.get(), alphaFactor
                );
            }
            drawHitPulse(renderer, panelX, panelY, panelW, panelH, panelRadius, alphaFactor, hitPulseFactor);
            drawFace(renderer, textRenderer, ctx, displayTarget, panelX, panelY, scaleFactor, alphaFactor, totemPopSnapshot);
            drawContent(renderer, textRenderer, displayTarget, panelX, panelY, panelDrawW, scaleFactor, alphaFactor, now, dt);
            if (effectsAnimation > 0.01f) {
                EffectLayout drawEffects = buildEffectLayout(displayTarget, preview, textRenderer, scaleFactor);
                float effectsY = panelY - drawEffects.height();
                drawEffects(renderer, textRenderer, drawEffects, panelX, effectsY, panelW, scaleFactor, alphaFactor * effectsAnimation, glassEnabled);
            }
            if (equipmentAnimation > 0.01f) {
                EquipmentLayout drawEquipment = buildEquipmentLayout(displayTarget, preview, textRenderer, scaleFactor);
                drawEquipment(renderer, textRenderer, ctx, drawEquipment, panelX, panelY + panelH, panelW, drawEquipment.height(), scaleFactor, alphaFactor * equipmentAnimation, glassEnabled);
            }
        }
    }

    private void updateDisplayTarget(LivingEntity target, long now) {
        if (target != null) {
            if (displayTarget != target) {
                displayTarget = target;
                PlayerHealthResolver.HealthSnapshot health = PlayerHealthResolver.resolve(target);
                float baseMaxHealth = Math.max(1.0f, target.getMaxHealth());
                float realHealth = Math.max(0.0f, health.totalHealth() - health.absorption());
                displayedHealth = health.totalHealth();
                healthAnimation = realHealth / baseMaxHealth;
                trailAnimation = healthAnimation;
                absorptionAnimation = health.absorption() / baseMaxHealth;
                displayedDistance = mc.player != null ? mc.player.distanceTo(target) : 0.0f;
                waveStartMs = now;
            }
            lastSeenMs = now;
            return;
        }

        if (displayTarget == mc.player && !(ClientScreen.current() instanceof ChatScreen)) {
            lastSeenMs = 0L;
        }
    }

    private boolean shouldShow(LivingEntity target, long now) {
        if (target != null) return true;
        return displayTarget != null && lastSeenMs > 0L && now - lastSeenMs <= Math.max(0L, holdMs.get());
    }

    private LivingEntity resolveTarget(boolean preview) {
        LivingEntity target = TargetingUtil.resolveManagedTarget(crosshairTargets.get(), playersOnly.get());
        if (target == null && (preview || ClientScreen.current() instanceof ChatScreen)) {
            target = mc.player;
        }
        return target;
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
            uiTextPrimary = theme().textPrimary();
            uiTextSecondary = theme().textMuted();
            uiBarBackground = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().surface(), 0xFF000000, 0.18f),
                    200
            );
            Themes.ThemeEntry entry = Theme.currentEntry();
            Themes.GradientSpec healthGradient = entry != null && entry.cardGradient() != null && entry.cardGradient().enabled()
                    ? entry.cardGradient()
                    : null;
            if (healthGradient != null) {
                uiHealthStart = healthGradient.start();
                uiHealthEnd = healthGradient.end();
                uiHealthGradientAngle = healthGradient.angleDeg();
            } else {
                uiHealthStart = theme().accent();
                uiHealthEnd = HudRenderUtil.mixColor(theme().accentSoft(), theme().textPrimary(), 0.18f);
                uiHealthGradientAngle = 45.0f;
            }
            uiTrail = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().surface(), theme().accentSoft(), 0.22f),
                    170
            );
            uiAbsorptionStart = 0xFFFFB400;
            uiAbsorptionEnd = 0xFFFFE17A;
            uiAbsorptionGradientAngle = uiHealthGradientAngle + 90.0f;
            uiHitOuter = HudRenderUtil.mixColor(theme().accentSoft(), 0xFFFF6767, 0.45f);
            uiHitInner = HudRenderUtil.mixColor(theme().accent(), 0xFFFFC2C2, 0.22f);
            return;
        }

        uiBgPrimary = bg.getArgb();
        uiBgSecondary = bg2.getArgb();
        uiStroke = stroke.getArgb();
        uiTextPrimary = text.getArgb();
        uiTextSecondary = textSecondary.getArgb();
        uiBarBackground = barBg.getArgb();
        uiHealthStart = healthStart.getArgb();
        uiHealthEnd = healthEnd.getArgb();
        uiHealthGradientAngle = 45.0f;
        uiTrail = trail.getArgb();
        uiAbsorptionStart = absorptionStart.getArgb();
        uiAbsorptionEnd = absorptionEnd.getArgb();
        uiAbsorptionGradientAngle = 135.0f;
        uiHitOuter = hitOuter.getArgb();
        uiHitInner = hitInner.getArgb();
    }

    private void drawBackground(Renderer2D renderer,
                                float x,
                                float y,
                                float width,
                                float height,
                                float radius,
                                float alphaFactor,
                                boolean glassEnabled) {
        if (glassEnabled) {
            renderer.roundedRect(x, y, width, height, radius, 1.0f, HudRenderUtil.glassPanelBackground(alphaFactor));
            return;
        }

        int primary = HudRenderUtil.scaleAlpha(uiBgPrimary, alphaFactor);
        int secondary = HudRenderUtil.scaleAlpha(uiBgSecondary, alphaFactor);

        if (isThemeMode()) {
            if (isAccentPanelStyle()) {
                renderer.roundedRectGradient(x, y, width, height, radius, 1.0f, primary, secondary, 90.0f);
            } else {
                HudRenderUtil.drawHudBackground(renderer, x, y, width, height, radius, 1.0f, primary, true);
            }
        } else {
            renderer.roundedRectGradientQuad(x, y, width, height, radius, 1.0f, primary, secondary, primary, secondary);
        }
    }

    private void drawFace(Renderer2D renderer,
                          TextRenderer textRenderer,
                          GuiGraphicsExtractor ctx,
                          LivingEntity target,
                          float x,
                          float y,
                          float scaleFactor,
                          float alphaFactor,
                          TotemPopSnapshot totemPopSnapshot) {
        float faceSize = FACE_SIZE * scaleFactor;
        float faceX = x + (FACE_X + CONTENT_X_OFFSET) * scaleFactor;
        float faceY = y + (FACE_Y + CONTENT_Y_OFFSET) * scaleFactor;
        float faceRadius = FACE_RADIUS * scaleFactor;

        float hurtPercent = target.hurtTime > 0 ? Math.min(1.0f, target.hurtTime / 10.0f) : 0.0f;
        int red = 255;
        int green = Math.round(255.0f * (1.0f - hurtPercent));
        int blue = Math.round(255.0f * (1.0f - hurtPercent));
        int tint = rgba(red, green, blue, Math.round(255.0f * alphaFactor));

        if (target instanceof Player player) {
            drawPlayerFace(player, faceX, faceY, faceSize, faceRadius, tint);
        } else {
            drawPlaceholderFace(renderer, textRenderer, target, faceX, faceY, faceSize, faceRadius, alphaFactor, hurtPercent);
        }

        drawTotemPopBadge(renderer, textRenderer, totemPopSnapshot, faceX, faceY, faceSize, faceRadius, scaleFactor, alphaFactor);
    }

    private TotemPopSnapshot resolveTotemPopSnapshot(LivingEntity target) {
        if (!(target instanceof Player player)) {
            return TotemPopSnapshot.empty(null);
        }
        return TotemPopCounter.snapshot(player.getUUID());
    }

    private void updateTotemPopAnimation(LivingEntity target,
                                         TotemPopSnapshot snapshot,
                                         float dt) {
        UUID id = target instanceof Player player ? player.getUUID() : null;
        if (id == null || !id.equals(totemPopTargetId)) {
            totemPopTargetId = id;
            totemPopAnimation = 0.0f;
            totemPopTextPulse = 0.0f;
            totemPopLastCount = 0;
        }

        int count = snapshot != null ? Math.max(0, snapshot.count()) : 0;
        if (count > 0 && count != totemPopLastCount) {
            totemPopLastCount = count;
            totemPopTextPulse = 1.0f;
        } else if (count <= 0) {
            totemPopLastCount = 0;
        }

        float targetAnimation = snapshot != null && snapshot.visible() ? 1.0f : 0.0f;
        totemPopAnimation = AnimationUtility.approach(totemPopAnimation, targetAnimation, dt, TOTEM_BADGE_ANIM_SPEED);
        totemPopAnimation = AnimationUtility.snap(totemPopAnimation, targetAnimation, 0.001f);
        totemPopTextPulse = AnimationUtility.approach(totemPopTextPulse, 0.0f, dt, TOTEM_BADGE_PULSE_SPEED);
        totemPopTextPulse = AnimationUtility.snap(totemPopTextPulse, 0.0f, 0.001f);
    }

    private void drawTotemPopBadge(Renderer2D renderer,
                                   TextRenderer textRenderer,
                                   TotemPopSnapshot snapshot,
                                   float faceX,
                                   float faceY,
                                   float faceSize,
                                   float faceRadius,
                                   float scaleFactor,
                                   float alphaFactor) {
        if (snapshot == null || snapshot.count() <= 0 || totemPopAnimation <= 0.001f) {
            return;
        }

        float anim = AnimationUtility.easeInOutCubic(totemPopAnimation);
        float visualAlpha = alphaFactor * anim;
        if (visualAlpha <= 0.001f) {
            return;
        }

        String text = "x" + snapshot.count();
        TextRenderer badgeRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, textRenderer);
        float pulse = AnimationUtility.easeOutBack(totemPopTextPulse, 1.12f);
        float groupScale = (0.86f + 0.14f * anim) * (1.0f + 0.13f * pulse);
        float textScale = TOTEM_BADGE_TEXT_SCALE * scaleFactor * groupScale;
        float iconSize = Math.min(faceSize * 0.38f, TOTEM_BADGE_ICON_SIZE * scaleFactor * groupScale);
        float gap = TOTEM_BADGE_TEXT_GAP * scaleFactor;
        float textWidth = measureText(badgeRenderer, text, textScale);
        float textHeight = getTextHeight(badgeRenderer, textScale);
        float groupWidth = iconSize + gap + textWidth;
        float groupHeight = Math.max(iconSize, textHeight);

        float groupX = faceX + (faceSize - groupWidth) * 0.5f;
        float groupY = faceY + faceSize - groupHeight - TOTEM_BADGE_BOTTOM_PAD * scaleFactor
                - (1.0f - anim) * 1.4f * scaleFactor;
        float iconX = groupX;
        float iconY = groupY + (groupHeight - iconSize) * 0.5f;
        drawItem(renderer, totemBadgeStack(), iconX, iconY, Math.max(0.1f, iconSize / 16.0f), 713, visualAlpha);

        float textX = iconX + iconSize + gap;
        float textY = groupY + (groupHeight - textHeight) * 0.5f - 0.12f * scaleFactor;
        int textColor = HudRenderUtil.setAlpha(totemPopTextColor.getArgb(), Math.round(255.0f * visualAlpha));
        badgeRenderer.begin(textScale, false, false);
        try {
            badgeRenderer.render(text, textX, textY, new RenderColor(textColor), true);
        } finally {
            badgeRenderer.end();
        }
    }

    private void drawPlayerFace(Player player,
                                float x,
                                float y,
                                float size,
                                float radius,
                                int tint) {
        Identifier skin = PlayerSkinResolver.resolveProfileSkin(player.getGameProfile());
        if (skin == null) return;

        Renderer2D.TEXTURE.roundedTexRect(
                x, y, size, size,
                radius, 1.0f,
                FACE_U0, FACE_V0, FACE_U1, FACE_V1,
                tint,
                skin
        );

        float hatSize = size * 1.1f;
        float hatOffset = (hatSize - size) * 0.5f;
        Renderer2D.TEXTURE.roundedTexRect(
                x - hatOffset, y - hatOffset, hatSize, hatSize,
                radius * 1.05f, 1.0f,
                HAT_U0, HAT_V0, HAT_U1, HAT_V1,
                tint,
                skin
        );
    }

    private void drawPlaceholderFace(Renderer2D renderer,
                                     TextRenderer textRenderer,
                                     LivingEntity target,
                                     float x,
                                     float y,
                                     float size,
                                     float radius,
                                     float alphaFactor,
                                     float hurtPercent) {
        int light = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(uiBgSecondary, uiHealthEnd, 0.28f), alphaFactor);
        int dark = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(uiBgPrimary, 0xFF000000, 0.22f), alphaFactor);
        renderer.roundedRectGradient(x, y, size, size, radius, 1.0f, light, dark, 90.0f);
        renderer.roundedRectStroke(
                x, y, size, size, radius, 1.0f,
                0.65f * size / FACE_SIZE,
                HudRenderUtil.scaleAlpha(uiStroke, alphaFactor)
        );

        String name = target.getName().getString();
        String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        TextRenderer faceRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, textRenderer);
        float textScale = PLACEHOLDER_TEXT_SCALE * scale.get().floatValue() * HudScale.scale(
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight()
        );
        faceRenderer.begin(textScale, false, false);
        try {
            float textWidth = (float) faceRenderer.getWidth(letter, false);
            float textHeight = (float) faceRenderer.getHeight(false);
            float tx = x + (size - textWidth) * 0.5f;
            float ty = y + (size - textHeight) * 0.5f - scale.get().floatValue();
            int letterColor = HudRenderUtil.scaleAlpha(
                    HudRenderUtil.mixColor(uiTextPrimary, uiTextSecondary, hurtPercent * 0.35f),
                    alphaFactor
            );
            faceRenderer.render(letter, tx, ty, new RenderColor(letterColor), false);
        } finally {
            faceRenderer.end();
        }
    }

    private void drawContent(Renderer2D renderer,
                             TextRenderer textRenderer,
                             LivingEntity target,
                             float x,
                             float y,
                             float drawWidth,
                             float scaleFactor,
                             float alphaFactor,
                             long now,
                             float dt) {
        float contentX = x + (FACE_X + FACE_SIZE + CONTENT_GAP + CONTENT_X_OFFSET) * scaleFactor;
        float nameY = y + (NAME_Y + CONTENT_Y_OFFSET) * scaleFactor;

        PlayerHealthResolver.HealthSnapshot resolved = PlayerHealthResolver.resolve(target);
        float health = Math.max(0.0f, resolved.totalHealth() - resolved.absorption());
        float maxHealth = Math.max(1.0f, target.getMaxHealth());
        float absorption = Math.max(0.0f, resolved.absorption());

        float targetDisplayHealth = health + absorption;
        displayedHealth = AnimationUtility.approach(displayedHealth, targetDisplayHealth, dt, HEALTH_SPEED);
        displayedHealth = AnimationUtility.snap(displayedHealth, targetDisplayHealth, 0.02f);
        if (targetDisplayHealth <= HEALTH_ZERO_CLAMP && displayedHealth <= HEALTH_ZERO_SNAP_THRESHOLD) {
            displayedHealth = 0.0f;
        }

        float targetHealthFraction = AnimationUtility.clamp01(health / maxHealth);
        float targetAbsorptionFraction = AnimationUtility.clamp01(absorption / maxHealth);
        healthAnimation = AnimationUtility.approach(healthAnimation, targetHealthFraction, dt, BAR_SPEED);
        healthAnimation = AnimationUtility.snap(healthAnimation, targetHealthFraction, PROGRESS_SNAP_EPSILON);
        if (targetHealthFraction > trailAnimation) {
            trailAnimation = targetHealthFraction;
        }
        trailAnimation = AnimationUtility.approach(trailAnimation, targetHealthFraction, dt, BAR_SPEED - 0.5f);
        trailAnimation = AnimationUtility.snap(trailAnimation, targetHealthFraction, PROGRESS_SNAP_EPSILON);
        absorptionAnimation = AnimationUtility.approach(absorptionAnimation, targetAbsorptionFraction, dt, BAR_SPEED);
        absorptionAnimation = AnimationUtility.snap(absorptionAnimation, targetAbsorptionFraction, PROGRESS_SNAP_EPSILON);

        boolean selfTarget = target == mc.player;
        if (!selfTarget) {
            float targetDistance = mc.player != null ? mc.player.distanceTo(target) : 0.0f;
            displayedDistance = AnimationUtility.approach(displayedDistance, targetDistance, dt, DISTANCE_SPEED);
            displayedDistance = AnimationUtility.snap(displayedDistance, targetDistance, 0.01f);
        }

        String hpText = formatHealth(formatDisplayHealth(displayedHealth));
        String distanceText = selfTarget ? "" : formatDistance(displayedDistance) + "m";
        String moduleStatus = resolveModuleStatus(target);
        String metaText = moduleStatus != null ? moduleStatus : distanceText;
        float panelWidth = drawWidth - PANEL_INSET * 4f * scaleFactor;
        TextRenderer labelRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, textRenderer);
        TextRenderer metaRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, textRenderer);
        boolean arcMode = isArcHealthMode();
        float hpTextScale = (arcMode ? ARC_HP_TEXT_SCALE : HP_TEXT_SCALE) * scaleFactor;
        float hpWidth = measureText(labelRenderer, hpText, hpTextScale);
        float contentReserve = arcMode
                ? ARC_RESERVED_WIDTH * scaleFactor
                : hpWidth + 12.0f * scaleFactor;
        float maxNameWidth = Math.max(12.0f * scaleFactor, panelWidth - FACE_SIZE * scaleFactor - CONTENT_GAP * scaleFactor - contentReserve);
        String rawName = target.getName().getString();
        String name = trimText(labelRenderer, rawName, maxNameWidth, NAME_TEXT_SCALE * scaleFactor);
        String meta = trimText(metaRenderer, metaText, maxNameWidth, DISTANCE_TEXT_SCALE * scaleFactor);
        float nameScale = NAME_TEXT_SCALE * scaleFactor;
        float rawNameWidth = measureText(labelRenderer, rawName, nameScale);
        boolean useMarquee = marquee.get() && rawNameWidth > maxNameWidth * 1.01f;

        int nameColor = HudRenderUtil.scaleAlpha(uiTextPrimary, alphaFactor);
        int hpColor = HudRenderUtil.scaleAlpha(uiTextSecondary, alphaFactor);
        int distanceColor = HudRenderUtil.scaleAlpha(uiTextSecondary, alphaFactor);

        if (useMarquee) {
            drawScrollingText(
                    labelRenderer,
                    rawName,
                    contentX,
                    nameY,
                    rawNameWidth,
                    maxNameWidth,
                    nameScale,
                    scaleFactor,
                    alphaFactor,
                    nameColor
            );
        } else {
            labelRenderer.begin(nameScale, false, false);
            try {
                labelRenderer.render(name, contentX, nameY, new RenderColor(nameColor), false);
            } finally {
                labelRenderer.end();
            }
        }

        if (arcMode) {
            metaRenderer.begin(DISTANCE_TEXT_SCALE * scaleFactor, false, false);
            try {
                metaRenderer.render(
                        meta,
                        contentX,
                        nameY + DISTANCE_Y_OFFSET * scaleFactor,
                        new RenderColor(distanceColor),
                        false
                );
            } finally {
                metaRenderer.end();
            }

            drawHealthArc(renderer, textRenderer, x, y, panelWidth, scaleFactor, alphaFactor, hpText, hpColor, now);
            return;
        }

        labelRenderer.begin(HP_TEXT_SCALE * scaleFactor, false, false);
        try {
            float hpX = x + panelWidth - hpWidth + CONTENT_X_OFFSET * scaleFactor;
            labelRenderer.render(hpText, hpX, nameY, new RenderColor(hpColor), false);
        } finally {
            labelRenderer.end();
        }

        float barX = contentX;
        float barY = nameY + BAR_Y_OFFSET * scaleFactor;
        float barWidth = BAR_WIDTH * scaleFactor;
        float barHeight = BAR_HEIGHT * scaleFactor;
        float barRadius = BAR_RADIUS * scaleFactor;

        renderer.roundedRect(
                barX, barY, barWidth, barHeight, barRadius, 1.0f,
                HudRenderUtil.scaleAlpha(uiBarBackground, alphaFactor)
        );

        float trailPercent = AnimationUtility.clamp01(trailAnimation);
        float healthPercent = AnimationUtility.clamp01(healthAnimation);
        if (trailPercent > healthPercent + 0.001f) {
            float trailFade = tailFade(BAR_WIDTH * trailPercent, BAR_TAIL_FADE_LENGTH);
            int trailColor = HudRenderUtil.scaleAlpha(uiTrail, alphaFactor * trailFade);
            drawScissoredHealthBar(
                    renderer, barX, barY, barWidth, barHeight, barRadius,
                    trailPercent, trailColor, trailColor, 0.0f
            );
        }

        long elapsedMs = Math.max(0L, now - waveStartMs);

        if (healthPercent > 0.0001f) {
            float healthFade = tailFade(BAR_WIDTH * healthPercent, BAR_TAIL_FADE_LENGTH);
            int[] healthWave = buildWaveColors(elapsedMs, 1500f, alphaFactor * healthFade, uiHealthStart, uiHealthEnd);
            drawScissoredHealthBar(
                    renderer, barX, barY, barWidth, barHeight, barRadius,
                    healthPercent, healthWave[0], healthWave[2], 90.0f
            );
        }

        float absorptionPercent = AnimationUtility.clamp01(absorptionAnimation);
        if (absorptionPercent > 0.0001f) {
            float absorptionFade = tailFade(BAR_WIDTH * absorptionPercent, BAR_TAIL_FADE_LENGTH);
            int[] absorptionWave = buildWaveColors(elapsedMs, 1200f, alphaFactor * absorptionFade, uiAbsorptionStart, uiAbsorptionEnd);
            drawScissoredHealthBar(
                    renderer, barX, barY, barWidth, barHeight, barRadius,
                    absorptionPercent, absorptionWave[0], absorptionWave[2], 90.0f
            );
        }
    }

    private static void drawScissoredHealthBar(
            Renderer2D renderer,
            float x,
            float y,
            float width,
            float height,
            float radius,
            float progress,
            int startColor,
            int endColor,
            float angle
    ) {
        float clampedProgress = AnimationUtility.clamp01(progress);
        if (clampedProgress <= 0.0001f) {
            return;
        }

        if (clampedProgress >= 0.9999f) {
            renderer.roundedRectGradient(x, y, width, height, radius, 1.0f, startColor, endColor, angle, 0.0f);
            return;
        }

        boolean clipped = ScissorFunction.pushRaw(x, y, width * clampedProgress, height);
        if (!clipped) {
            return;
        }
        try {
            renderer.roundedRectGradient(x, y, width, height, radius, 1.0f, startColor, endColor, angle, 0.0f);
        } finally {
            ScissorFunction.pop();
        }
    }

    private String resolveModuleStatus(LivingEntity target) {
        if (target == null) {
            return null;
        }

        AutoCrystal autoCrystal = Modules.get(AutoCrystal.class);
        if (autoCrystal != null) {
            String status = autoCrystal.getTargetHudStatus(target);
            if (status != null && !status.isBlank()) {
                return status;
            }
        }

        AutoAnchor autoAnchor = Modules.get(AutoAnchor.class);
        if (autoAnchor != null) {
            String status = autoAnchor.getTargetHudStatus(target);
            if (status != null && !status.isBlank()) {
                return status;
            }
        }

        AutoBed autoBed = Modules.get(AutoBed.class);
        if (autoBed != null) {
            String status = autoBed.getTargetHudStatus(target);
            if (status != null && !status.isBlank()) {
                return status;
            }
        }

        return null;
    }

    private void drawScrollingText(TextRenderer renderer,
                                   String text,
                                   float x,
                                   float y,
                                   float fullWidth,
                                   float viewWidth,
                                   float scale,
                                   float baseScale,
                                   float alpha,
                                   int color) {
        float speed = BASE_NAME_SCROLL_SPEED * baseScale;
        float gap = BASE_NAME_SCROLL_GAP * baseScale;
        if (speed <= 0.0f) {
            return;
        }

        float cycleDistance = fullWidth + gap;
        float cycleTime = NAME_SCROLL_PAUSE_SEC + (cycleDistance / speed);
        if (cycleTime <= 0.0f) {
            return;
        }

        float now = Util.getMillis() / 1000.0f;
        float t = now % cycleTime;
        float offset = 0.0f;
        if (t > NAME_SCROLL_PAUSE_SEC) {
            offset = -(t - NAME_SCROLL_PAUSE_SEC) * speed;
        }

        RenderColor renderColor = new RenderColor(HudRenderUtil.scaleAlpha(color, alpha));
        float fade = Math.min(viewWidth * 0.25f, BASE_NAME_FADE * baseScale);
        renderer.begin(scale, false, false);
        try {
            renderer.renderHorizontalFadeClipped(text, x + offset, y, renderColor, x, x + viewWidth, fade, fade, false);
            if (cycleDistance > viewWidth * 0.5f) {
                renderer.renderHorizontalFadeClipped(text, x + offset + cycleDistance, y, renderColor,
                        x, x + viewWidth, fade, fade, false);
            }
        } finally {
            renderer.end();
        }
    }

    private float getTextHeight(TextRenderer renderer, float scale) {
        renderer.begin(scale, false, false);
        try {
            return (float) renderer.getHeight(false);
        } finally {
            renderer.end();
        }
    }

    private void drawHealthArc(Renderer2D renderer,
                               TextRenderer textRenderer,
                               float x,
                               float y,
                               float panelWidth,
                               float scaleFactor,
                               float alphaFactor,
                               String hpText,
                               int hpTextColor,
                               long now) {
        float centerX = x + panelWidth - ARC_RIGHT_INSET * scaleFactor + CONTENT_X_OFFSET * scaleFactor;
        float centerY = y + ARC_CENTER_Y * scaleFactor + CONTENT_Y_OFFSET * scaleFactor;
        float radius = ARC_RADIUS * scaleFactor;
        float thickness = ARC_THICKNESS * scaleFactor;
        float softness = Math.max(0.35f, 0.45f * scaleFactor);

        int ringBackground = HudRenderUtil.scaleAlpha(
                HudRenderUtil.mixColor(uiBarBackground, 0xFF000000, 0.34f),
                alphaFactor * 0.55f
        );
        renderer.arcStroke(centerX, centerY, radius, thickness, 0.0f, 360.0f, softness, ringBackground);

        float healthPercent = AnimationUtility.clamp01(healthAnimation);

        float healthSweep = 360.0f * healthPercent;
        if (healthSweep > 0.001f) {
            float healthFade = tailFade(healthSweep, ARC_TAIL_FADE_DEGREES);
            drawAnimatedArcGradient(
                    renderer,
                    centerX,
                    centerY,
                    radius,
                    thickness,
                    0.0f,
                    healthSweep,
                    softness,
                    alphaFactor * healthFade,
                    uiHealthStart,
                    uiHealthEnd,
                    uiHealthGradientAngle,
                    1700f,
                    now - waveStartMs,
                    0.0f
            );
        }

        float absorptionPercent = AnimationUtility.clamp01(absorptionAnimation);
        float absorptionSweep = 360.0f * absorptionPercent;
        if (absorptionSweep > 0.001f) {
            float absorptionThickness = Math.max(1.6f * scaleFactor, thickness * 0.9f);
            float absorptionFade = tailFade(absorptionSweep, ARC_TAIL_FADE_DEGREES);
            drawAnimatedArcGradient(
                    renderer,
                    centerX,
                    centerY,
                    radius,
                    absorptionThickness,
                    0.0f,
                    absorptionSweep,
                    softness,
                    alphaFactor * absorptionFade,
                    uiAbsorptionStart,
                    uiAbsorptionEnd,
                    uiAbsorptionGradientAngle,
                    1300f,
                    now - waveStartMs,
                    1.35f
            );
        }

        TextRenderer hpRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, textRenderer);
        float hpScale = ARC_HP_TEXT_SCALE * scaleFactor;
        hpRenderer.begin(hpScale, false, false);
        try {
            float textWidth = (float) hpRenderer.getWidth(hpText, false);
            float textHeight = (float) hpRenderer.getHeight(false);
            hpRenderer.render(
                    hpText,
                    centerX - textWidth * 0.5f,
                    centerY - textHeight * 0.5f,
                    new RenderColor(hpTextColor),
                    false
            );
        } finally {
            hpRenderer.end();
        }
    }

    private void drawEquipment(Renderer2D renderer,
                               TextRenderer textRenderer,
                               GuiGraphicsExtractor ctx,
                               EquipmentLayout layout,
                               float x,
                               float y,
                               float panelWidth,
                               float equipmentHeight,
                               float scaleFactor,
                               float alphaFactor,
                               boolean glassEnabled) {
        if (ctx == null || layout.entries().isEmpty() || alphaFactor <= 0.01f) {
            return;
        }

        float rowX = x + Math.max(0.0f, (panelWidth - layout.width()) * 0.5f);
        float rowY = y + Math.max(0.0f, equipmentHeight - layout.height()) + (EQUIPMENT_ROW_Y_OFFSET * scaleFactor);

        float slotHeight = EQUIPMENT_SLOT * scaleFactor;
        float radius = 2.2f * scaleFactor;
        List<EquipmentRenderTask> itemTasks = new ArrayList<>(layout.entries().size());
        int slotFillLeft = HudRenderUtil.scaleAlpha(
                HudRenderUtil.mixColor(uiBarBackground, uiBgPrimary, 0.34f),
                alphaFactor
        );
        int slotFillRight = HudRenderUtil.scaleAlpha(
                HudRenderUtil.mixColor(uiBgSecondary, uiBarBackground, 0.24f),
                alphaFactor * 0.96f
        );
        int slotStroke = HudRenderUtil.scaleAlpha(
                HudRenderUtil.mixColor(uiStroke, uiBgSecondary, 0.10f),
                alphaFactor * 0.46f
        );
        int slotHighlight = HudRenderUtil.scaleAlpha(
                HudRenderUtil.mixColor(uiTextPrimary, uiBgPrimary, 0.88f),
                alphaFactor * 0.08f
        );
        float itemScale = Math.max(0.35f, (slotHeight - 3.0f * scaleFactor) / 16.0f);
        float cursorX = rowX;
        for (EquipmentEntry entry : layout.entries()) {
            if (glassEnabled) {
                drawGlassCard(cursorX, rowY, entry.width(), slotHeight, radius, scaleFactor, alphaFactor);
                renderer.roundedRect(cursorX, rowY, entry.width(), slotHeight, radius, 1.0f,
                        HudRenderUtil.glassSmallBackground(alphaFactor));
            } else {
                renderer.roundedRectGradientQuad(
                        cursorX, rowY, entry.width(), slotHeight, radius, 1.0f,
                        slotFillLeft, slotFillRight, slotFillRight, slotFillLeft
                );
                renderer.roundedRectStroke(cursorX, rowY, entry.width(), slotHeight, radius, 1.0f, 0.5f * scaleFactor, slotStroke);
                renderer.roundedRect(
                        cursorX + 0.75f * scaleFactor,
                        rowY + 0.75f * scaleFactor,
                        Math.max(0.0f, entry.width() - 1.5f * scaleFactor),
                        Math.max(0.6f, 0.75f * scaleFactor),
                        Math.max(0.5f, radius * 0.45f),
                        1.0f,
                        slotHighlight
                );
            }

            if (entry.stack() != null && !entry.stack().isEmpty()) {
                float itemX = cursorX + 1.5f * scaleFactor;
                float itemY = rowY + (slotHeight - 16.0f * itemScale) * 0.5f;
                itemTasks.add(new EquipmentRenderTask(entry.stack(), itemX, itemY, itemScale));
            } else {
                drawEmptyEquipmentCross(renderer, cursorX, rowY, entry.width(), slotHeight, scaleFactor, alphaFactor);
            }

            cursorX += entry.width() + EQUIPMENT_GAP * scaleFactor;
        }
        if (!itemTasks.isEmpty()) {
            int seed = 0;
            for (EquipmentRenderTask task : itemTasks) {
                drawItem(renderer, task.stack(), task.x(), task.y(), task.scale(), seed++, alphaFactor);
            }
        }
    }

    private void drawEffects(Renderer2D renderer,
                             TextRenderer textRenderer,
                             EffectLayout layout,
                             float x,
                             float y,
                             float panelWidth,
                             float scaleFactor,
                             float alphaFactor,
                             boolean glassEnabled) {
        if (layout.chips().isEmpty() || alphaFactor <= 0.01f) {
            return;
        }

        float chipHeight = EFFECTS_CHIP_HEIGHT * scaleFactor;
        float rowX = x + (panelWidth - layout.width()) * 0.5f;
        float rowY = y + (layout.height() - chipHeight) * 0.5f;
        float radius = EFFECTS_CHIP_RADIUS * scaleFactor;
        int fill = glassEnabled
                ? HudRenderUtil.glassSmallBackground(alphaFactor)
                : HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(uiBgPrimary, uiBgSecondary, 0.4f), alphaFactor);
        int stroke = glassEnabled
                ? 0
                : HudRenderUtil.scaleAlpha(uiStroke, alphaFactor * 0.5f);
        int iconColor = HudRenderUtil.scaleAlpha(uiTextPrimary, alphaFactor);
        int textColor = HudRenderUtil.scaleAlpha(uiTextSecondary, alphaFactor);

        TextRenderer chipRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, textRenderer);
        float textScale = EFFECTS_TEXT_SCALE * scaleFactor;
        float textHeight = 0.0f;
        chipRenderer.begin(textScale, false, false);
        try {
            textHeight = (float) chipRenderer.getHeight(false);
            float cursorX = rowX;
            for (EffectChip chip : layout.chips()) {
                if (glassEnabled) {
                    drawGlassCard(cursorX, rowY, chip.width(), chipHeight, radius, scaleFactor, alphaFactor);
                }
                renderer.roundedRect(cursorX, rowY, chip.width(), chipHeight, radius, 1.0f, fill);
                if (!glassEnabled) {
                    renderer.roundedRectStroke(cursorX, rowY, chip.width(), chipHeight, radius, 1.0f, 0.45f * scaleFactor, stroke);
                }

                float iconX = cursorX + EFFECTS_CHIP_PAD_X * scaleFactor;
                float iconY = rowY + (chipHeight - EFFECTS_ICON_SIZE * scaleFactor) * 0.5f;
                if (chip.iconId() != null) {
                    Renderer2D.TEXTURE.roundedTexRect(
                            iconX,
                            iconY,
                            EFFECTS_ICON_SIZE * scaleFactor,
                            EFFECTS_ICON_SIZE * scaleFactor,
                            2.0f * scaleFactor,
                            1.0f,
                            iconColor,
                            chip.iconId()
                    );
                }

                if (!chip.text().isEmpty()) {
                    float textX = iconX + EFFECTS_ICON_SIZE * scaleFactor + EFFECTS_TEXT_GAP * scaleFactor;
                    float textY = rowY + (chipHeight - textHeight) * 0.5f;
                    chipRenderer.render(chip.text(), textX, textY, new RenderColor(textColor), false);
                }

                cursorX += chip.width() + EFFECTS_CHIP_GAP * scaleFactor;
            }
        } finally {
            chipRenderer.end();
        }
    }

    private void drawHitPulse(Renderer2D renderer,
                              float x,
                              float y,
                              float width,
                              float height,
                              float radius,
                              float alphaFactor,
                              float pulse) {
        if (!hitAnim.get() || pulse <= 0.001f) return;

        float scaledPulse = pulse * hitPulse.get().floatValue();
        float spread = 6.0f * (0.65f + scaledPulse);
        int outerColor = HudRenderUtil.scaleAlpha(uiHitOuter, 0.58f * scaledPulse * alphaFactor);
        int innerColor = HudRenderUtil.scaleAlpha(uiHitInner, 0.92f * scaledPulse * alphaFactor);

        renderer.roundedRectStroke(
                x - spread * 0.4f,
                y - spread * 0.4f,
                width + spread * 0.8f,
                height + spread * 0.8f,
                radius + spread * 0.15f,
                1.0f,
                0.85f + scaledPulse,
                outerColor
        );
        renderer.roundedRectStroke(x, y, width, height, radius, 1.0f, 0.9f + scaledPulse, innerColor);
    }

    private void drawBlur(float x, float y, float width, float height, float radius, int tintRgb, float alphaFactor) {
        if (!hasEffect() || width <= 0.0f || height <= 0.0f) return;
        float quality = hud.getBlurRadius();
        float brightness = 1.0f;
        float alpha = (blurAlpha.get() / 255.0f) * alphaFactor;
        Renderer2D.COLOR.blurRect(x, y, width, height, radius, quality, brightness, alpha, 0xFFFFFF);
    }

    private void drawGlass(float x, float y, float width, float height, float radius, float alphaFactor) {
        float blurStrength = (blurAlpha.get() / 255.0f) * alphaFactor;
        float glassAlpha = alphaFactor;
        float glassScale = PANEL_RADIUS <= 0.0f ? 1.0f : radius / PANEL_RADIUS;
        HudRenderUtil.drawLiquidGlass(x, y, width, height, radius, glassScale, true, blurStrength, glassAlpha);
        if (isAccentPanelStyle()) {
            HudRenderUtil.ThemeGradient accentGradient = HudRenderUtil.themeAccentGradient(
                    Math.round(42.0f * AnimationUtility.clamp01(alphaFactor))
            );
            Renderer2D.COLOR.roundedRectGradient(
                    x, y, width, height, radius, 1.0f,
                    accentGradient.start(), accentGradient.end(), accentGradient.angleDeg()
            );
        }
    }

    private void drawGlassCard(float x,
                               float y,
                               float width,
                               float height,
                               float radius,
                               float scaleFactor,
                               float alphaFactor) {
        float blurStrength = (blurAlpha.get() / 255.0f) * alphaFactor;
        HudRenderUtil.drawLiquidGlass(x, y, width, height, radius, scaleFactor, true, blurStrength, alphaFactor);
    }

    private void drawItem(Renderer2D renderer,
                          ItemStack stack,
                          float x,
                          float y,
                          float itemScale,
                          int seed,
                          float alphaFactor) {
        if (renderer == null || stack == null || stack.isEmpty() || alphaFactor <= 0.001f) {
            return;
        }

        double previousAlpha = renderer.getAlpha();
        renderer.setAlpha(previousAlpha * AnimationUtility.clamp01(alphaFactor));
        try {
            renderer.item(stack, x, y, itemScale, seed, Renderer2D.ITEM_OVERLAY_NONE, null);
        } finally {
            renderer.setAlpha(previousAlpha);
        }
    }

    private float computeHitPulse(long now) {
        if (displayTarget == null) return 0.0f;
        Long hitAt = HIT_FLASH_MS_BY_ID.get(displayTarget.getId());
        if (hitAt == null) return 0.0f;

        float elapsed = now - hitAt;
        if (elapsed >= HIT_FLASH_MS) {
            HIT_FLASH_MS_BY_ID.remove(displayTarget.getId());
            return 0.0f;
        }
        float t = 1.0f - elapsed / HIT_FLASH_MS;
        return AnimationUtility.easeOutCubic(AnimationUtility.clamp01(t));
    }

    private EquipmentLayout buildEquipmentLayout(LivingEntity target,
                                                 boolean preview,
                                                 TextRenderer textRenderer,
                                                 float scale) {
        if (!isEquipmentEnabled() || target == null || textRenderer == null || scale <= 0.0f) {
            return new EquipmentLayout(List.of(), 0.0f, 0.0f);
        }

        List<EquipmentEntry> entries = collectEquipmentEntries(target, preview, textRenderer, scale);
        if (entries.isEmpty()) {
            return new EquipmentLayout(List.of(), 0.0f, 0.0f);
        }

        float totalWidth = 0.0f;
        for (int i = 0; i < entries.size(); i++) {
            totalWidth += entries.get(i).width();
            if (i < entries.size() - 1) {
                totalWidth += EQUIPMENT_GAP * scale;
            }
        }
        float totalHeight = EQUIPMENT_TOP_GAP * scale + EQUIPMENT_SLOT * scale + EQUIPMENT_BOTTOM_PAD * scale;
        return new EquipmentLayout(entries, totalWidth, totalHeight);
    }

    private EffectLayout buildEffectLayout(LivingEntity target,
                                           boolean preview,
                                           TextRenderer textRenderer,
                                           float scale) {
        if (!isEffectsEnabled() || target == null || textRenderer == null || scale <= 0.0f) {
            return new EffectLayout(List.of(), 0.0f, 0.0f);
        }

        List<MobEffectInstance> effects = collectTargetEffects(target, preview);
        if (effects.isEmpty()) {
            return new EffectLayout(List.of(), 0.0f, 0.0f);
        }

        List<EffectChip> chips = new ArrayList<>();
        TextRenderer chipRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, textRenderer);
        float textScale = EFFECTS_TEXT_SCALE * scale;
        int limit = Math.min(4, effects.size());
        float totalWidth = 0.0f;

        for (int i = 0; i < limit; i++) {
            MobEffectInstance inst = effects.get(i);
            Identifier iconId = resolveEffectIcon(inst);
            String text = compactEffectText(target.getId(), inst);
            float width = EFFECTS_CHIP_PAD_X * 2.0f * scale + EFFECTS_ICON_SIZE * scale;
            if (!text.isEmpty()) {
                width += EFFECTS_TEXT_GAP * scale + measureText(chipRenderer, text, textScale);
            }
            chips.add(new EffectChip(inst, iconId, text, width));
            totalWidth += width;
            if (i < limit - 1) {
                totalWidth += EFFECTS_CHIP_GAP * scale;
            }
        }

        float totalHeight = (EFFECTS_CHIP_HEIGHT + EFFECTS_PANEL_GAP) * scale;
        return new EffectLayout(chips, totalWidth, totalHeight);
    }

    private List<EquipmentEntry> collectEquipmentEntries(LivingEntity target,
                                                         boolean preview,
                                                         TextRenderer textRenderer,
                                                         float scale) {
        List<EquipmentEntry> entries = new ArrayList<>();

        if (preview) {
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            ItemStack apple = new ItemStack(Items.GOLDEN_APPLE);
            ItemStack head = new ItemStack(Items.DIAMOND_HELMET);
            ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
            ItemStack legs = new ItemStack(Items.DIAMOND_LEGGINGS);
            ItemStack feet = new ItemStack(Items.DIAMOND_BOOTS);

            sword.setDamageValue(Math.max(1, sword.getMaxDamage() / 6));
            head.setDamageValue(Math.max(1, head.getMaxDamage() / 3));
            chest.setDamageValue(Math.max(1, chest.getMaxDamage() / 2));
            legs.setDamageValue(Math.max(1, legs.getMaxDamage() / 4));
            feet.setDamageValue(Math.max(1, feet.getMaxDamage() / 5));

            addEquipmentEntry(entries, sword, scale);
            addEquipmentEntry(entries, apple, scale);
            addEquipmentEntry(entries, head, scale);
            addEquipmentEntry(entries, chest, scale);
            addEquipmentEntry(entries, legs, scale);
            addEquipmentEntry(entries, feet, scale);
            return entries;
        }

        addEquipmentEntry(entries, target.getMainHandItem(), scale);
        addEquipmentEntry(entries, target.getOffhandItem(), scale);
        addEquipmentEntry(entries, target.getItemBySlot(EquipmentSlot.HEAD), scale);
        addEquipmentEntry(entries, target.getItemBySlot(EquipmentSlot.CHEST), scale);
        addEquipmentEntry(entries, target.getItemBySlot(EquipmentSlot.LEGS), scale);
        addEquipmentEntry(entries, target.getItemBySlot(EquipmentSlot.FEET), scale);
        return entries;
    }

    private void addEquipmentEntry(List<EquipmentEntry> entries,
                                   ItemStack stack,
                                   float scale) {
        float baseWidth = EQUIPMENT_SLOT * scale;
        entries.add(new EquipmentEntry(stack == null ? ItemStack.EMPTY : stack, baseWidth));
    }

    private List<MobEffectInstance> collectTargetEffects(LivingEntity target, boolean preview) {
        if (target == null) {
            return List.of();
        }
        if (target == mc.player && !effectsSelf.get() && !preview) {
            return List.of();
        }
        if (target instanceof Player player) {
            StatusEffectView.sync(mc);
            List<MobEffectInstance> effects = StatusEffectView.collectHudEffects(player);
            if (!effects.isEmpty() || !preview) {
                return effects;
            }
        } else {
            List<MobEffectInstance> direct = new ArrayList<>(target.getActiveEffects());
            if (!direct.isEmpty()) {
                return direct;
            }
        }

        if (!preview) {
            return List.of();
        }

        List<MobEffectInstance> sample = new ArrayList<>();
        sample.add(new MobEffectInstance(net.minecraft.world.effect.MobEffects.STRENGTH, 2450, 1));
        sample.add(new MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 1640, 0));
        sample.add(new MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 920, 0));
        return sample;
    }

    private Identifier resolveEffectIcon(MobEffectInstance inst) {
        if (inst == null) {
            return null;
        }
        return inst.getEffect().unwrapKey()
                .map(key -> Identifier.fromNamespaceAndPath("minecraft", "textures/mob_effect/" + key.identifier().getPath() + ".png"))
                .orElse(null);
    }

    private String compactEffectText(int entityId, MobEffectInstance inst) {
        if (inst == null) {
            return "";
        }
        String duration = formatEffectDuration(entityId, inst);
        if (!duration.isEmpty()) {
            return duration;
        }
        int level = inst.getAmplifier() + 1;
        return level > 1 ? roman(level) : "";
    }

    private String formatEffectDuration(int entityId, MobEffectInstance inst) {
        if (inst == null) {
            return "";
        }
        if (StatusEffectTracker.shouldHideDuration(entityId, inst.getEffect())) {
            return "";
        }
        int ticks = inst.getDuration();
        if (ticks < 0 || ticks >= 32767) {
            return "";
        }
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return seconds < 10 ? minutes + ":0" + seconds : minutes + ":" + seconds;
    }

    private String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(n);
        };
    }

    private boolean isThemeMode() {
        return COLOR_THEME.equals(colorMode.get());
    }

    private boolean isAccentPanelStyle() {
        return isThemeMode() && HudRenderUtil.PANEL_STYLE_ACCENT.equals(panelStyle.get());
    }

    private boolean isCustomMode() {
        return COLOR_CUSTOM.equals(colorMode.get());
    }

    private boolean hasEffect() {
        return !EFFECT_NONE.equals(bgEffect.get());
    }

    private boolean isBlurEffect() {
        return EFFECT_BLUR.equals(bgEffect.get());
    }

    private boolean isGlassEffect() {
        return EFFECT_GLASS.equals(bgEffect.get());
    }

    private boolean isEffectsEnabled() {
        return !EFFECTS_OFF.equals(effectsMode.get());
    }

    private boolean isEquipmentEnabled() {
        return !EQUIPMENT_OFF.equals(equipmentMode.get());
    }

    private boolean isArcHealthMode() {
        return HP_MODE_ARC.equals(hpMode.get());
    }

    private void drawEmptyEquipmentCross(Renderer2D renderer,
                                         float x,
                                         float y,
                                         float width,
                                         float height,
                                         float scaleFactor,
                                         float alphaFactor) {
        float inset = Math.max(2.0f * scaleFactor, width * 0.28f);
        float stroke = Math.max(0.6f, 0.85f * scaleFactor);
        int color = HudRenderUtil.scaleAlpha(uiTextSecondary, alphaFactor);
        renderer.line(x + inset, y + inset, x + width - inset, y + height - inset, color);
        renderer.line(x + width - inset, y + inset, x + inset, y + height - inset, color);
        if (stroke > 1.0f) {
            float nudge = stroke * 0.35f;
            renderer.line(x + inset + nudge, y + inset, x + width - inset + nudge, y + height - inset, color);
            renderer.line(x + width - inset - nudge, y + inset, x + inset - nudge, y + height - inset, color);
        }
    }

    private record EquipmentEntry(ItemStack stack, float width) {
    }

    private record EquipmentLayout(List<EquipmentEntry> entries, float width, float height) {
    }

    private record EquipmentRenderTask(ItemStack stack, float x, float y, float scale) {
    }

    private record EffectChip(MobEffectInstance effect, Identifier iconId, String text, float width) {
    }

    private record EffectLayout(List<EffectChip> chips, float width, float height) {
    }
}
