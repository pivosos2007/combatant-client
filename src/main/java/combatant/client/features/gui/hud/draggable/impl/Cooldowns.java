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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import combatant.client.config.SettingDef;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.HudTextEffects;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.PvpCooldowns;
import combatant.client.mixins.accessors.ItemCooldownEntryAccessor;
import combatant.client.mixins.accessors.ItemCooldownManagerAccessor;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.pvp.CooldownRegistry;
import combatant.client.util.pvp.ItemCooldownSnapshot;
import combatant.client.util.pvp.PvpState;
import combatant.client.util.pvp.client.CooldownsState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 30)
public final class Cooldowns extends DraggableHudElement {

    {
        defaultLayout(1160.4823f, 466.0f);
    }


    private static final float HEADER_HEIGHT = 15.5f;
    private static final float BODY_Y_OFFSET = 18.5f;
    private static final float CONTENT_START_Y = 25.0f;
    private static final float BODY_INSET_Y = 6.5f;
    private static final float ROW_STEP = 11.0f;
    private static final float MIN_WIDTH = 110.0f;
    private static final float PANEL_RADIUS = 4.0f;
    private static final float PANEL_SOFTNESS = 1.0f;
    private static final float PANEL_STROKE = 0.55f;
    private static final float HEADER_DIVIDER_X = 18.0f;
    private static final float HEADER_DIVIDER_Y = 5.0f;
    private static final float HEADER_DIVIDER_H = 6.0f;
    private static final float ROW_DIVIDER_X = 15.0f;
    private static final float ROW_DIVIDER_H = 6.0f;
    private static final float TITLE_ICON_X = 4.0f;
    private static final float TITLE_TEXT_X = 22.0f;
    private static final float COUNT_LABEL_OFFSET = 22.0f;
    private static final float COUNT_VALUE_OFFSET = 2.5f;
    private static final float ROW_ICON_X = 3.5f;
    private static final float ROW_ICON_VISUAL_SIZE = 8.0f;
    private static final float ROW_TEXT_X = 18.0f;
    private static final float ROW_TIME_RIGHT = 8.0f;
    private static final float ROW_CONTENT_CENTER_OFFSET = 1.9f;
    private static final int BLINK_THRESHOLD_TICKS = 100;
    private static final double BLINK_SPEED = 0.008;
    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";
    private static final int PVP_ACTIVE_COLOR = 0xFFFFA500;
    private static final int PVP_IDLE_COLOR = 0xFF00A0FF;
    private static final int PVP_BLOCKED_COLOR = 0xFFFF5656;
    private static final Item[] PREVIEW_ITEMS = new Item[]{
            Items.ENDER_EYE, Items.ENDER_PEARL, Items.SUGAR, Items.MACE,
            Items.ENCHANTED_GOLDEN_APPLE, Items.TRIDENT, Items.CROSSBOW,
            Items.DRIED_KELP, Items.NETHERITE_SCRAP
    };
    private static ItemStack[] previewStacks;

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final ScriptedListHudPanel scriptedPanel = new ScriptedListHudPanel();
    private final NumberValue<Double> scaleValue =
            new NumberValue<>("cooldowns_scale", 1.0, HudPanelLayoutModes.SCALE_MIN, HudPanelLayoutModes.SCALE_MAX);
    private final ModeValue layoutMode =
            new ModeValue("cooldowns_layout", "Split Header", HudPanelLayoutModes.SPLIT_HEADER, HudPanelLayoutModes.UNIFIED_DIVIDER);
    private final ModeValue colorMode =
            new ModeValue("cooldowns_color_mode", "Theme", COLOR_THEME, COLOR_CUSTOM);
    private final ModeValue panelStyle =
            new ModeValue("cooldowns_panel_style", HudRenderUtil.PANEL_STYLE_DEFAULT,
                    HudRenderUtil.PANEL_STYLE_DEFAULT, HudRenderUtil.PANEL_STYLE_ACCENT,
                    HudRenderUtil.PANEL_STYLE_GRADIENT);
    private final NumberValue<Integer> themeGradientStrength =
            new NumberValue<>("cooldowns_theme_gradient_strength", 72, 0, 100);
    private final ModeValue bgEffect =
            new ModeValue("cooldowns_bg_effect", "Blur", EFFECT_NONE, EFFECT_BLUR);
    private final BooleanValue blink =
            new BooleanValue("cooldowns_blink", true);
    private final RGBAColorValue bg =
            new RGBAColorValue("cooldowns_bg", "#EB111318");
    private final RGBAColorValue bg2 =
            new RGBAColorValue("cooldowns_bg_secondary", "#F7161616");
    private final NumberValue<Integer> bgAlpha =
            new NumberValue<>("cooldowns_bg_alpha", 185, 0, 255);
    private final BooleanValue strokeEnabled =
            new BooleanValue("cooldowns_stroke_enabled", false);
    private final NumberValue<Integer> strokeAlpha =
            new NumberValue<>("cooldowns_stroke_alpha", 160, 0, 255);
    private final BooleanValue strokeGradient =
            new BooleanValue("cooldowns_stroke_gradient", true);
    private final BooleanValue shadowEnabled =
            new BooleanValue("cooldowns_shadow_enabled", true);
    private final ModeValue shadowMode =
            new ModeValue("cooldowns_shadow_mode", HudRenderUtil.SHADOW_MODE_BLACK,
                    HudRenderUtil.SHADOW_MODE_BLACK, HudRenderUtil.SHADOW_MODE_THEME);
    private final NumberValue<Integer> themeShadowStrength =
            new NumberValue<>("cooldowns_theme_shadow_strength", 100, 0, 100);
    private final NumberValue<Integer> shadowAlpha =
            new NumberValue<>("cooldowns_shadow_alpha", 38, 0, 255);
    private final RGBColorValue stroke =
            new RGBColorValue("cooldowns_stroke", "#5A5A5A");
    private final RGBColorValue text =
            new RGBColorValue("cooldowns_text", "#FFFFFF");
    private final RGBColorValue muted =
            new RGBColorValue("cooldowns_muted", "#A5A5A5");
    private final NumberValue<Integer> blurAlpha =
            new NumberValue<>("cooldowns_blur_alpha", 255, 0, 255);
    private final BooleanValue headerIconPulse =
            new BooleanValue("cooldowns_header_icon_pulse", true);
    private final NumberValue<Integer> headerIconPulseSpeed =
            new NumberValue<>("cooldowns_header_icon_pulse_speed", 18, 1, 60);
    private final NumberValue<Integer> headerIconPulseIntensity =
            new NumberValue<>("cooldowns_header_icon_pulse_intensity", 100, 0, 100);

