/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.SettingDef;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.config.values.RGBColorValue;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.HudTextEffects;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.BlockESP;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.session.SessionStatisticsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 210)
public final class Statistics extends DraggableHudElement {
    private static final float BASE_WIDTH = 145.0f;
    private static final float STATS_HEIGHT = 64.0f;
    private static final float GRAPH_HEIGHT = 54.0f;
    private static final float GRAPH_GAP = 5.0f;
    private static final float PANEL_RADIUS = 4.0f;
    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";
    private static final String INFO_GAMES = "games";
    private static final String INFO_KD = "kd";
    private static final String INFO_KILLS = "kills";
    private static final String INFO_DEATHS = "deaths";
    private static final String INFO_PLAY_TIME = "play_time";
    private static final String INFO_AVERAGE_SPEED = "average_speed";
    private static final String INFO_BEDWARS_IRON = "bedwars_iron";
    private static final String INFO_BEDWARS_GOLD = "bedwars_gold";
    private static final String INFO_BEDWARS_DIAMONDS = "bedwars_diamonds";
    private static final String INFO_BEDWARS_EMERALDS = "bedwars_emeralds";
    private static final String INFO_BLOCKESP_TOTAL = "blockesp_total";
    private static final String INFO_BLOCKESP_VISIBLE = "blockesp_visible";
    private static final String INFO_BLOCKESP_BREAKDOWN = "blockesp_breakdown";
    private static final int MAX_BLOCKESP_ROWS = 6;

