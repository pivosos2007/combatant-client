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
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import combatant.client.config.SettingDef;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.HudTextEffects;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.relations.StaffData;
import combatant.client.features.relations.StaffTracker;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.PlayerHeadRenderer;
import combatant.client.util.player.PlayerSkinResolver;
import combatant.client.util.text.LegacyTextUtil;
import combatant.client.util.text.TextRenderUtil;
import combatant.client.util.text.TextRenderUtil.Part;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 50)
public final class Admins extends DraggableHudElement {

    {
        defaultLinkedLayout(16.0f, 212.0f, "keybinds", "RIGHT", 0.0f, 0.0f);
    }


    private static final float HEADER_HEIGHT = 15.5f;
    private static final float CONTENT_START_Y = 25.0f;
    private static final float ROW_STEP = 11.0f;
    private static final float MIN_WIDTH = 96.0f;
    private static final float ROW_DIVIDER_X = 15.0f;
    private static final float ROW_DIVIDER_H = 6.0f;
    private static final float TITLE_TEXT_X = 22.0f;
    private static final float COUNT_LABEL_OFFSET = 22.0f;
    private static final float ROW_HEAD_X = 3.5f;
    private static final float ROW_HEAD_SIZE = 8.0f;
    private static final float ROW_TEXT_X = 18.0f;
    private static final float ROW_STATUS_RIGHT = 8.0f;
    private static final float ROW_CONTENT_CENTER_OFFSET = 1.95f;
    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final ScriptedListHudPanel scriptedPanel = new ScriptedListHudPanel();
    private final NumberValue<Double> scaleValue =
            new NumberValue<>("admins_scale", 1.0, HudPanelLayoutModes.SCALE_MIN, HudPanelLayoutModes.SCALE_MAX);
    private final ModeValue layoutMode =
            new ModeValue("admins_layout", "Split Header", HudPanelLayoutModes.SPLIT_HEADER, HudPanelLayoutModes.UNIFIED_DIVIDER);
    private final ModeValue colorMode =
            new ModeValue("admins_color_mode", "Theme", COLOR_THEME, COLOR_CUSTOM);
    private final ModeValue panelStyle =
            new ModeValue("admins_panel_style", HudRenderUtil.PANEL_STYLE_DEFAULT,
                    HudRenderUtil.PANEL_STYLE_DEFAULT, HudRenderUtil.PANEL_STYLE_ACCENT,
                    HudRenderUtil.PANEL_STYLE_GRADIENT);
    private final NumberValue<Integer> themeGradientStrength =
            new NumberValue<>("admins_theme_gradient_strength", 72, 0, 100);
    private final ModeValue bgEffect =
            new ModeValue("admins_bg_effect", "Blur", EFFECT_NONE, EFFECT_BLUR);
    private final RGBAColorValue bg =
            new RGBAColorValue("admins_bg", "#EB111318");
    private final RGBAColorValue bg2 =
            new RGBAColorValue("admins_bg_secondary", "#F7161616");
    private final NumberValue<Integer> bgAlpha =
            new NumberValue<>("admins_bg_alpha", 131, 0, 255);
    private final BooleanValue strokeEnabled =
            new BooleanValue("admins_stroke_enabled", false);
    private final NumberValue<Integer> strokeAlpha =
            new NumberValue<>("admins_stroke_alpha", 160, 0, 255);
    private final BooleanValue strokeGradient =
            new BooleanValue("admins_stroke_gradient", true);
    private final BooleanValue shadowEnabled =
            new BooleanValue("admins_shadow_enabled", true);
    private final ModeValue shadowMode =
            new ModeValue("admins_shadow_mode", HudRenderUtil.SHADOW_MODE_BLACK,
                    HudRenderUtil.SHADOW_MODE_BLACK, HudRenderUtil.SHADOW_MODE_THEME);
    private final NumberValue<Integer> themeShadowStrength =
            new NumberValue<>("admins_theme_shadow_strength", 100, 0, 100);
    private final NumberValue<Integer> shadowAlpha =
            new NumberValue<>("admins_shadow_alpha", 38, 0, 255);
    private final RGBColorValue stroke =
            new RGBColorValue("admins_stroke", "#5A5A5A");
    private final RGBColorValue text =
            new RGBColorValue("admins_text", "#FFFFFF");
    private final RGBColorValue muted =
            new RGBColorValue("admins_muted", "#A5A5A5");
    private final NumberValue<Integer> blurAlpha =
            new NumberValue<>("admins_blur_alpha", 255, 0, 255);
    private final BooleanValue headerIconPulse =
            new BooleanValue("admins_header_icon_pulse", true);
    private final NumberValue<Integer> headerIconPulseSpeed =
            new NumberValue<>("admins_header_icon_pulse_speed", 18, 1, 60);
    private final NumberValue<Integer> headerIconPulseIntensity =
            new NumberValue<>("admins_header_icon_pulse_intensity", 100, 0, 100);

