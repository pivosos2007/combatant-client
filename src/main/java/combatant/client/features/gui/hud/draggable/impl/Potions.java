/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.values.*;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import combatant.client.config.SettingDef;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.HudTextEffects;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 20)
public final class Potions extends DraggableHudElement {

    {
        defaultLayout(31.729004f, 182.0f);
    }


    private static final float HEADER_HEIGHT = 15.5f;
    private static final float BODY_Y_OFFSET = 18.5f;
    private static final float CONTENT_START_Y = 25.0f;
    private static final float ROW_STEP = 11.0f;
    private static final float MIN_WIDTH = 95.0f;
    private static final float TITLE_TEXT_X = 22.0f;
    private static final float COUNT_LABEL_OFFSET = 22.0f;
    private static final int BLINK_THRESHOLD_TICKS = 200;
    private static final double BLINK_SPEED = 0.009;
    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";
    private static final int NEGATIVE_EFFECT_RGB = 0xFFFF554B;
    private static final Identifier[] PREVIEW_ICONS = new Identifier[]{
            Identifier.fromNamespaceAndPath("minecraft", "textures/mob_effect/speed.png"),
            Identifier.fromNamespaceAndPath("minecraft", "textures/mob_effect/haste.png"),
            Identifier.fromNamespaceAndPath("minecraft", "textures/mob_effect/fire_resistance.png"),
            Identifier.fromNamespaceAndPath("minecraft", "textures/mob_effect/regeneration.png"),
            Identifier.fromNamespaceAndPath("minecraft", "textures/mob_effect/night_vision.png")
    };

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final ScriptedListHudPanel scriptedPanel = new ScriptedListHudPanel();
    private final NumberValue<Double> scaleValue =
            new NumberValue<>("potions_scale", 1.05, HudPanelLayoutModes.SCALE_MIN, HudPanelLayoutModes.SCALE_MAX);
    private final ModeValue layoutMode =
            new ModeValue("potions_layout", "Unified Divider", HudPanelLayoutModes.SPLIT_HEADER, HudPanelLayoutModes.UNIFIED_DIVIDER);
    private final ModeValue colorMode =
            new ModeValue("potions_color_mode", "Theme", COLOR_THEME, COLOR_CUSTOM);
    private final ModeValue panelStyle =
            new ModeValue("potions_panel_style", HudRenderUtil.PANEL_STYLE_DEFAULT,
                    HudRenderUtil.PANEL_STYLE_DEFAULT, HudRenderUtil.PANEL_STYLE_ACCENT,
                    HudRenderUtil.PANEL_STYLE_GRADIENT);
    private final NumberValue<Integer> themeGradientStrength =
            new NumberValue<>("potions_theme_gradient_strength", 72, 0, 100);
    private final ModeValue bgEffect =
            new ModeValue("potions_bg_effect", "Blur", EFFECT_NONE, EFFECT_BLUR);
    private final RGBAColorValue bg =
            new RGBAColorValue("potions_bg", "#EB784F4F");
    private final RGBAColorValue bg2 =
            new RGBAColorValue("potions_bg_secondary", "#F7161616");
    private final NumberValue<Integer> bgAlpha =
            new NumberValue<>("potions_bg_alpha", 200, 0, 255);
    private final BooleanValue strokeEnabled =
            new BooleanValue("potions_stroke_enabled", false);
    private final NumberValue<Integer> strokeAlpha =
            new NumberValue<>("potions_stroke_alpha", 160, 0, 255);
    private final BooleanValue strokeGradient =
            new BooleanValue("potions_stroke_gradient", true);
    private final BooleanValue shadowEnabled =
            new BooleanValue("potions_shadow_enabled", true);
    private final ModeValue shadowMode =
            new ModeValue("potions_shadow_mode", HudRenderUtil.SHADOW_MODE_BLACK,
                    HudRenderUtil.SHADOW_MODE_BLACK, HudRenderUtil.SHADOW_MODE_THEME);
    private final NumberValue<Integer> themeShadowStrength =
            new NumberValue<>("potions_theme_shadow_strength", 100, 0, 100);
    private final NumberValue<Integer> shadowAlpha =
            new NumberValue<>("potions_shadow_alpha", 38, 0, 255);
    private final RGBColorValue stroke =
            new RGBColorValue("potions_stroke", "#5A5A5A");
    private final RGBColorValue text =
            new RGBColorValue("potions_text", "#FFFFFF");
    private final RGBColorValue muted =
            new RGBColorValue("potions_muted", "#A5A5A5");
    private final NumberValue<Integer> blurAlpha =
            new NumberValue<>("potions_blur_alpha", 159, 0, 255);
    private final BooleanValue headerIconPulse =
            new BooleanValue("potions_header_icon_pulse", true);
    private final NumberValue<Integer> headerIconPulseSpeed =
            new NumberValue<>("potions_header_icon_pulse_speed", 18, 1, 60);
    private final NumberValue<Integer> headerIconPulseIntensity =
            new NumberValue<>("potions_header_icon_pulse_intensity", 100, 0, 100);

