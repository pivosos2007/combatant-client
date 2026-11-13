/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.impl.RenderPrewarmCollectEvent;
import combatant.client.features.gui.hud.HudElementInfo;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.gauges.GaugeRegistry;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.TextSizing;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static combatant.client.features.theme.Theme.theme;

@HudElementInfo(id = "gauges", displayName = "Gauges", enabledByDefault = false,
        order = 190, description = "hud.draggable.gauges.description")
public final class Gauges extends DraggableHudElement {
    private static final float MIN_WIDTH = 115f;
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final Minecraft mc = Minecraft.getInstance();
    private final ScriptedListHudPanel panel = new ScriptedListHudPanel();
    private final NumberValue<Double> scale = num("gauges_scale", 1.0, 0.7, 1.6);
    private final ModeValue layout = mode("gauges_layout", HudPanelLayoutModes.SPLIT_HEADER,
            new String[]{HudPanelLayoutModes.SPLIT_HEADER, HudPanelLayoutModes.UNIFIED_DIVIDER});
    private final ModeValue style = mode("gauges_style", "Bar", new String[]{"Bar", "Segments"});
    private final BooleanValue detail = bool("gauges_show_detail", false);
    private final BooleanValue blur = bool("gauges_blur", true);
    private final NumberValue<Integer> backgroundAlpha = num("gauges_background_alpha", 188, 0, 255);
    private final NumberValue<Integer> blurAlpha = visibleWhen(num("gauges_blur_alpha", 185, 0, 255), blur::get);
    private final Map<String, Float> progress = new LinkedHashMap<>();
    private final Map<String, Float> presence = new LinkedHashMap<>();
    private final Map<String, GaugeRegistry.Gauge> remembered = new LinkedHashMap<>();
    private float animatedWidth = -1f;
    private float animatedHeight = -1f;
    private float visible = 0f;

    public Gauges() {
        super("gauges", "Gauges", "hud.draggable.gauges.description", false);
        defaultLayout(20f, 125f);
    }