    private final Map<String, Float> entryAnim = new LinkedHashMap<>();
    private final Map<String, StaffData.StaffEntry> lastEntries = new LinkedHashMap<>();
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
    private int uiDivider;
    private int uiBlurTint;

    public Admins() {
        super("admins", "Admins", true);
    }

    private static List<StaffData.StaffEntry> filterStaff(List<StaffData.StaffEntry> in) {
        List<StaffData.StaffEntry> out = new ArrayList<>(in.size());
        for (StaffData.StaffEntry entry : in) {
            if (entry.status() == StaffTracker.Status.OFFLINE && entry.offlineSince() <= 0) continue;
            if (entry.status() == StaffTracker.Status.WAITING && entry.lastGone() <= 0) continue;
            out.add(entry);
        }
        return out;
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
        defs.add(SettingDef.number(headerIconPulseSpeed).visibleWhen(headerIconPulse::get));
        defs.add(SettingDef.number(headerIconPulseIntensity).visibleWhen(headerIconPulse::get));
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        this.x = screenW - 200.0f;
        this.y = 80.0f;
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
        List<StaffData.StaffEntry> entries = filterStaff(StaffData.getEntries());
        List<AnimatedEntry> animatedEntries = animateEntries(entries);
        boolean showExampleRow = entries.isEmpty() && (forceVisible || chatPreview);
        boolean showWidget = !entries.isEmpty() || showExampleRow;
        visibilityAnim = HudRenderUtil.animateVisibility(visibilityAnim, showWidget);
        float widgetScale = HudRenderUtil.visibilityScale(visibilityAnim);
        if (animatedEntries.isEmpty() && !showExampleRow && visibilityAnim <= 0.0f) {
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
        float titleWidth = (float) headerTextRenderer.getWidth("Admins", false);
        headerTextRenderer.end();

        long now = System.currentTimeMillis();
        rowTextRenderer.begin(fontScale, true, false);
        float rowTextH = (float) rowTextRenderer.getHeight(false);
        float maxWidth = MIN_WIDTH * baseScale;
        if (showExampleRow) {
            float previewNameWidth = (float) rowTextRenderer.getWidth("Example Staff", false);
            float previewStatusWidth = (float) rowTextRenderer.getWidth("Vanish", false);
            maxWidth = Math.max(maxWidth, previewNameWidth + previewStatusWidth + (30.0f * baseScale));
        } else {
            for (AnimatedEntry animatedEntry : animatedEntries) {
                StaffData.StaffEntry entry = animatedEntry.entry();
                float nameWidth = measureLegacyName(rowTextRenderer, entry.displayName());
                float statusWidth = (float) rowTextRenderer.getWidth(formatStatus(entry, now), false);
                maxWidth = Math.max(maxWidth, nameWidth + statusWidth + (30.0f * baseScale));
            }
        }
        rowTextRenderer.end();

        rowTextRenderer.begin(fontScale * 0.92f, false, false);
        String activeCountText = Integer.toString(entries.size());
        float activeLabelWidth = (float) rowTextRenderer.getWidth("Active:", false);
        float activeCountWidth = (float) rowTextRenderer.getWidth(activeCountText, false);
        rowTextRenderer.end();

        float headerWidth = (TITLE_TEXT_X * baseScale) + titleWidth
                + activeLabelWidth + activeCountWidth
                + ((COUNT_LABEL_OFFSET + 12.0f) * baseScale);
        maxWidth = Math.max(maxWidth, headerWidth);

        float contentHeight = showExampleRow ? rowStep : totalAnimatedHeight(animatedEntries, rowStep);
        float targetHeight = (showExampleRow || !animatedEntries.isEmpty())
                ? (CONTENT_START_Y * baseScale + contentHeight)
                : (HEADER_HEIGHT * baseScale);

        displayWidth = HudRenderUtil.animateDimension(displayWidth, maxWidth);
        displayHeight = HudRenderUtil.animateDimension(displayHeight, targetHeight);
        width = displayWidth;
        height = displayHeight;

        if (!showWidget && widgetScale <= 0.001f && animatedEntries.isEmpty()) {
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
            panelRows.add(ScriptedListHudPanel.row(
                    "preview",
                    "head",
                    ScriptedListHudPanel.idString(resolvePreviewSkin()),
                    List.of(ScriptedListHudPanel.textPart("Example Staff", withAlpha(uiText & 0x00FFFFFF, 255), 0.0f)),
                    "Vanish",
                    withAlpha(resolveStatusColor(StaffTracker.Status.VANISH) & 0x00FFFFFF, 255),
                    withAlpha(uiMuted & 0x00FFFFFF, 130),
                    1.0f
            ));
        } else {
            for (AnimatedEntry animatedEntry : animatedEntries) {
                float anim = animatedEntry.anim();
                if (anim <= 0.01f) continue;
                StaffData.StaffEntry entry = animatedEntry.entry();
                int rowAlpha = clamp255(Math.round(255.0f * anim));
                int nameBaseColor = withAlpha(uiText & 0x00FFFFFF, rowAlpha);
                List<LinkedHashMap<String, Object>> parts = legacyParts(rowTextRenderer, entry.displayName(), anim, nameBaseColor);
                panelRows.add(ScriptedListHudPanel.row(
                        entry.name(),
                        "head",
                        ScriptedListHudPanel.idString(resolveSkinByName(entry.name())),
                        parts,
                        formatStatus(entry, now),
                        withAlpha(resolveStatusColor(entry.status()) & 0x00FFFFFF, rowAlpha),
                        withAlpha(uiMuted & 0x00FFFFFF, Math.max(26, rowAlpha - 120)),
                        anim
                ));
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
                        ScriptedListHudPanel.ADMINS,
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
                        entries.size(),
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

    private void renderExampleRow(Renderer2D renderer,
                                  TextRenderer rowTextRenderer,
                                  float fontScale,
                                  float rowTextH,
                                  float baseScale,
                                  float renderWidth,
                                  float centerY,
                                  GuiGraphicsExtractor ctx,
                                  float renderX) {
        int textColor = withAlpha(uiText & 0x00FFFFFF, 255);
        int statusColor = withAlpha(resolveStatusColor(StaffTracker.Status.VANISH) & 0x00FFFFFF, 255);
        int dividerColor = withAlpha(uiMuted & 0x00FFFFFF, 130);
        float rowContentCenterY = centerY + (ROW_CONTENT_CENTER_OFFSET * baseScale);

        renderHead(
                ctx,
                resolvePreviewSkin(),
                renderX + (ROW_HEAD_X * baseScale),
                rowContentCenterY,
                ROW_HEAD_SIZE * baseScale,
                255,
                baseScale
        );

        renderer.quad(
                renderX + (ROW_DIVIDER_X * baseScale),
                rowContentCenterY - ((ROW_DIVIDER_H * baseScale) * 0.5f),
                Math.max(0.5f, 0.5f * baseScale),
                ROW_DIVIDER_H * baseScale,
                dividerColor
        );

        rowTextRenderer.begin(fontScale, false, false);
        float textY = rowContentCenterY - (rowTextH * 0.5f);
        rowTextRenderer.render("Example Staff",
                renderX + (ROW_TEXT_X * baseScale), textY, new RenderColor(textColor), false);
        float statusWidth = (float) rowTextRenderer.getWidth("Vanish", false);
        rowTextRenderer.render("Vanish",
                renderX + renderWidth - statusWidth - (ROW_STATUS_RIGHT * baseScale),
                textY,
                new RenderColor(statusColor),
                false
        );
        rowTextRenderer.end();
    }

    private void renderHead(GuiGraphicsExtractor ctx,
                            Identifier skin,
                            float headX,
                            float centerY,
                            float size,
                            int alpha,
                            float baseScale) {
        float headY = centerY - (size * 0.5f);
        float radius = Math.min(1.15f * baseScale, size * 0.24f);
        if (skin != null && ctx != null) {
            RenderColor color = new RenderColor(0xFFFFFFFF);
            color.setAlpha(alpha);
            PlayerHeadRenderer.drawRounded(
                    ctx,
                    headX,
                    headY,
                    size,
                    radius,
                    skin,
                    color,
                    true,
                    null,
                    0.0f,
                    false
            );
            return;
        }

        int fill = withAlpha(uiText & 0x00FFFFFF, Math.max(28, alpha / 5));
        int strokeColor = withAlpha(uiMuted & 0x00FFFFFF, Math.max(80, alpha));
        Renderer2D.COLOR.roundedRect(headX, headY, size, size, radius, 1.0f, fill);
        Renderer2D.COLOR.roundedRectStroke(
                headX, headY, size, size, radius, 1.0f,
                Math.max(0.45f, 0.55f * baseScale),
                strokeColor
        );
    }

    private Identifier resolvePreviewSkin() {
        if (mc != null && mc.player != null) {
            return PlayerSkinResolver.resolveProfileSkin(mc.player.getGameProfile());
        }
        return null;
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
        uiDivider = HudRenderUtil.scaleAlpha(uiMuted, 0.55f);
        uiBlurTint = HudRenderUtil.mixColor(uiHeaderLeft, uiBodyRight, 0.5f);
    }

    private List<AnimatedEntry> animateEntries(List<StaffData.StaffEntry> entries) {
        Map<String, StaffData.StaffEntry> current = new LinkedHashMap<>();
        for (StaffData.StaffEntry entry : entries) {
            current.put(entry.name(), entry);
        }

        Map<String, StaffData.StaffEntry> merged = new LinkedHashMap<>();
        merged.putAll(current);
        for (Map.Entry<String, StaffData.StaffEntry> entry : lastEntries.entrySet()) {
            merged.putIfAbsent(entry.getKey(), entry.getValue());
        }
        lastEntries.clear();
        lastEntries.putAll(merged);

        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, StaffData.StaffEntry> entry : lastEntries.entrySet()) {
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

        List<AnimatedEntry> out = new ArrayList<>();
        for (Map.Entry<String, StaffData.StaffEntry> entry : lastEntries.entrySet()) {
            float anim = entryAnim.getOrDefault(entry.getKey(), 0.0f);
            if (anim > 0.01f) {
                out.add(new AnimatedEntry(entry.getValue(), anim));
            }
        }
        return out;
    }

    private float totalAnimatedHeight(List<AnimatedEntry> rows, float rowStep) {
        float out = 0.0f;
        for (AnimatedEntry row : rows) {
            out += rowStep * row.anim();
        }
        return out;
    }

    private float measureLegacyName(TextRenderer renderer, Component displayName) {
        Component converted = LegacyTextUtil.convertLegacyCodes(displayName);
        List<Part> parts = TextRenderUtil.flattenStyled(converted, theme().textPrimary());
        float out = 0.0f;
        for (Part part : parts) {
            out += (float) renderer.getWidth(part.text(), false);
        }
        return out;
    }

    private void drawLegacyName(TextRenderer renderer,
                                float fontScale,
                                float x,
                                float y,
                                Component displayName,
                                float alpha,
                                int fallbackColor) {
        Component converted = LegacyTextUtil.convertLegacyCodes(displayName);
        List<Part> parts = TextRenderUtil.flattenStyled(converted, fallbackColor);

        renderer.begin(fontScale, false, false);
        float cursorX = x;
        for (Part part : parts) {
            renderer.render(
                    part.text(),
                    cursorX,
                    y,
                    new RenderColor(HudRenderUtil.scaleAlpha(part.color(), alpha)),
                    false
            );
            cursorX += (float) renderer.getWidth(part.text(), false);
        }
        renderer.end();
    }

    private List<LinkedHashMap<String, Object>> legacyParts(TextRenderer renderer,
                                                                       Component displayName,
                                                                       float alpha,
                                                                       int fallbackColor) {
        Component converted = LegacyTextUtil.convertLegacyCodes(displayName);
        List<Part> parts = TextRenderUtil.flattenStyled(converted, fallbackColor);
        List<LinkedHashMap<String, Object>> out = new ArrayList<>(parts.size());
        float cursorX = 0.0f;
        for (Part part : parts) {
            int color = HudRenderUtil.scaleAlpha(part.color(), alpha);
            out.add(ScriptedListHudPanel.textPart(part.text(), color, cursorX));
            cursorX += (float) renderer.getWidth(part.text(), false);
        }
        return out;
    }

    private AbstractClientPlayer resolvePlayerByName(String name) {
        if (name == null || name.isEmpty() || mc == null || mc.level == null) {
            return null;
        }
        for (Player player : mc.level.players()) {
            if (player instanceof AbstractClientPlayer clientPlayer) {
                String playerName = clientPlayer.getGameProfile() != null
                        ? clientPlayer.getGameProfile().name()
                        : clientPlayer.getName().getString();
                if (playerName != null && playerName.equalsIgnoreCase(name)) {
                    return clientPlayer;
                }
            }
        }
        return null;
    }

    private Identifier resolveSkinByName(String name) {
        AbstractClientPlayer player = resolvePlayerByName(name);
        if (player != null) {
            return PlayerHeadRenderer.resolveCachedSkin(player);
        }

        if (mc != null && mc.getConnection() != null) {
            for (PlayerInfo entry : mc.getConnection().getOnlinePlayers()) {
                var profile = entry.getProfile();
                if (profile == null || profile.name() == null) continue;
                if (!profile.name().equalsIgnoreCase(name)) continue;
                Identifier skin = PlayerSkinResolver.resolveProfileSkin(profile);
                return PlayerHeadRenderer.resolveCachedSkin(profile.id(), profile.name(), skin);
            }
        }
        return PlayerHeadRenderer.getCachedSkin(name);
    }

    private int resolveStatusColor(StaffTracker.Status status) {
        return switch (status) {
            case GM0 -> 0xFF55FF55;
            case GM1 -> 0xFF55DDFF;
            case GM2 -> 0xFFFFFF55;
            case GM3 -> 0xFFFF55FF;
            case VANISH -> 0xFFFF7755;
            case WAITING -> 0xFFAAAAAA;
            case OFFLINE -> 0xFF888888;
            default -> uiMuted != 0 ? uiMuted : 0xFF888888;
        };
    }

    private String formatStatus(StaffData.StaffEntry entry, long now) {
        return switch (entry.status()) {
            case OFFLINE -> entry.offlineSince() <= 0 ? "--:--" : formatTime(now - entry.offlineSince());
            case WAITING -> entry.lastGone() <= 0 ? "--:--" : formatTime(now - entry.lastGone());
            case VANISH -> "Vanish";
            default -> entry.status().name();
        };
    }

    private String formatTime(long ms) {
        if (ms < 0) {
            return "--:--";
        }
        long sec = ms / 1000L;
        long min = sec / 60L;
        long hrs = min / 60L;
        if (hrs == 0) {
            return String.format("%02d:%02d", min, sec % 60L);
        }
        return String.format("%02d:%02d:%02d", hrs, min % 60L, sec % 60L);
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

    private record AnimatedEntry(StaffData.StaffEntry entry, float anim) {
    }
}