    private final Map<String, Float> entryAnim = new LinkedHashMap<>();
    private final Map<String, Row> lastEntries = new LinkedHashMap<>();
    private final List<QueuedItemIcon> pendingItemTasks = new ArrayList<>(9);
    private float displayWidth = -1.0f;
    private float displayHeight = -1.0f;
    private float visibilityAnim = 0.0f;
    private boolean foregroundReady;
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

    public Cooldowns() {
        super("cooldowns", "Cooldowns", true);
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }


    private static ItemStack[] previewStacks() {
        if (previewStacks == null || previewStacks.length != PREVIEW_ITEMS.length) {
            previewStacks = buildPreviewStacks();
        }
        return previewStacks;
    }

    private static ItemStack[] buildPreviewStacks() {
        ItemStack[] stacks = new ItemStack[PREVIEW_ITEMS.length];
        for (int i = 0; i < PREVIEW_ITEMS.length; i++) {
            stacks[i] = new ItemStack(PREVIEW_ITEMS[i]);
        }
        return stacks;
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
        defs.add(SettingDef.bool(blink));
        defs.add(SettingDef.bool(headerIconPulse));
        defs.add(SettingDef.number(headerIconPulseSpeed).visibleWhen(headerIconPulse::get));
        defs.add(SettingDef.number(headerIconPulseIntensity).visibleWhen(headerIconPulse::get));
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        this.x = screenW - 132.0f;
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
        foregroundReady = false;
        pendingItemTasks.clear();
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
        float titleWidth = (float) headerTextRenderer.getWidth("Cooldowns", false);
        headerTextRenderer.end();

        rowTextRenderer.begin(fontScale, true, false);
        float rowTextH = (float) rowTextRenderer.getHeight(false);
        float maxWidth = MIN_WIDTH * baseScale;
        if (showExampleRow) {
            float previewWidth = (float) rowTextRenderer.getWidth("Example Cooldowns**:**", false) + (30.0f * baseScale);
            maxWidth = Math.max(maxWidth, previewWidth);
        } else {
            for (AnimatedRow animatedRow : animatedRows) {
                Row row = animatedRow.row();
                float widthCandidate = (float) rowTextRenderer.getWidth(row.name() + row.duration(), false) + (30.0f * baseScale);
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

        List<LinkedHashMap<String, Object>> panelRows = ScriptedListHudPanel.rows();
        rowTextRenderer.begin(drawFontScale, false, false);
        if (showExampleRow) {
            ItemStack stack = previewStacks()[(int) ((System.currentTimeMillis() / 1000L) % previewStacks().length)];
            panelRows.add(ScriptedListHudPanel.row(
                    "preview",
                    "item",
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    List.of(ScriptedListHudPanel.textPart("Example Cooldowns", withAlpha(uiText & 0x00FFFFFF, 255), 0.0f)),
                    "**:**",
                    withAlpha(uiCounter & 0x00FFFFFF, 255),
                    withAlpha(uiMuted & 0x00FFFFFF, 130),
                    1.0f
            ));
        } else {
            for (AnimatedRow animatedRow : animatedRows) {
                float anim = animatedRow.anim();
                if (anim <= 0.01f) continue;
                Row rowData = animatedRow.row();
                int rowBaseAlpha = clamp255(Math.round(255.0f * anim));
                int iconAlpha = getBlinkAlpha(rowData, rowBaseAlpha, 110);
                int textAlpha = getBlinkAlpha(rowData, rowBaseAlpha, 95);
                int timeAlpha = getBlinkAlpha(rowData, rowBaseAlpha, 60);
                panelRows.add(ScriptedListHudPanel.row(
                        rowData.key(),
                        "item",
                        BuiltInRegistries.ITEM.getKey(rowData.stack().getItem()).toString(),
                        List.of(ScriptedListHudPanel.textPart(rowData.name(), withAlpha(uiText & 0x00FFFFFF, textAlpha), 0.0f)),
                        rowData.duration(),
                        withAlpha(rowData.timeColor() & 0x00FFFFFF, timeAlpha),
                        withAlpha(uiMuted & 0x00FFFFFF, Math.max(26, iconAlpha - 120)),
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
                        ScriptedListHudPanel.COOLDOWNS,
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
        foregroundReady = false;
    }

    @Override
    public void renderEngineForeground(Renderer2D renderer,
                                       TextRenderer textRenderer,
                                       GuiGraphicsExtractor ctx,
                                       float tickDelta,
                                       int screenW,
                                       int screenH) {
        if (!foregroundReady || ctx == null || pendingItemTasks.isEmpty()) {
            pendingItemTasks.clear();
            return;
        }
        for (QueuedItemIcon task : pendingItemTasks) {
            // Cooldowns icons live in the same logical HUD space as the panel geometry/text.
            renderer.item(task.stack(), task.x(), task.y(), task.scale(), task.seed(), Renderer2D.ITEM_OVERLAY_NONE, null);
        }
        pendingItemTasks.clear();
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
                                  float renderX) {
        ItemStack stack = previewStacks()[(int) ((System.currentTimeMillis() / 1000L) % previewStacks().length)];
        int textColor = withAlpha(uiText & 0x00FFFFFF, 255);
        int timeColor = withAlpha(uiCounter & 0x00FFFFFF, 255);
        int dividerColor = withAlpha(uiMuted & 0x00FFFFFF, 130);
        float rowContentCenterY = centerY + (ROW_CONTENT_CENTER_OFFSET * baseScale);

        queueItemIcon(pendingItemTasks, stack, renderX + (ROW_ICON_X * baseScale), rowContentCenterY, baseScale, 0);

        renderer.quad(
                renderX + (ROW_DIVIDER_X * baseScale),
                rowContentCenterY - ((ROW_DIVIDER_H * baseScale) * 0.5f),
                Math.max(0.5f, 0.5f * baseScale),
                ROW_DIVIDER_H * baseScale,
                dividerColor
        );

        rowTextRenderer.begin(fontScale, false, false);
        float textY = rowContentCenterY - (rowTextH * 0.5f);
        rowTextRenderer.render("Example Cooldowns",
                renderX + (ROW_TEXT_X * baseScale), textY, new RenderColor(textColor), false);
        float durationWidth = (float) rowTextRenderer.getWidth("**:**", false);
        rowTextRenderer.render("**:**",
                renderX + renderWidth - durationWidth - (ROW_TIME_RIGHT * baseScale),
                textY,
                new RenderColor(timeColor),
                false
        );
        rowTextRenderer.end();
    }

    private void queueItemIcon(List<QueuedItemIcon> itemTasks,
                               ItemStack stack,
                               float iconX,
                               float centerY,
                               float baseScale,
                               int seed) {
        if (itemTasks == null) {
            return;
        }
        if (stack.isEmpty()) {
            return;
        }
        float iconSize = ROW_ICON_VISUAL_SIZE * baseScale;
        float itemScale = iconSize / 16.0f;
        float itemDrawX = iconX;
        float itemDrawY = centerY - (16.0f * itemScale * 0.5f);
        itemTasks.add(new QueuedItemIcon(stack.copy(), itemDrawX, itemDrawY, itemScale, seed));
    }

    private List<Row> collectRows() {
        List<Row> rows = new ArrayList<>();
        if (mc != null && mc.player != null) {
            var manager = mc.player.getCooldowns();
            int nowTick = ((ItemCooldownManagerAccessor) manager).getTick();

            ((ItemCooldownManagerAccessor) manager).getEntries().forEach((Identifier id, Object entry) -> {
                int remainingTicks = ((ItemCooldownEntryAccessor) entry).getEndTick() - nowTick;
                if (remainingTicks <= 0) return;
                Item item = id != null ? BuiltInRegistries.ITEM.getValue(id) : Items.AIR;
                ItemStack stack = new ItemStack(item);
                String name = stack.isEmpty() ? (id != null ? id.getPath() : "Unknown") : stack.getHoverName().getString();
                String key = "vanilla:" + (id != null ? id.toString() : name);
                rows.add(new Row(key, stack, name, formatSeconds(remainingTicks / 20.0f), remainingTicks,
                        uiMuted != 0 ? uiMuted : theme().textMuted()));
            });

            PvpCooldowns pvpCooldowns = Modules.get(PvpCooldowns.class);
            boolean pvpEnabled = pvpCooldowns != null && pvpCooldowns.isSystemEnabled();
            if (pvpEnabled && pvpCooldowns.shouldRenderWidget()) {
                boolean inPvp = CooldownsState.MANAGER.isInPvp();
                int stateColor = inPvp ? PVP_ACTIVE_COLOR : PVP_IDLE_COLOR;
                for (Item item : CooldownRegistry.trackedItems()) {
                    ItemCooldownSnapshot snapshot = CooldownsState.MANAGER.snapshot(item);
                    if (!snapshot.visible()) continue;

                    float seconds = snapshot.cooling()
                            ? snapshot.cooldownRemainingMs() / 1000.0f
                            : snapshot.useWindowRemainingMs() / 1000.0f;
                    String text = snapshot.cooling()
                            ? formatSeconds(seconds)
                            : snapshot.compactText();
                    int remainingTicks = snapshot.cooling()
                            ? Math.max(1, Math.round(seconds * 20.0f))
                            : Math.max(1, Math.round(Math.max(1.0f, seconds) * 20.0f));
                    ItemStack stack = new ItemStack(item);
                    rows.add(new Row(
                            "pvp:" + BuiltInRegistries.ITEM.getKey(item),
                            stack,
                            stack.getHoverName().getString(),
                            text,
                            remainingTicks,
                            snapshot.cooling() ? stateColor : (uiMuted != 0 ? uiMuted : theme().textMuted())
                    ));
                }
            }

            if (pvpEnabled && PvpState.isActive()) {
                Float secondsLeft = PvpState.getSecondsLeft();
                if (secondsLeft != null && secondsLeft > 0.0f) {
                    int remainingTicks = Math.max(1, Math.round(secondsLeft * 20.0f));
                    ItemStack pearlStack = new ItemStack(Items.ENDER_PEARL);
                    rows.add(new Row(
                            "pvpban:" + BuiltInRegistries.ITEM.getKey(Items.ENDER_PEARL),
                            pearlStack,
                            pearlStack.getHoverName().getString(),
                            formatSeconds(secondsLeft),
                            remainingTicks,
                            PVP_BLOCKED_COLOR
                    ));
                    ItemStack chorusStack = new ItemStack(Items.CHORUS_FRUIT);
                    rows.add(new Row(
                            "pvpban:" + BuiltInRegistries.ITEM.getKey(Items.CHORUS_FRUIT),
                            chorusStack,
                            chorusStack.getHoverName().getString(),
                            formatSeconds(secondsLeft),
                            remainingTicks,
                            PVP_BLOCKED_COLOR
                    ));
                }
            }
        }

        rows.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name()));
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

    private int getBlinkAlpha(Row row, int baseAlpha, int minAlphaFloor) {
        if (!blink.get()) {
            return baseAlpha;
        }
        if (row.remainingTicks() <= 0 || row.remainingTicks() > BLINK_THRESHOLD_TICKS) {
            return baseAlpha;
        }

        double wave = Math.sin(System.currentTimeMillis() * BLINK_SPEED);
        float factor = (float) ((wave + 1.0) * 0.5);
        int minAlpha = Math.max(0, Math.min(baseAlpha, minAlphaFloor));
        return clamp255(Math.round(minAlpha + (baseAlpha - minAlpha) * (1.0f - factor)));
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

    private String formatSeconds(float seconds) {
        if (seconds >= 10.0f) {
            return String.format("%.0fs", seconds);
        }
        return String.format("%.1fs", Math.round(seconds * 10.0f) / 10.0f);
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

    private record Row(String key, ItemStack stack, String name, String duration, int remainingTicks, int timeColor) {
    }

    private record AnimatedRow(Row row, float anim) {
    }

    private record QueuedItemIcon(ItemStack stack, float x, float y, float scale, int seed) {
    }
}