    private final Map<String, Float> entryAnim = new LinkedHashMap<>();
    private final Map<String, Row> lastEntries = new LinkedHashMap<>();
    private float displayWidth = -1.0f;
    private float displayHeight = -1.0f;
    private float visibilityAnim = 0.0f;
    private int uiHeaderLeft;
    private int uiHeaderRight;
    private int uiBodyLeft;
    private int uiBodyRight;
    private int uiOutline;
    private int uiText;
    private int uiMuted;
    private int uiCounter;
    private int uiTitleText;
    private int uiAmpText;
    private int uiDivider;
    private int uiBlurTint;

    public Potions() {
        super("potions", "Potions", true);
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.number(scaleValue));
        defs.add(SettingDef.mode(layoutMode));
        defs.add(SettingDef.mode(colorMode));
        defs.add(SettingDef.mode(panelStyle).visibleWhen(this::isThemeMode));
        defs.add(SettingDef.number(themeGradientStrength).visibleWhen(this::isGradientPanelStyle));
        defs.add(SettingDef.color(bg).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.color(bg2).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.bool(strokeEnabled));
        defs.add(SettingDef.colorNoAlpha(stroke).visibleWhen(() -> strokeEnabled.get() && isCustomMode()));
        defs.add(SettingDef.number(strokeAlpha).visibleWhen(strokeEnabled::get));
        defs.add(SettingDef.bool(strokeGradient).visibleWhen(() -> strokeEnabled.get() && isThemeMode()));
        defs.add(SettingDef.bool(shadowEnabled));
        defs.add(SettingDef.mode(shadowMode).visibleWhen(() -> shadowEnabled.get() && isThemeMode()));
        defs.add(SettingDef.number(themeShadowStrength).visibleWhen(this::isThemeShadow));
        defs.add(SettingDef.number(shadowAlpha).visibleWhen(shadowEnabled::get));
        defs.add(SettingDef.number(bgAlpha).visibleWhen(this::isThemeMode));
        defs.add(SettingDef.colorNoAlpha(text).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(muted).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.mode(bgEffect));
        defs.add(SettingDef.number(blurAlpha).visibleWhen(this::hasEffect));
        defs.add(SettingDef.bool(headerIconPulse));
        defs.add(SettingDef.number(headerIconPulseSpeed)
                .visibleWhen(headerIconPulse::get));
        defs.add(SettingDef.number(headerIconPulseIntensity)
                .visibleWhen(headerIconPulse::get));
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        this.x = screenW - 120.0f;
        this.y = 20.0f;
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
        boolean forceVisible = DraggableHudElementRegistry.isForceVisible();
        if (mc == null || (mc.player == null && !forceVisible)) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        boolean chatPreview = ClientScreen.current() instanceof ChatScreen;
        List<Row> rows = collectRows();
        List<AnimatedRow> animatedRows = animateRows(rows);
        boolean showExampleRow = rows.isEmpty() && (forceVisible || chatPreview);
        boolean showWidget = !rows.isEmpty() || showExampleRow;
        visibilityAnim = HudRenderUtil.animateVisibility(visibilityAnim, showWidget);
        float widgetScale = HudRenderUtil.visibilityScale(visibilityAnim);
        if (animatedRows.isEmpty() && !showExampleRow && visibilityAnim <= 0.0f) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        updatePalette();