    {
        defaultLayout(20.0f, 150.0f);
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final ScriptedStatisticsHudPanel scriptedPanel = new ScriptedStatisticsHudPanel();
    private final SessionStatisticsTracker statistics = SessionStatisticsTracker.INSTANCE;

    private final NumberValue<Double> scaleValue =
            new NumberValue<>("statistics_scale", 1.0, HudPanelLayoutModes.SCALE_MIN, HudPanelLayoutModes.SCALE_MAX);
    private final ModeValue layoutMode =
            new ModeValue("statistics_layout", "Unified Divider",
                    HudPanelLayoutModes.SPLIT_HEADER, HudPanelLayoutModes.UNIFIED_DIVIDER);
    private final BooleanMapValue information =
            new BooleanMapValue("statistics_information", defaultInformationModes());
    private final BooleanValue showSpeedGraph =
            new BooleanValue("statistics_show_speed_graph", true);
    private final BooleanValue separateGraph =
            new BooleanValue("statistics_separate_graph", true);
    private final ModeValue colorMode =
            new ModeValue("statistics_color_mode", "Theme", COLOR_THEME, COLOR_CUSTOM);
    private final ModeValue panelStyle =
            new ModeValue("statistics_panel_style", HudRenderUtil.PANEL_STYLE_DEFAULT,
                    HudRenderUtil.PANEL_STYLE_DEFAULT, HudRenderUtil.PANEL_STYLE_ACCENT);
    private final ModeValue bgEffect =
            new ModeValue("statistics_bg_effect", "Blur", EFFECT_NONE, EFFECT_BLUR);
    private final RGBAColorValue bg =
            new RGBAColorValue("statistics_bg", "#F7343434");
    private final RGBAColorValue bg2 =
            new RGBAColorValue("statistics_bg_secondary", "#F7161616");
    private final NumberValue<Integer> bgAlpha =
            new NumberValue<>("statistics_bg_alpha", 194, 0, 255);
    private final BooleanValue strokeEnabled =
            new BooleanValue("statistics_stroke_enabled", false);
    private final RGBColorValue stroke =
            new RGBColorValue("statistics_stroke", "#5A5A5A");
    private final NumberValue<Integer> strokeAlpha =
            new NumberValue<>("statistics_stroke_alpha", 160, 0, 255);
    private final BooleanValue strokeGradient =
            new BooleanValue("statistics_stroke_gradient", true);
    private final BooleanValue shadowEnabled =
            new BooleanValue("statistics_shadow_enabled", true);
    private final ModeValue shadowMode =
            new ModeValue("statistics_shadow_mode", HudRenderUtil.SHADOW_MODE_BLACK,
                    HudRenderUtil.SHADOW_MODE_BLACK, HudRenderUtil.SHADOW_MODE_THEME);
    private final NumberValue<Integer> shadowAlpha =
            new NumberValue<>("statistics_shadow_alpha", 38, 0, 255);
    private final RGBColorValue text =
            new RGBColorValue("statistics_text", "#FFFFFF");
    private final RGBColorValue muted =
            new RGBColorValue("statistics_muted", "#A5A5A5");
    private final NumberValue<Integer> blurAlpha =
            new NumberValue<>("statistics_blur_alpha", 255, 0, 255);
    private final BooleanValue headerIconPulse =
            new BooleanValue("statistics_header_icon_pulse", true);
    private final NumberValue<Integer> headerIconPulseSpeed =
            new NumberValue<>("statistics_header_icon_pulse_speed", 16, 1, 60);
    private final NumberValue<Integer> headerIconPulseIntensity =
            new NumberValue<>("statistics_header_icon_pulse_intensity", 100, 0, 100);

    private float displayWidth = -1.0f;
    private float displayHeight = -1.0f;
    private float visibilityAnim;
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

    public Statistics() {
        super("statistics", "Statistics", true);
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.number(scaleValue));
        defs.add(SettingDef.mode(layoutMode));
        defs.add(SettingDef.group("statistics_information", information));
        defs.add(SettingDef.bool(showSpeedGraph));
        defs.add(SettingDef.bool(separateGraph).visibleWhen(showSpeedGraph::get));
        defs.add(SettingDef.mode(colorMode));
        defs.add(SettingDef.mode(panelStyle).visibleWhen(this::isThemeMode));
        defs.add(SettingDef.color(bg).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.color(bg2).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.number(bgAlpha).visibleWhen(this::isThemeMode));
        defs.add(SettingDef.bool(strokeEnabled));
        defs.add(SettingDef.colorNoAlpha(stroke).visibleWhen(() -> strokeEnabled.get() && isCustomMode()));
        defs.add(SettingDef.number(strokeAlpha).visibleWhen(strokeEnabled::get));
        defs.add(SettingDef.bool(strokeGradient).visibleWhen(() -> strokeEnabled.get() && isThemeMode()));
        defs.add(SettingDef.bool(shadowEnabled));
        defs.add(SettingDef.mode(shadowMode).visibleWhen(shadowEnabled::get));
        defs.add(SettingDef.number(shadowAlpha).visibleWhen(shadowEnabled::get));
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
        x = 20.0f;
        y = Math.min(150.0f, Math.max(8.0f, screenH - 140.0f));
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
        boolean showWidget = forceVisible || (mc != null && mc.player != null);
        visibilityAnim = HudRenderUtil.animateVisibility(visibilityAnim, showWidget);
        float widgetScale = HudRenderUtil.visibilityScale(visibilityAnim);
        if (!showWidget && visibilityAnim <= 0.0f) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        updatePalette();
        SessionStatisticsTracker.Snapshot snapshot = statistics.snapshot();
        boolean exampleData = forceVisible && (mc == null || mc.player == null);
        List<LinkedHashMap<String, Object>> infoRows = collectInformationRows(snapshot, exampleData);
        boolean showPlayTime = information.get(INFO_PLAY_TIME);

        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        TextRenderer headerTextRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, fallback);
        TextRenderer rowTextRenderer = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, fallback);
        if (headerTextRenderer == null) headerTextRenderer = fallback;
        if (rowTextRenderer == null) rowTextRenderer = fallback;

        float baseScale = HudScale.scale(screenW, screenH) * 1.1f * HudPanelLayoutModes.effectiveScale(scaleValue);
        float fontScale = 0.98f * (hud.getFontSize() / 18.0f);

        headerTextRenderer.begin(fontScale, false, false);
        float headerTextH = (float) headerTextRenderer.getHeight(false);
        headerTextRenderer.end();
        rowTextRenderer.begin(fontScale, false, false);
        float rowTextH = (float) rowTextRenderer.getHeight(false);
        float maxRowPairWidth = 0.0f;
        for (Map<String, Object> row : infoRows) {
            String label = String.valueOf(row.getOrDefault("label", ""));
            String value = String.valueOf(row.getOrDefault("value", ""));
            maxRowPairWidth = Math.max(maxRowPairWidth,
                    (float) rowTextRenderer.getWidth(label, false)
                            + (float) rowTextRenderer.getWidth(value, false));
        }
        rowTextRenderer.end();

        boolean graphVisible = showSpeedGraph.get();
        boolean graphSeparated = graphVisible && separateGraph.get();
        float statsHeightUnits = Math.max(
                showPlayTime ? STATS_HEIGHT : 28.0f,
                28.0f + infoRows.size() * 12.0f
        );
        float targetHeightUnits = statsHeightUnits;
        if (graphVisible) {
            targetHeightUnits += GRAPH_HEIGHT + (graphSeparated ? GRAPH_GAP : 0.0f);
        }
        float targetWidth = Math.max(
                BASE_WIDTH * baseScale,
                maxRowPairWidth + (showPlayTime ? 84.0f : 24.0f) * baseScale
        );
        float targetHeight = targetHeightUnits * baseScale;
        displayWidth = HudRenderUtil.animateDimension(displayWidth, targetWidth);
        displayHeight = HudRenderUtil.animateDimension(displayHeight, targetHeight);
        width = displayWidth;
        height = displayHeight;

        float drawScale = Math.max(0.0f, widgetScale);
        float drawWidth = displayWidth * drawScale;
        float drawHeight = displayHeight * drawScale;
        if (drawWidth <= 0.0f || drawHeight <= 0.0f) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        float drawX = x + (displayWidth - drawWidth) * 0.5f;
        float drawY = y + (displayHeight - drawHeight) * 0.5f;
        float drawBaseScale = baseScale * drawScale;
        float drawFontScale = fontScale * drawScale;
        float statsHeight = statsHeightUnits * drawBaseScale;
        float drawMainHeight;
        float drawGraphHeight;
        float drawGraphGap;
        if (!graphVisible) {
            drawMainHeight = drawHeight;
            drawGraphHeight = 0.0f;
            drawGraphGap = 0.0f;
        } else if (graphSeparated) {
            drawMainHeight = Math.min(drawHeight, statsHeight);
            float remainder = Math.max(0.0f, drawHeight - drawMainHeight);
            drawGraphGap = Math.min(GRAPH_GAP * drawBaseScale, remainder);
            drawGraphHeight = Math.max(0.0f, remainder - drawGraphGap);
        } else {
            drawMainHeight = drawHeight;
            drawGraphGap = 0.0f;
            drawGraphHeight = Math.max(0.0f, drawHeight - statsHeight);
        }

        int resolvedHeaderIconColor = uiCounter;
        if (headerIconPulse.get()) {
            int pulsed = HudTextEffects.animatedColor(
                    uiCounter,
                    HudTextEffects.Effect.PULSE,
                    headerIconPulseSpeed.get(),
                    (float) (Util.getMillis() / 1000.0),
                    0.0f
            );
            resolvedHeaderIconColor = HudRenderUtil.mixColor(
                    uiCounter,
                    pulsed,
                    headerIconPulseIntensity.get() / 100.0f
            );
        }

        if (shadowEnabled.get()) {
            boolean themeShadow = HudRenderUtil.SHADOW_MODE_THEME.equals(shadowMode.get());
            HudRenderUtil.drawHudShadow(
                    renderer, drawX, drawY, drawWidth, drawMainHeight,
                    PANEL_RADIUS * drawBaseScale, drawBaseScale,
                    themeShadow, shadowAlpha.get(), drawScale
            );
            if (graphSeparated && drawGraphHeight > 0.0f) {
                HudRenderUtil.drawHudShadow(
                        renderer,
                        drawX,
                        drawY + drawMainHeight + drawGraphGap,
                        drawWidth,
                        drawGraphHeight,
                        PANEL_RADIUS * drawBaseScale,
                        drawBaseScale,
                        themeShadow,
                        shadowAlpha.get(),
                        drawScale
                );
            }
        }

        long elapsedMs = snapshot.elapsedMs();
        String playTime = formatPlayTime(elapsedMs);
        float hourProgress = (elapsedMs % 3_600_000L) / 3_600_000.0f;
        float arcEndAngle = -90.0f + 360.0f * hourProgress;
        List<LinkedHashMap<String, Object>> graphPoints = buildGraphPoints(
                drawWidth,
                drawGraphHeight,
                drawBaseScale,
                snapshot.speedSamples(),
                forceVisible && snapshot.speedSamples().size() < 2
        );
        HudRenderUtil.ThemeGradient accent = HudRenderUtil.themeAccentGradient(255);
        int accentStart = isThemeMode()
                ? accent.start()
                : HudRenderUtil.mixColor(text.getArgb(), muted.getArgb(), 0.12f);
        int accentEnd = isThemeMode()
                ? accent.end()
                : HudRenderUtil.mixColor(text.getArgb(), muted.getArgb(), 0.45f);

        scriptedPanel.render(
                renderer,
                fallback,
                ctx,
                tickDelta,
                new ScriptedStatisticsHudPanel.Panel(
                        drawX,
                        drawY,
                        drawWidth,
                        drawHeight,
                        drawMainHeight,
                        drawGraphHeight,
                        drawGraphGap,
                        drawScale,
                        drawBaseScale,
                        drawFontScale,
                        headerTextH * drawScale,
                        rowTextH * drawScale,
                        playTime,
                        String.format(Locale.ROOT, "%.2f BPS", snapshot.averageBps()),
                        arcEndAngle,
                        showPlayTime,
                        graphVisible,
                        graphSeparated,
                        hasEffect(),
                        Math.min(1.0f, (blurAlpha.get() / 255.0f) * (isThemeMode() ? 1.15f : 1.0f)),
                        HudPanelLayoutModes.current(layoutMode),
                        strokeEnabled.get(),
                        strokeAlpha.get() / 255.0f,
                        isThemeMode() && strokeGradient.get(),
                        resolveStrokeGradientStart(),
                        resolveStrokeGradientEnd(),
                        accentStart,
                        accentEnd,
                        resolvedHeaderIconColor,
                        true,
                        new ScriptedListHudPanel.Palette(
                                uiHeaderLeft, uiHeaderRight, uiBodyLeft, uiBodyRight,
                                uiOutline, uiText, uiMuted, uiCounter, uiTitleText, uiDivider, uiBlurTint
                        ),
                        infoRows,
                        graphPoints
                )
        );
    }

    private static Map<String, Boolean> defaultInformationModes() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(INFO_GAMES, true);
        defaults.put(INFO_KD, true);
        defaults.put(INFO_KILLS, true);
        defaults.put(INFO_DEATHS, false);
        defaults.put(INFO_PLAY_TIME, true);
        defaults.put(INFO_AVERAGE_SPEED, false);
        defaults.put(INFO_BEDWARS_IRON, false);
        defaults.put(INFO_BEDWARS_GOLD, false);
        defaults.put(INFO_BEDWARS_DIAMONDS, false);
        defaults.put(INFO_BEDWARS_EMERALDS, false);
        defaults.put(INFO_BLOCKESP_TOTAL, true);
        defaults.put(INFO_BLOCKESP_VISIBLE, false);
        defaults.put(INFO_BLOCKESP_BREAKDOWN, false);
        return defaults;
    }

    private List<LinkedHashMap<String, Object>> collectInformationRows(SessionStatisticsTracker.Snapshot snapshot,
                                                                        boolean exampleData) {
        List<LinkedHashMap<String, Object>> rows = new ArrayList<>();
        if (information.get(INFO_GAMES)) {
            rows.add(infoRow(INFO_GAMES, "Games", Integer.toString(snapshot.gamesPlayed()), uiCounter));
        }
        if (information.get(INFO_KD)) {
            String kd = snapshot.deaths() == 0
                    ? Integer.toString(snapshot.kills())
                    : String.format(Locale.ROOT, "%.2f", snapshot.kd());
            rows.add(infoRow(INFO_KD, "K/D", kd, uiText));
        }
        if (information.get(INFO_KILLS)) {
            rows.add(infoRow(INFO_KILLS, "Kills", Integer.toString(snapshot.kills()), uiText));
        }
        if (information.get(INFO_DEATHS)) {
            rows.add(infoRow(INFO_DEATHS, "Deaths", Integer.toString(snapshot.deaths()), uiText));
        }
        if (information.get(INFO_AVERAGE_SPEED)) {
            rows.add(infoRow(INFO_AVERAGE_SPEED, "Average Speed",
                    String.format(Locale.ROOT, "%.2f BPS", snapshot.averageBps()), uiCounter));
        }

        addResourceRow(rows, INFO_BEDWARS_IRON, "Iron", Items.IRON_INGOT, 0xFFD8D8D8, exampleData ? 32 : -1);
        addResourceRow(rows, INFO_BEDWARS_GOLD, "Gold", Items.GOLD_INGOT, 0xFFFFD45A, exampleData ? 14 : -1);
        addResourceRow(rows, INFO_BEDWARS_DIAMONDS, "Diamonds", Items.DIAMOND, 0xFF55E3F0, exampleData ? 4 : -1);
        addResourceRow(rows, INFO_BEDWARS_EMERALDS, "Emeralds", Items.EMERALD, 0xFF57D987, exampleData ? 2 : -1);

        BlockESP blockEsp = Modules.get(BlockESP.class);
        boolean blockEspActive = blockEsp != null && blockEsp.isEnabled();
        if (!blockEspActive && !exampleData) return rows;

        BlockESP.DetectionSnapshot detections = blockEspActive
                ? blockEsp.detectionSnapshot()
                : new BlockESP.DetectionSnapshot(22, 16, 6, List.of(
                new BlockESP.BlockDetection("minecraft:diamond_ore", "Diamond Ore", 14, 9, 5),
                new BlockESP.BlockDetection("minecraft:ancient_debris", "Ancient Debris", 8, 7, 1)
        ));
        if (information.get(INFO_BLOCKESP_TOTAL)) {
            rows.add(infoRow(INFO_BLOCKESP_TOTAL, "Detected Blocks", Integer.toString(detections.total()), uiCounter));
        }
        if (information.get(INFO_BLOCKESP_VISIBLE)) {
            rows.add(infoRow(INFO_BLOCKESP_VISIBLE, "Visible Blocks",
                    detections.visible() + " / " + detections.total(), uiText));
        }
        if (information.get(INFO_BLOCKESP_BREAKDOWN)) {
            int shown = 0;
            int remainder = 0;
            for (BlockESP.BlockDetection detection : detections.blocks()) {
                if (shown < MAX_BLOCKESP_ROWS) {
                    rows.add(infoRow(
                            "blockesp:" + detection.id(),
                            compactLabel(detection.name()),
                            Integer.toString(detection.total()),
                            uiCounter
                    ));
                    shown++;
                } else {
                    remainder += detection.total();
                }
            }
            if (remainder > 0) {
                rows.add(infoRow("blockesp:other", "Other Blocks", Integer.toString(remainder), uiMuted));
            }
        }
        return rows;
    }

    private void addResourceRow(List<LinkedHashMap<String, Object>> rows,
                                String option,
                                String label,
                                Item item,
                                int color,
                                int exampleCount) {
        if (!information.get(option)) return;
        int count = exampleCount >= 0 ? exampleCount : countInventoryItem(item);
        rows.add(infoRow(option, label, Integer.toString(count), color));
    }

    private int countInventoryItem(Item item) {
        if (mc == null || mc.player == null || item == null) return 0;
        int count = 0;
        for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack != null && !stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static LinkedHashMap<String, Object> infoRow(String key, String label, String value, int valueColor) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("key", key != null ? key : "row");
        row.put("label", label != null ? label : "");
        row.put("value", value != null ? value : "");
        row.put("valueColor", String.format("#%08X", valueColor));
        return row;
    }

    private static String compactLabel(String label) {
        if (label == null || label.isBlank()) return "Block";
        return label.length() <= 22 ? label : label.substring(0, 21) + "…";
    }

    private List<LinkedHashMap<String, Object>> buildGraphPoints(float drawWidth,
                                                                 float drawGraphHeight,
                                                                 float drawBaseScale,
                                                                 List<Float> samples,
                                                                 boolean preview) {
        int sourceCount = preview ? 48 : samples.size();
        if (sourceCount < 2 || drawGraphHeight <= 0.0f) return List.of();

        float plotWidth = Math.max(1.0f, drawWidth - 14.0f * drawBaseScale);
        float plotHeight = Math.max(1.0f, drawGraphHeight - 25.0f * drawBaseScale);
        float[] source = new float[sourceCount];
        for (int i = 0; i < sourceCount; i++) {
            if (preview) {
                source[i] = 3.4f + (float) Math.sin(i * 0.28f) * 1.6f
                        + (float) Math.sin(i * 0.09f) * 0.8f;
            } else {
                source[i] = Math.max(0.0f, samples.get(i));
            }
        }

        // Weighted smoothing removes tick-to-tick movement noise without making
        // acceleration feel delayed. Catmull-Rom resampling below then gives the
        // renderer evenly spaced, sub-pixel-friendly segments.
        float[] smooth = new float[sourceCount];
        for (int i = 0; i < sourceCount; i++) {
            float weighted = 0.0f;
            float weightSum = 0.0f;
            for (int offset = -2; offset <= 2; offset++) {
                int index = Math.max(0, Math.min(sourceCount - 1, i + offset));
                float weight = 3.0f - Math.abs(offset);
                weighted += source[index] * weight;
                weightSum += weight;
            }
            smooth[i] = weighted / weightSum;
        }

        float maxSpeed = 6.0f;
        for (float sample : smooth) {
            maxSpeed = Math.max(maxSpeed, sample * 1.15f);
        }
        if (preview) maxSpeed = Math.max(maxSpeed, 8.0f);

        int count = Math.max(32, Math.min(64, Math.round(plotWidth / Math.max(1.8f, 2.1f * drawBaseScale))));
        List<LinkedHashMap<String, Object>> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            float position = (sourceCount - 1.0f) * i / Math.max(1, count - 1);
            int index = Math.min(sourceCount - 1, (int) Math.floor(position));
            float t = position - index;
            float p0 = smooth[Math.max(0, index - 1)];
            float p1 = smooth[index];
            float p2 = smooth[Math.min(sourceCount - 1, index + 1)];
            float p3 = smooth[Math.min(sourceCount - 1, index + 2)];
            float t2 = t * t;
            float t3 = t2 * t;
            float value = 0.5f * ((2.0f * p1)
                    + (-p0 + p2) * t
                    + (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * t2
                    + (-p0 + 3.0f * p1 - 3.0f * p2 + p3) * t3);
            value = Math.max(Math.min(p1, p2), Math.min(Math.max(p1, p2), value));

            float px = plotWidth * i / Math.max(1, count - 1);
            float py = plotHeight - Math.min(1.0f, value / maxSpeed) * plotHeight;
            LinkedHashMap<String, Object> point = new LinkedHashMap<>();
            point.put("x", px);
            point.put("y", py);
            points.add(point);
        }
        return points;
    }

    private static String formatPlayTime(long elapsedMs) {
        long totalSeconds = Math.max(0L, elapsedMs / 1000L);
        long seconds = totalSeconds % 60L;
        long minutes = (totalSeconds / 60L) % 60L;
        long hours = totalSeconds / 3600L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
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
                uiHeaderLeft = HudRenderUtil.accentSurface(uiHeaderLeft, 0.20f);
                uiHeaderRight = HudRenderUtil.accentSurface(uiHeaderRight, 0.27f);
                uiBodyLeft = HudRenderUtil.accentSurface(uiBodyLeft, 0.18f);
                uiBodyRight = HudRenderUtil.accentSurface(uiBodyRight, 0.26f);
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

    private boolean isThemeMode() {
        return COLOR_THEME.equals(colorMode.get());
    }

    private boolean isCustomMode() {
        return COLOR_CUSTOM.equals(colorMode.get());
    }

    private boolean isAccentPanelStyle() {
        return isThemeMode() && HudRenderUtil.PANEL_STYLE_ACCENT.equals(panelStyle.get());
    }

    private boolean hasEffect() {
        return !EFFECT_NONE.equals(bgEffect.get());
    }

    private int resolveStrokeGradientStart() {
        if (isThemeMode()) return HudRenderUtil.themeAccentGradient(255).start();
        return stroke.getArgb() | 0xFF000000;
    }

    private int resolveStrokeGradientEnd() {
        if (isThemeMode()) return HudRenderUtil.themeAccentGradient(255).end();
        return stroke.getArgb() | 0xFF000000;
    }

}
