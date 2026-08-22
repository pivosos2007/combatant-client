/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ScriptedStatisticsHudPanel {
    private static final String LAYOUT_ID = "combatant:api/hud/draggable/statistics_panel";

    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(LAYOUT_ID);
    private final CachedUiScriptRuntime runtime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    private static float floatValue(Object value) {
        return value instanceof Number number ? number.floatValue() : 0.0f;
    }

    private static String string(Object value) {
        return value instanceof String text ? text : "";
    }

    private static void putPatch(LinkedHashMap<String, LinkedHashMap<String, Object>> patches,
                                 String key,
                                 String prop,
                                 Object value) {
        patches.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(prop, value);
    }

    boolean render(Renderer2D renderer,
                   TextRenderer textRenderer,
                   GuiGraphicsExtractor ctx,
                   float tickDelta,
                   Panel panel) {
        if (renderer == null || panel == null || panel.width <= 0.0f || panel.height <= 0.0f) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) return false;

        HudScriptLayouts.pollReloadCombo(mc);
        if (moduleHandle.consumeChanged()) {
            runtime.reset();
        }

        UiScriptModule module = ensureModule(mc);
        if (module == null) return false;

        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        LinkedHashMap<String, Object> props = panel.toProps();
        UiRuntime baked = runtime.bake(
                moduleHandle,
                module,
                "statistics",
                panel.treeSignature(),
                panel.dataSignature(),
                panel.layoutSignature(),
                panel.width,
                panel.height,
                fallback,
                panel.x,
                panel.y,
                panel.width,
                panel.height,
                () -> props,
                panel::patches
        );
        if (baked == null) return false;
        baked.render(new UiRenderContext(renderer, fallback, ctx, tickDelta, UiProjectionMode.CURRENT));
        return true;
    }

    private UiScriptModule ensureModule(Minecraft mc) {
        if (!moduleHandle.ensureLoaded(mc.getResourceManager())) {
            HudScriptLayouts.reportLoadError(moduleHandle);
            return null;
        }
        moduleHandle.consumeChanged();
        return moduleHandle.module();
    }

    record Panel(float x,
                 float y,
                 float width,
                 float height,
                 float mainHeight,
                 float graphHeight,
                 float graphGap,
                 float drawScale,
                 float baseScale,
                 float fontScale,
                 float headerTextHeight,
                 float rowTextHeight,
                 String playTime,
                 String averageSpeed,
                 float arcEndAngle,
                 boolean showPlayTime,
                 boolean showGraph,
                 boolean separateGraph,
                 boolean blur,
                 float blurAlpha,
                 String layout,
                 boolean strokeEnabled,
                 float strokeAlpha,
                 boolean strokeGradient,
                 int strokeStartColor,
                 int strokeEndColor,
                 int accentStartColor,
                 int accentEndColor,
                 int headerIconColor,
                 boolean shadowControlled,
                 ScriptedListHudPanel.Palette palette,
                 List<LinkedHashMap<String, Object>> rows,
                 List<LinkedHashMap<String, Object>> graphPoints) {

        Panel {
            playTime = playTime != null ? playTime : "00:00";
            averageSpeed = averageSpeed != null ? averageSpeed : "0.00 BPS";
            layout = layout != null ? layout : HudPanelLayoutModes.SPLIT_HEADER;
            strokeAlpha = Math.max(0.0f, Math.min(1.0f, strokeAlpha));
            rows = rows != null ? rows : List.of();
            graphPoints = graphPoints != null ? graphPoints : List.of();
        }

        LinkedHashMap<String, Object> toProps() {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("id", "statistics");
            out.put("title", "Statistics");
            out.put("headerIconAsset", "chart-spline");
            out.put("width", width);
            out.put("height", height);
            out.put("mainHeight", mainHeight);
            out.put("graphHeight", graphHeight);
            out.put("graphGap", graphGap);
            out.put("drawScale", drawScale);
            out.put("baseScale", baseScale);
            out.put("fontScale", fontScale);
            out.put("headerTextHeight", headerTextHeight);
            out.put("rowTextHeight", rowTextHeight);
            out.put("playTime", playTime);
            out.put("averageSpeed", averageSpeed);
            out.put("arcEndAngle", arcEndAngle);
            out.put("showPlayTime", showPlayTime);
            out.put("showGraph", showGraph);
            out.put("separateGraph", separateGraph);
            out.put("blur", blur);
            out.put("blurAlpha", blurAlpha);
            out.put("layout", layout);
            out.put("strokeEnabled", strokeEnabled);
            out.put("strokeAlpha", strokeAlpha);
            out.put("strokeGradient", strokeGradient);
            out.put("strokeStartColor", hex(strokeStartColor));
            out.put("strokeEndColor", hex(strokeEndColor));
            out.put("accentStartColor", hex(accentStartColor));
            out.put("accentEndColor", hex(accentEndColor));
            out.put("headerIconColor", hex(headerIconColor));
            out.put("shadowControlled", shadowControlled);
            out.put("palette", palette.toProps());
            out.put("rows", rows.toArray());
            out.put("graphPoints", graphPoints.toArray());

            LinkedHashMap<String, Object> variant = new LinkedHashMap<>();
            variant.put("headerDividerX", 18.0f);
            variant.put("titleIconX", 5.0f);
            variant.put("titleTextX", 22.0f);
            out.put("variant", variant);
            return out;
        }

        long treeSignature() {
            long h = 0xcbf29ce484222325L;
            h = CachedUiScriptRuntime.mix(h, width);
            h = CachedUiScriptRuntime.mix(h, height);
            h = CachedUiScriptRuntime.mix(h, mainHeight);
            h = CachedUiScriptRuntime.mix(h, graphHeight);
            h = CachedUiScriptRuntime.mix(h, graphGap);
            h = CachedUiScriptRuntime.mix(h, drawScale);
            h = CachedUiScriptRuntime.mix(h, baseScale);
            h = CachedUiScriptRuntime.mix(h, fontScale);
            h = CachedUiScriptRuntime.mix(h, headerTextHeight);
            h = CachedUiScriptRuntime.mix(h, rowTextHeight);
            h = CachedUiScriptRuntime.mix(h, showPlayTime);
            h = CachedUiScriptRuntime.mix(h, showGraph);
            h = CachedUiScriptRuntime.mix(h, separateGraph);
            h = CachedUiScriptRuntime.mix(h, blur);
            h = CachedUiScriptRuntime.mix(h, blurAlpha);
            h = CachedUiScriptRuntime.mix(h, layout);
            h = CachedUiScriptRuntime.mix(h, strokeEnabled);
            h = CachedUiScriptRuntime.mix(h, strokeAlpha);
            h = CachedUiScriptRuntime.mix(h, strokeGradient);
            h = CachedUiScriptRuntime.mix(h, strokeStartColor);
            h = CachedUiScriptRuntime.mix(h, strokeEndColor);
            h = CachedUiScriptRuntime.mix(h, accentStartColor);
            h = CachedUiScriptRuntime.mix(h, accentEndColor);
            h = CachedUiScriptRuntime.mix(h, shadowControlled);
            h = CachedUiScriptRuntime.mix(h, rows.size());
            for (Map<String, Object> row : rows) {
                h = CachedUiScriptRuntime.mix(h, string(row.get("key")));
                h = CachedUiScriptRuntime.mix(h, string(row.get("label")));
                h = CachedUiScriptRuntime.mix(h, floatValue(row.get("animation")));
            }
            h = mixPalette(h);
            return h;
        }

        long dataSignature() {
            long h = 0xcbf29ce484222325L;
            h = CachedUiScriptRuntime.mix(h, playTime);
            h = CachedUiScriptRuntime.mix(h, averageSpeed);
            h = CachedUiScriptRuntime.mix(h, arcEndAngle);
            h = CachedUiScriptRuntime.mix(h, headerIconColor);
            for (Map<String, Object> row : rows) {
                h = CachedUiScriptRuntime.mix(h, string(row.get("value")));
                h = CachedUiScriptRuntime.mix(h, string(row.get("valueColor")));
            }
            for (Map<String, Object> point : graphPoints) {
                h = CachedUiScriptRuntime.mix(h, floatValue(point.get("x")));
                h = CachedUiScriptRuntime.mix(h, floatValue(point.get("y")));
            }
            return h;
        }

        long layoutSignature() {
            long h = 0xcbf29ce484222325L;
            h = CachedUiScriptRuntime.mix(h, x);
            h = CachedUiScriptRuntime.mix(h, y);
            h = CachedUiScriptRuntime.mix(h, width);
            h = CachedUiScriptRuntime.mix(h, height);
            return h;
        }

        LinkedHashMap<String, LinkedHashMap<String, Object>> patches() {
            LinkedHashMap<String, LinkedHashMap<String, Object>> patches = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String key = string(row.get("key"));
                putPatch(patches, "stat:" + key + ":value", "text", string(row.get("value")));
            }
            if (showPlayTime) {
                putPatch(patches, "playtime:value", "text", playTime);
                putPatch(patches, "playtime:arc", "endAngle", arcEndAngle);
            }
            putPatch(patches, "header:icon", "tint", hex(headerIconColor));
            if (showGraph) {
                putPatch(patches, "graph:average", "text", "Average: " + averageSpeed);
                putPatch(patches, "graph:area", "points", graphPoints);
                putPatch(patches, "graph:glow", "points", graphPoints);
                putPatch(patches, "graph:spline", "points", graphPoints);
            }
            return patches;
        }

        private long mixPalette(long h) {
            h = CachedUiScriptRuntime.mix(h, palette.headerLeft());
            h = CachedUiScriptRuntime.mix(h, palette.headerRight());
            h = CachedUiScriptRuntime.mix(h, palette.bodyLeft());
            h = CachedUiScriptRuntime.mix(h, palette.bodyRight());
            h = CachedUiScriptRuntime.mix(h, palette.outline());
            h = CachedUiScriptRuntime.mix(h, palette.text());
            h = CachedUiScriptRuntime.mix(h, palette.muted());
            h = CachedUiScriptRuntime.mix(h, palette.counter());
            h = CachedUiScriptRuntime.mix(h, palette.titleText());
            h = CachedUiScriptRuntime.mix(h, palette.divider());
            h = CachedUiScriptRuntime.mix(h, palette.blurTint());
            return h;
        }
    }
}