        TextRenderer headerIconRenderer = Fonts.renderer("IconsNur", FontInfo.Type.Regular, TextRenderer.get());
        TextRenderer headerTextRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, TextRenderer.get());
        TextRenderer rowTextRenderer = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, TextRenderer.get());
        if (headerTextRenderer == null) headerTextRenderer = textRenderer;
        if (rowTextRenderer == null) rowTextRenderer = textRenderer;
        if (headerIconRenderer == null) headerIconRenderer = textRenderer;

        float baseScale = HudScale.scale(screenW, screenH) * 1.1f * HudPanelLayoutModes.effectiveScale(scaleValue);
        float rowStep = ROW_STEP * baseScale;
        float fontScale = 0.98f * (hud.getFontSize() / 18.0f);

        headerIconRenderer.begin(fontScale, true, false);
        float headerIconH = (float) headerIconRenderer.getHeight(false);
        headerIconRenderer.end();

        headerTextRenderer.begin(fontScale, true, false);
        float headerTextH = (float) headerTextRenderer.getHeight(false);
        float titleWidth = (float) headerTextRenderer.getWidth("Potions", false);
        headerTextRenderer.end();

        rowTextRenderer.begin(fontScale, true, false);
        float rowTextH = (float) rowTextRenderer.getHeight(false);
        float maxWidth = MIN_WIDTH * baseScale;
        if (showExampleRow) {
            float previewWidth = (float) rowTextRenderer.getWidth("Example effect**:**", false) + (30.0f * baseScale);
            maxWidth = Math.max(maxWidth, previewWidth);
        } else {
            for (AnimatedRow animatedRow : animatedRows) {
                Row row = animatedRow.row();
                String label = row.name() + (row.amp().isEmpty() ? "" : " " + row.amp());
                float widthCandidate = (float) rowTextRenderer.getWidth(label + row.duration(), false) + (30.0f * baseScale);
                maxWidth = Math.max(maxWidth, widthCandidate);
            }
        }
        rowTextRenderer.end();

        rowTextRenderer.begin(fontScale * 0.92f, false, false);
        String activeCountText = Integer.toString(rows.size());
        float activeLabelWidth = (float) rowTextRenderer.getWidth("Active:", false);
        float activeCountWidth = (float) rowTextRenderer.getWidth(activeCountText, false);
        rowTextRenderer.end();
        float headerWidth = (TITLE_TEXT_X * baseScale) + titleWidth
                + activeLabelWidth + activeCountWidth
                + ((COUNT_LABEL_OFFSET + 12.0f) * baseScale);
        maxWidth = Math.max(maxWidth, headerWidth);

        float contentHeight = showExampleRow ? rowStep : totalAnimatedHeight(animatedRows, rowStep);
        float targetHeight = (showExampleRow || !animatedRows.isEmpty())
                ? (CONTENT_START_Y * baseScale + contentHeight)
                : (HEADER_HEIGHT * baseScale);

        displayWidth = HudRenderUtil.animateDimension(displayWidth, maxWidth);
        displayHeight = HudRenderUtil.animateDimension(displayHeight, targetHeight);
        width = displayWidth;
        height = displayHeight;

        if (!showWidget && widgetScale <= 0.001f && animatedRows.isEmpty()) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        float drawScale = Math.max(0.0f, widgetScale);
        float drawWidth = displayWidth * drawScale;
        float drawHeight = displayHeight * drawScale;
        if (drawWidth <= 0.0f || drawHeight <= 0.0f) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        float drawX = x + ((displayWidth - drawWidth) * 0.5f);
        float drawY = y + ((displayHeight - drawHeight) * 0.5f);
        float drawBaseScale = baseScale * drawScale;
        float drawFontScale = fontScale * drawScale;
        float drawHeaderIconH = headerIconH * drawScale;
        float drawHeaderTextH = headerTextH * drawScale;
        float drawRowTextH = rowTextH * drawScale;
        int resolvedHeaderIconColor = uiCounter;
        if (headerIconPulse.get()) {
            int pulsedHeaderIconColor = HudTextEffects.animatedColor(
                    uiCounter,
                    HudTextEffects.Effect.PULSE,
                    headerIconPulseSpeed.get(),
                    (float) (Util.getMillis() / 1000.0),
                    0.0f
            );
            resolvedHeaderIconColor = HudRenderUtil.mixColor(
                    uiCounter,
                    pulsedHeaderIconColor,
                    headerIconPulseIntensity.get() / 100.0f
            );
        }

        HudRenderUtil.ThemeGradient headerIconGradient = isThemeMode()
                ? HudRenderUtil.themeForegroundGradient(255)
                : new HudRenderUtil.ThemeGradient(resolvedHeaderIconColor, resolvedHeaderIconColor, 45.0f);
        if (isThemeMode() && headerIconPulse.get()) {
            headerIconGradient = HudTextEffects.animatedGradient(
                    headerIconGradient,
                    HudTextEffects.Effect.PULSE,
                    headerIconPulseSpeed.get(),
                    (float) (Util.getMillis() / 1000.0),
                    0.0f,
                    headerIconPulseIntensity.get() / 100.0f
            );
        }

        List<LinkedHashMap<String, Object>> panelRows = ScriptedListHudPanel.rows();
        rowTextRenderer.begin(drawFontScale, false, false);
        if (showExampleRow) {
            Identifier previewIcon = PREVIEW_ICONS[(int) ((System.currentTimeMillis() / 1000L) % PREVIEW_ICONS.length)];
            List<LinkedHashMap<String, Object>> parts = List.of(
                    ScriptedListHudPanel.textPart("Example effect", withAlpha(uiText & 0x00FFFFFF, 255), 0.0f)
            );
            LinkedHashMap<String, Object> row = ScriptedListHudPanel.row(
                    "preview",
                    "texture",
                    ScriptedListHudPanel.idString(previewIcon),
                    parts,
                    "**:**",
                    withAlpha(uiCounter & 0x00FFFFFF, 255),
                    withAlpha(uiMuted & 0x00FFFFFF, 130),
                    1.0f
            );
            row.put("iconTint", ScriptedListHudPanel.hex(withAlpha(0x00FFFFFF, 255)));
            panelRows.add(row);
        } else {
            for (AnimatedRow animatedRow : animatedRows) {
                float anim = animatedRow.anim();
                if (anim <= 0.01f) continue;
                Row rowData = animatedRow.row();
                int rowBaseAlpha = clamp255(Math.round(255.0f * anim));
                int blinkAlpha = getBlinkAlpha(rowData, rowBaseAlpha, 100);
                boolean bad = isBadEffect(rowData.effect());
                int nameColor = withAlpha(bad ? NEGATIVE_EFFECT_RGB : (uiText & 0x00FFFFFF), blinkAlpha);
                int dividerColor = withAlpha((bad ? NEGATIVE_EFFECT_RGB : (uiMuted & 0x00FFFFFF)), Math.max(24, blinkAlpha - 125));
                List<LinkedHashMap<String, Object>> parts = new ArrayList<>(2);
                parts.add(ScriptedListHudPanel.textPart(rowData.name(), nameColor, 0.0f));
                if (!rowData.amp().isEmpty()) {
                    float labelWidth = (float) rowTextRenderer.getWidth(rowData.name(), false);
                    parts.add(ScriptedListHudPanel.textPart(" " + rowData.amp(), withAlpha(uiAmpText & 0x00FFFFFF, blinkAlpha), labelWidth));
                }
                LinkedHashMap<String, Object> row = ScriptedListHudPanel.row(
                        rowData.key(),
                        "texture",
                        ScriptedListHudPanel.idString(rowData.iconId()),
                        parts,
                        rowData.duration(),
                        withAlpha(uiCounter & 0x00FFFFFF, blinkAlpha),
                        dividerColor,
                        anim
                );
                row.put("iconTint", ScriptedListHudPanel.hex(withAlpha(0x00FFFFFF, blinkAlpha)));
                panelRows.add(row);
            }
        }
        rowTextRenderer.end();

        if (shadowEnabled.get()) {
            HudRenderUtil.drawHudShadow(
                    renderer, drawX, drawY, drawWidth, drawHeight,
                    ScriptedListHudPanel.PANEL_RADIUS * drawBaseScale, drawBaseScale,
                    isThemeShadow(), shadowAlpha.get(), 1.0f,
                    themeShadowStrength.get() / 100.0f
            );
        }

        scriptedPanel.render(
                renderer,
                textRenderer,
                ctx,
                tickDelta,
                new ScriptedListHudPanel.Panel(
                        ScriptedListHudPanel.POTIONS,
                        new ScriptedListHudPanel.Palette(uiHeaderLeft, uiHeaderRight, uiBodyLeft, uiBodyRight,
                                uiOutline, uiText, uiMuted, uiCounter, uiTitleText, uiDivider, uiBlurTint),
                        drawX,
                        drawY,
                        drawWidth,
                        drawHeight,
                        drawScale,
                        drawBaseScale,
                        drawFontScale,
                        drawHeaderIconH,
                        drawHeaderTextH,
                        drawRowTextH,
                        activeLabelWidth * drawScale,
                        activeCountWidth * drawScale,
                        rows.size(),
                        hasEffect(),
                        Math.min(1.0f, (blurAlpha.get() / 255.0f) * (isThemeMode() ? 1.15f : 1.0f)),
                        resolvedHeaderIconColor,
                        isThemeMode(),
                        headerIconGradient.start(),
                        headerIconGradient.end(),
                        headerIconGradient.angleDeg(),
                        HudPanelLayoutModes.current(layoutMode),
                        strokeEnabled.get(),
                        strokeAlpha.get() / 255.0f,
                        isThemeMode() && strokeGradient.get(),
                        resolveStrokeGradientStart(),
                        resolveStrokeGradientEnd(),
                        true,
                        panelRows
                )
        );
    }

    @Override
    public boolean isDraggable() {
        return super.isDraggable();
    }

    private List<Row> collectRows() {
        List<Row> rows = new ArrayList<>();
        if (mc != null && mc.player != null) {
            mc.player.getActiveEffects().stream()
                    .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                            I18n.get(a.getDescriptionId()),
                            I18n.get(b.getDescriptionId())
                    ))
                    .forEach(inst -> {
                        String name = I18n.get(inst.getDescriptionId());
                        String amp = inst.getAmplifier() > 0 ? Integer.toString(inst.getAmplifier() + 1) : "";
                        String duration = formatDuration(inst);
                        Identifier effectId = inst.getEffect().unwrapKey().map(ResourceKey::identifier).orElse(null);
                        String key = effectId != null ? effectId.toString() : name;
                        Identifier iconId = effectId != null
                                ? Identifier.fromNamespaceAndPath("minecraft", "textures/mob_effect/" + effectId.getPath() + ".png")
                                : null;
                        rows.add(new Row(key, inst, name, amp, duration, iconId));
                    });
        }
        return rows;
    }

    private List<AnimatedRow> animateRows(List<Row> rows) {
        Map<String, Row> current = new LinkedHashMap<>();
        for (Row row : rows) {
            current.put(row.key(), row);
        }

        Map<String, Row> merged = new LinkedHashMap<>();
        merged.putAll(current);
        for (Map.Entry<String, Row> entry : lastEntries.entrySet()) {
            merged.putIfAbsent(entry.getKey(), entry.getValue());
        }
        lastEntries.clear();
        lastEntries.putAll(merged);

        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Row> entry : lastEntries.entrySet()) {
            String key = entry.getKey();
            float target = current.containsKey(key) ? 1.0f : 0.0f;
            float anim = entryAnim.getOrDefault(key, 0.0f);
            anim = AnimationUtility.approach(anim, target, AnimationUtility.deltaTime(), 12.0f);
            anim = AnimationUtility.snap(anim, target, 0.02f);
            if (anim <= 0.0f && target <= 0.0f) {
                toRemove.add(key);
            } else {
                entryAnim.put(key, anim);
            }
        }
        for (String key : toRemove) {
            entryAnim.remove(key);
            lastEntries.remove(key);
        }

        List<AnimatedRow> out = new ArrayList<>();
        for (Map.Entry<String, Row> entry : lastEntries.entrySet()) {
            float anim = entryAnim.getOrDefault(entry.getKey(), 0.0f);
            if (anim > 0.01f) {
                out.add(new AnimatedRow(entry.getValue(), anim));
            }
        }
        return out;
    }

    private float totalAnimatedHeight(List<AnimatedRow> rows, float rowStep) {
        float out = 0.0f;
        for (AnimatedRow row : rows) {
            out += rowStep * row.anim();
        }
        return out;
    }

    private String formatDuration(MobEffectInstance inst) {
        int rawTicks = inst.getDuration();
        if (rawTicks < 0) {
            return "**:**";
        }
        int ticks = Math.max(0, rawTicks);
        int minutes = ticks / 1200;
        if (minutes > 60) {
            return "**:**";
        }
        return minutes + ":" + String.format("%02d", (ticks % 1200) / 20);
    }

    private int getBlinkAlpha(Row row, int baseAlpha, int minAlphaFloor) {
        MobEffectInstance inst = row.effect();
        if (inst == null) {
            return baseAlpha;
        }
        int duration = inst.getDuration();
        if (duration <= 0 || duration > BLINK_THRESHOLD_TICKS) {
            return baseAlpha;
        }

        double wave = Math.sin(System.currentTimeMillis() * BLINK_SPEED);
        float factor = (float) ((wave + 1.0) * 0.5);
        int minAlpha = Math.max(0, Math.min(baseAlpha, minAlphaFloor));
        return clamp255(Math.round(minAlpha + (baseAlpha - minAlpha) * (1.0f - factor)));
    }

    private boolean isBadEffect(MobEffectInstance effect) {
        return effect != null && effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL;
    }

    private void updatePalette() {
        if (isThemeMode()) {
            int alpha = bgAlpha.get();
            int window = HudRenderUtil.setAlpha(theme().windowBg(), alpha);
            int header = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().windowHeader(), theme().surface(), 0.18f),
                    Math.min(255, alpha + 14)
            );
            int surface = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().surface(), theme().windowBg(), 0.22f),
                    Math.min(255, alpha + 6)
            );
            int deep = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().surface(), theme().windowHeader(), 0.42f),
                    Math.min(255, alpha + 18)
            );

            uiHeaderLeft = HudRenderUtil.mixColor(header, window, 0.22f);
            uiHeaderRight = HudRenderUtil.mixColor(surface, header, 0.52f);
            uiBodyLeft = HudRenderUtil.mixColor(window, surface, 0.24f);
            uiBodyRight = HudRenderUtil.mixColor(deep, surface, 0.18f);
            if (isAccentPanelStyle()) {
                uiHeaderLeft = HudRenderUtil.accentSurface(uiHeaderLeft, 0.18f);
                uiHeaderRight = HudRenderUtil.accentSurface(uiHeaderRight, 0.26f);
                uiBodyLeft = HudRenderUtil.accentSurface(uiBodyLeft, 0.20f);
                uiBodyRight = HudRenderUtil.accentSurface(uiBodyRight, 0.30f);
            } else if (isGradientPanelStyle()) {
                float strength = themeGradientStrength.get() / 100.0f;
                HudRenderUtil.ThemeGradient gradient = HudRenderUtil.themePanelGradient(255);
                uiHeaderLeft = HudRenderUtil.gradientSurface(uiHeaderLeft, gradient.start(), strength);
                uiHeaderRight = HudRenderUtil.gradientSurface(uiHeaderRight, gradient.end(), strength);
                uiBodyLeft = HudRenderUtil.gradientSurface(uiBodyLeft, gradient.start(), strength * 0.92f);
                uiBodyRight = HudRenderUtil.gradientSurface(uiBodyRight, gradient.end(), strength);
            }
            uiOutline = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().windowStroke(), theme().strokeSoft(), 0.18f),
                    Math.min(255, Math.max(182, alpha + 36))
            );
            uiText = theme().textPrimary();
            uiTitleText = HudRenderUtil.mixColor(theme().textPrimary(), theme().accent(), 0.14f);
            uiMuted = HudRenderUtil.mixColor(theme().textMuted(), theme().textPrimary(), 0.16f);
            uiCounter = HudRenderUtil.mixColor(theme().accent(), theme().textPrimary(), 0.38f);
            uiAmpText = HudRenderUtil.mixColor(theme().textPrimary(), theme().accent(), 0.22f);
            uiDivider = HudRenderUtil.scaleAlpha(uiMuted, 0.68f);
            uiBlurTint = HudRenderUtil.mixColor(
                    HudRenderUtil.mixColor(uiHeaderRight, uiBodyRight, 0.5f),
                    theme().accent(),
                    0.14f
            );
            return;
        }

        uiHeaderLeft = bg.getArgb();
        uiHeaderRight = bg2.getArgb();
        uiBodyLeft = HudRenderUtil.mixColor(bg.getArgb(), bg2.getArgb(), 0.15f);
        uiBodyRight = bg2.getArgb();
        uiOutline = stroke.getArgb();
        uiText = text.getArgb();
        uiTitleText = uiText;
        uiMuted = muted.getArgb();
        uiCounter = HudRenderUtil.mixColor(text.getArgb(), 0xFFE1E1FF, 0.20f);
        uiAmpText = uiText;
        uiDivider = HudRenderUtil.scaleAlpha(uiMuted, 0.55f);
        uiBlurTint = HudRenderUtil.mixColor(uiHeaderLeft, uiBodyRight, 0.5f);
    }

    private int withAlpha(int rgb, int alpha) {
        return (clamp255(alpha) << 24) | (rgb & 0x00FFFFFF);
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

    private record Row(String key, MobEffectInstance effect, String name, String amp, String duration,
                       Identifier iconId) {
    }

    private record AnimatedRow(Row row, float anim) {
    }
}