    @Override
    public boolean usesEngineRenderer() { return true; }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        x = 20f;
        y = 125f;
    }

    @Override
    public void collectRenderPrewarm(RenderPrewarmCollectEvent event) {
        super.collectRenderPrewarm(event);
        event.svg("chart-spline");
    }

    @Override
    public void renderEngine(Renderer2D renderer, TextRenderer fallback,
                             GuiGraphicsExtractor ctx, float tickDelta, int screenW, int screenH) {
        boolean preview = DraggableHudElementRegistry.isForceVisible()
                || ClientScreen.current() instanceof ChatScreen;
        if (mc == null || (mc.player == null && !preview)) {
            remembered.clear();
            progress.clear();
            presence.clear();
            animatedWidth = -1f;
            animatedHeight = -1f;
            visible = 0f;
            width = 0f;
            height = 0f;
            return;
        }
        List<GaugeRegistry.Gauge> values = GaugeRegistry.snapshot();
        if (values.isEmpty() && preview) values = List.of(
                new GaugeRegistry.Gauge("preview:timer", "Timer", .72f,
                        GaugeRegistry.Readiness.THRESHOLD, .4f, true, GaugeRegistry.Flow.DRAIN, ""),
                new GaugeRegistry.Gauge("preview:buffer", "Buffer", .35f,
                        GaugeRegistry.Readiness.FULL, 1f, false, GaugeRegistry.Flow.FILL, ""),
                new GaugeRegistry.Gauge("preview:ready", "Shulker", 1f,
                        GaugeRegistry.Readiness.FULL, 1f, true, GaugeRegistry.Flow.FILL, "Ready", GaugeRegistry.Kind.MESSAGE));
        Set<String> current = new HashSet<>();
        for (GaugeRegistry.Gauge value : values) {
            String id = value.id();
            current.add(id);
            remembered.put(id, value);
            progress.put(id, AnimationUtility.snap(
                    approach(progress.getOrDefault(id, value.fill()), value.fill(), 24f), value.fill(), .005f));
            presence.put(id, animatePresence(presence.getOrDefault(id, 0f), true));
        }
        for (String id : List.copyOf(remembered.keySet())) {
            if (current.contains(id)) continue;
            float next = animatePresence(presence.getOrDefault(id, 1f), false);
            if (next <= 0f) {
                presence.remove(id);
                progress.remove(id);
                remembered.remove(id);
            } else presence.put(id, next);
        }
        visible = animateVisibility(visible, !remembered.isEmpty() || preview);
        if (remembered.isEmpty() && visible < .015f) {
            width = 0f;
            height = 0f;
            return;
        }

        TextRenderer reference = fallback != null ? fallback : TextRenderer.get();
        TextRenderer rowFont = BuiltinFontCatalog.ONEST_MEDIUM.renderer(reference);
        TextRenderer titleFont = BuiltinFontCatalog.INTER_BOLD.renderer(reference);
        if (rowFont == null) rowFont = reference;
        if (titleFont == null) titleFont = reference;
        float baseScale = HudScale.scale(screenW, screenH) * 1.1f * scale.get().floatValue() * 2f;
        float fontScale = .98f * TextSizing.scaleForSize(hud.getFontSize());
        int alpha = backgroundAlpha.get();
        int window = HudRenderUtil.setAlpha(theme().windowBg(), alpha);
        int header = HudRenderUtil.setAlpha(theme().windowHeader(), Math.min(255, alpha + 12));
        int body = HudRenderUtil.setAlpha(theme().surface(), alpha);
        int text = theme().textPrimary();
        int muted = theme().textMuted();
        int accent = theme().accent();
        int softAccent = theme().accentSoft();
        int outline = theme().windowStroke();
        int track = HudRenderUtil.setAlpha(muted, 66);

        float maxLabelWidth = 0f;
        float maxMessageWidth = 0f;
        float rowFontHeight, headerFontHeight, titleWidth;
        rowFont.begin(fontScale, true, false);
        rowFontHeight = (float) rowFont.getHeight(false);
        for (GaugeRegistry.Gauge gauge : remembered.values()) {
            String label = caption(gauge);
            maxLabelWidth = Math.max(maxLabelWidth, (float) rowFont.getWidth(label, false));
            if (gauge.kind() == GaugeRegistry.Kind.MESSAGE) {
                maxMessageWidth = Math.max(maxMessageWidth, (float) rowFont.getWidth(gauge.detail(), false));
            }
        }
        float countLabelW = (float) rowFont.getWidth("Active:", false);
        float countW = (float) rowFont.getWidth(Integer.toString(values.size()), false);
        rowFont.end();
        titleFont.begin(fontScale, true, false);
        headerFontHeight = (float) titleFont.getHeight(false);
        titleWidth = (float) titleFont.getWidth("Gauges", false);
        titleFont.end();

        float desiredWidth = Math.max(MIN_WIDTH * baseScale,
                Math.max(Math.max(maxLabelWidth + 84f * baseScale,
                                maxLabelWidth + maxMessageWidth + 30f * baseScale),
                        titleWidth + countLabelW + countW + 35f * baseScale));
        float heightUnits = 25f;
        List<LinkedHashMap<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, GaugeRegistry.Gauge> entry : remembered.entrySet()) {
            GaugeRegistry.Gauge gauge = entry.getValue();
            String id = entry.getKey();
            float rowPresence = presence.getOrDefault(id, 0f);
            boolean exiting = !current.contains(id);
            float layoutProgress = rowPresence;
            heightUnits += ScriptedListHudPanel.ROW_STEP * layoutProgress;
            float level = progress.getOrDefault(id, gauge.fill());
            int gaugeColor = gauge.ready() ? accent : (gauge.flow() == GaugeRegistry.Flow.DRAIN
                    ? softAccent : muted);
            boolean isMessage = gauge.kind() == GaugeRegistry.Kind.MESSAGE;
            String percent = isMessage ? gauge.detail() : Math.round(level * 100f) + "%";
            var row = ScriptedListHudPanel.row(id, "", "",
                    List.of(ScriptedListHudPanel.textPart(caption(gauge), text, 0f)),
                    percent, gauge.ready() ? text : muted,
                    HudRenderUtil.setAlpha(muted, 85), rowPresence);
            float alphaFactor = exiting ? exitAlpha(rowPresence) : rowPresence;
            row.put("alpha", alphaFactor);
            ScriptedListHudPanel.rowLayoutProgress(row, layoutProgress);
            row.put("rightMode", isMessage ? "text" : "gauge");
            row.put("meterFill", level);
            row.put("meterThreshold", gauge.target());
            row.put("meterThresholdVisible", gauge.readiness() == GaugeRegistry.Readiness.THRESHOLD
                    && gauge.target() > 0f);
            row.put("meterColor", ScriptedListHudPanel.hex(gaugeColor));
            row.put("meterTrack", ScriptedListHudPanel.hex(track));
            row.put("meterStyle", style.get());
            rows.add(row);
        }
        float desiredHeight = heightUnits * baseScale;
        animatedWidth = HudRenderUtil.animateDimension(animatedWidth, desiredWidth);
        animatedHeight = HudRenderUtil.animateDimension(animatedHeight, desiredHeight);
        width = animatedWidth;
        height = animatedHeight;
        if (visible <= 0f) return;
        float scaleIn = Math.max(.01f, HudRenderUtil.visibilityScale(visible));
        float drawX = x + (width * (1f - scaleIn)) * .5f;
        float drawY = y + (height * (1f - scaleIn)) * .5f;
        float drawWidth = width * scaleIn;
        float drawHeight = height * scaleIn;
        float drawScale = baseScale * scaleIn;

        HudRenderUtil.drawHudShadow(renderer, drawX, drawY, drawWidth, drawHeight,
                ScriptedListHudPanel.PANEL_RADIUS * drawScale, drawScale, false, 38, visible);
        panel.render(renderer, fallback, ctx, tickDelta, new ScriptedListHudPanel.Panel(
                ScriptedListHudPanel.GAUGES,
                new ScriptedListHudPanel.Palette(
                        HudRenderUtil.mixColor(header, window, .2f), header,
                        HudRenderUtil.mixColor(window, body, .2f), body,
                        outline, text, muted, softAccent, text,
                        HudRenderUtil.setAlpha(muted, 90), body),
                drawX, drawY, drawWidth, drawHeight, drawScale,
                fontScale * scaleIn, 8f * drawScale, headerFontHeight * scaleIn,
                rowFontHeight * scaleIn, countLabelW * scaleIn, countW * scaleIn,
                values.size(), blur.get(), blurAlpha.get() / 255f,
                accent, false, accent, softAccent, 45f,
                layout.get(), false, 0f, false, outline, outline,
                false, rows, ""));
    }

    private String caption(GaugeRegistry.Gauge gauge) {
        return gauge.kind() != GaugeRegistry.Kind.MESSAGE && detail.get() && !gauge.detail().isBlank()
                ? gauge.label() + " · " + gauge.detail() : gauge.label();
    }

    private static float exitAlpha(float anim) {
        float t = Math.max(0f, Math.min(1f, (anim - .18f) / .64f));
        return t * t * (3f - 2f * t);
    }

    private static float animatePresence(float current, boolean appearing) {
        return animateTo(current, appearing, appearing ? .14f : .16f);
    }

    private static float animateVisibility(float current, boolean showing) {
        return animateTo(current, showing, showing ? .12f : .14f);
    }

    private static float animateTo(float current, boolean showing, float duration) {
        float step = AnimationUtility.clamp01(AnimationUtility.deltaTime() / duration);
        float next = showing ? Math.min(1f, current + step) : Math.max(0f, current - step);
        return AnimationUtility.snap(next, showing ? 1f : 0f, .001f);
    }

    private static float approach(float current, float target, float speed) {
        return AnimationUtility.approach(current, target, AnimationUtility.deltaTime(), speed);
    }
}
