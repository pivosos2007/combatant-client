/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.util.resources.asset.UiScriptAsset;
import java.util.LinkedHashMap;
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
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

@UiScriptAsset("combatant:api/hud/draggable/triangulator")
final class ScriptedTriangulatorHudPanel {
    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(ScriptedTriangulatorHudPanel.class);
    private final CachedUiScriptRuntime runtime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());

    static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    static LinkedHashMap<String, Object> row(String key,
                                                        String label,
                                                        String value,
                                                        int labelColor,
                                                        int valueColor,
                                                        int markerColor,
                                                        int dividerColor,
                                                        float alpha) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("key", key != null ? key : "");
        row.put("label", label != null ? label : "");
        row.put("value", value != null ? value : "");
        row.put("labelColor", hex(labelColor));
        row.put("valueColor", hex(valueColor));
        row.put("markerColor", hex(markerColor));
        row.put("dividerColor", hex(dividerColor));
        row.put("alpha", alpha);
        return row;
    }

    static String idString(Identifier id) {
        return id != null ? id.toString() : "";
    }

    static List<LinkedHashMap<String, Object>> rows() {
        return new ArrayList<>();
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
                "triangulator",
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

    private static String string(Object value) {
        return value instanceof String s ? s : "";
    }

    private static float floatValue(Object value) {
        return value instanceof Number n ? n.floatValue() : 0.0f;
    }

    private static void putPatch(LinkedHashMap<String, LinkedHashMap<String, Object>> patches,
                                 String key,
                                 String prop,
                                 Object value) {
        LinkedHashMap<String, Object> patch = patches.get(key);
        if (patch == null) {
            patch = new LinkedHashMap<>();
            patches.put(key, patch);
        }
        patch.put(prop, value);
    }
    record Palette(int headerLeft,
                   int headerRight,
                   int bodyLeft,
                   int bodyRight,
                   int outline,
                   int text,
                   int muted,
                   int counter,
                   int titleText,
                   int divider,
                   int blurTint,
                   int success,
                   int warn,
                   int danger) {
        LinkedHashMap<String, Object> toProps() {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("headerLeft", hex(headerLeft));
            out.put("headerRight", hex(headerRight));
            out.put("bodyLeft", hex(bodyLeft));
            out.put("bodyRight", hex(bodyRight));
            out.put("outline", hex(outline));
            out.put("text", hex(text));
            out.put("muted", hex(muted));
            out.put("counter", hex(counter));
            out.put("titleText", hex(titleText));
            out.put("divider", hex(divider));
            out.put("blurTint", hex(blurTint));
            out.put("success", hex(success));
            out.put("warn", hex(warn));
            out.put("danger", hex(danger));
            return out;
        }
    }

    record Panel(Palette palette,
                 float x,
                 float y,
                 float width,
                 float height,
                 float drawScale,
                 float baseScale,
                 float fontScale,
                 float headerTextHeight,
                 float rowTextHeight,
                 float countLabelWidth,
                 float countValueWidth,
                 int activeCount,
                 boolean blur,
                 float blurAlpha,
                 boolean strokeEnabled,
                 float strokeAlpha,
                 boolean strokeGradient,
                 int strokeStartColor,
                 int strokeEndColor,
                 String headerIcon,
                 int headerIconColor,
                 boolean headerIconGradient,
                 int headerIconGradientStart,
                 int headerIconGradientEnd,
                 float headerIconGradientAngle,
                 boolean clearVisible,
                 int clearIconColor,
                 boolean copyVisible,
                 int copyIconColor,
                 String statusText,
                 String primaryLine,
                 String secondaryLine,
                 String confidenceText,
                 int statusColor,
                 List<LinkedHashMap<String, Object>> rows) {
        Panel(Palette palette,
              float x,
              float y,
              float width,
              float height,
              float drawScale,
              float baseScale,
              float fontScale,
              float headerTextHeight,
              float rowTextHeight,
              float countLabelWidth,
              float countValueWidth,
              int activeCount,
              boolean blur,
              float blurAlpha,
              boolean strokeEnabled,
              float strokeAlpha,
              boolean strokeGradient,
              int strokeStartColor,
              int strokeEndColor,
              String headerIcon,
              int headerIconColor,
              boolean headerIconGradient,
              int headerIconGradientStart,
              int headerIconGradientEnd,
              float headerIconGradientAngle,
              boolean clearVisible,
              int clearIconColor,
              boolean copyVisible,
              int copyIconColor,
              String statusText,
              String primaryLine,
              String secondaryLine,
              String confidenceText,
              int statusColor,
              List<LinkedHashMap<String, Object>> rows) {
            this.palette = palette;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.drawScale = drawScale;
            this.baseScale = baseScale;
            this.fontScale = fontScale;
            this.headerTextHeight = headerTextHeight;
            this.rowTextHeight = rowTextHeight;
            this.countLabelWidth = countLabelWidth;
            this.countValueWidth = countValueWidth;
            this.activeCount = activeCount;
            this.blur = blur;
            this.blurAlpha = blurAlpha;
            this.strokeEnabled = strokeEnabled;
            this.strokeAlpha = Math.max(0.0f, Math.min(1.0f, strokeAlpha));
            this.strokeGradient = strokeGradient;
            this.strokeStartColor = strokeStartColor;
            this.strokeEndColor = strokeEndColor;
            this.headerIcon = headerIcon != null ? headerIcon : "";
            this.headerIconColor = headerIconColor;
            this.headerIconGradient = headerIconGradient;
            this.headerIconGradientStart = headerIconGradientStart;
            this.headerIconGradientEnd = headerIconGradientEnd;
            this.headerIconGradientAngle = headerIconGradientAngle;
            this.clearVisible = clearVisible;
            this.clearIconColor = clearIconColor;
            this.copyVisible = copyVisible;
            this.copyIconColor = copyIconColor;
            this.statusText = statusText != null ? statusText : "";
            this.primaryLine = primaryLine != null ? primaryLine : "";
            this.secondaryLine = secondaryLine != null ? secondaryLine : "";
            this.confidenceText = confidenceText != null ? confidenceText : "";
            this.statusColor = statusColor;
            this.rows = rows != null ? rows : List.of();
        }

        LinkedHashMap<String, Object> toProps() {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("id", "triangulator");
            out.put("title", "Triangulator");
            out.put("width", width);
            out.put("height", height);
            out.put("drawScale", drawScale);
            out.put("baseScale", baseScale);
            out.put("fontScale", fontScale);
            out.put("headerTextHeight", headerTextHeight);
            out.put("rowTextHeight", rowTextHeight);
            out.put("countLabelWidth", countLabelWidth);
            out.put("countValueWidth", countValueWidth);
            out.put("activeCount", activeCount);
            out.put("blur", blur);
            out.put("blurAlpha", blurAlpha);
            out.put("shadowControlled", true);
            out.put("strokeEnabled", strokeEnabled);
            out.put("strokeAlpha", strokeAlpha);
            out.put("strokeGradient", strokeGradient);
            out.put("strokeStartColor", hex(strokeStartColor));
            out.put("strokeEndColor", hex(strokeEndColor));
            out.put("headerIcon", headerIcon);
            out.put("headerIconColor", hex(headerIconColor));
            out.put("headerIconGradient", headerIconGradient);
            out.put("headerIconGradientStart", hex(headerIconGradientStart));
            out.put("headerIconGradientEnd", hex(headerIconGradientEnd));
            out.put("headerIconGradientAngle", headerIconGradientAngle);
            out.put("clearVisible", clearVisible);
            out.put("clearIcon", "x");
            out.put("clearIconColor", hex(clearIconColor));
            out.put("copyVisible", copyVisible);
            out.put("copyIcon", "clipboard");
            out.put("copyIconColor", hex(copyIconColor));
            out.put("statusText", statusText);
            out.put("primaryLine", primaryLine);
            out.put("secondaryLine", secondaryLine);
            out.put("confidenceText", confidenceText);
            out.put("statusColor", hex(statusColor));
            out.put("palette", palette.toProps());
            out.put("rows", rows.toArray());
            return out;
        }

        long treeSignature() {
            long h = 0xcbf29ce484222325L;
            h = CachedUiScriptRuntime.mix(h, width);
            h = CachedUiScriptRuntime.mix(h, height);
            h = CachedUiScriptRuntime.mix(h, drawScale);
            h = CachedUiScriptRuntime.mix(h, baseScale);
            h = CachedUiScriptRuntime.mix(h, fontScale);
            h = CachedUiScriptRuntime.mix(h, headerTextHeight);
            h = CachedUiScriptRuntime.mix(h, rowTextHeight);
            h = CachedUiScriptRuntime.mix(h, countLabelWidth);
            h = CachedUiScriptRuntime.mix(h, countValueWidth);
            h = CachedUiScriptRuntime.mix(h, blur);
            h = CachedUiScriptRuntime.mix(h, blurAlpha);
            h = CachedUiScriptRuntime.mix(h, strokeEnabled);
            h = CachedUiScriptRuntime.mix(h, strokeAlpha);
            h = CachedUiScriptRuntime.mix(h, strokeGradient);
            h = CachedUiScriptRuntime.mix(h, headerIconGradient);
            h = CachedUiScriptRuntime.mix(h, headerIconGradientAngle);
            h = CachedUiScriptRuntime.mix(h, clearVisible);
            h = CachedUiScriptRuntime.mix(h, copyVisible);
            h = CachedUiScriptRuntime.mix(h, String.valueOf(Math.max(0, activeCount)).length());
            h = CachedUiScriptRuntime.mix(h, activeCount);
            h = CachedUiScriptRuntime.mix(h, statusText.length());
            h = CachedUiScriptRuntime.mix(h, primaryLine.length());
            h = CachedUiScriptRuntime.mix(h, secondaryLine.length());
            h = CachedUiScriptRuntime.mix(h, confidenceText.length());
            h = CachedUiScriptRuntime.mix(h, rows.size());
            for (LinkedHashMap<String, Object> row : rows) {
                h = CachedUiScriptRuntime.mix(h, string(row.get("key")));
                h = CachedUiScriptRuntime.mix(h, string(row.get("label")).length());
                h = CachedUiScriptRuntime.mix(h, string(row.get("value")).length());
                h = CachedUiScriptRuntime.mix(h, Math.round(floatValue(row.get("alpha")) * 100.0f));
            }
            return h;
        }

        long dataSignature() {
            long h = 0xcbf29ce484222325L;
            h = mixPalette(h);
            h = CachedUiScriptRuntime.mix(h, activeCount);
            h = CachedUiScriptRuntime.mix(h, strokeEnabled);
            h = CachedUiScriptRuntime.mix(h, strokeAlpha);
            h = CachedUiScriptRuntime.mix(h, strokeGradient);
            h = CachedUiScriptRuntime.mix(h, strokeStartColor);
            h = CachedUiScriptRuntime.mix(h, strokeEndColor);
            h = CachedUiScriptRuntime.mix(h, headerIcon);
            h = CachedUiScriptRuntime.mix(h, headerIconColor);
            h = CachedUiScriptRuntime.mix(h, headerIconGradientStart);
            h = CachedUiScriptRuntime.mix(h, headerIconGradientEnd);
            h = CachedUiScriptRuntime.mix(h, clearIconColor);
            h = CachedUiScriptRuntime.mix(h, copyIconColor);
            h = CachedUiScriptRuntime.mix(h, statusText);
            h = CachedUiScriptRuntime.mix(h, primaryLine);
            h = CachedUiScriptRuntime.mix(h, secondaryLine);
            h = CachedUiScriptRuntime.mix(h, confidenceText);
            h = CachedUiScriptRuntime.mix(h, statusColor);
            for (LinkedHashMap<String, Object> row : rows) {
                h = CachedUiScriptRuntime.mix(h, string(row.get("key")));
                h = CachedUiScriptRuntime.mix(h, string(row.get("label")));
                h = CachedUiScriptRuntime.mix(h, string(row.get("value")));
                h = CachedUiScriptRuntime.mix(h, string(row.get("labelColor")));
                h = CachedUiScriptRuntime.mix(h, string(row.get("valueColor")));
                h = CachedUiScriptRuntime.mix(h, string(row.get("markerColor")));
                h = CachedUiScriptRuntime.mix(h, string(row.get("dividerColor")));
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
            putPatch(patches, "header:count-value", "text", String.valueOf(Math.max(0, activeCount)));
            putPatch(patches, "header:icon", "tint", hex(headerIconColor));
            putPatch(patches, "header:icon", "gradientStartColor", hex(headerIconGradientStart));
            putPatch(patches, "header:icon", "gradientEndColor", hex(headerIconGradientEnd));
            putPatch(patches, "clear:icon", "tint", hex(clearIconColor));
            putPatch(patches, "copy:icon", "tint", hex(copyIconColor));
            putPatch(patches, "summary:status", "text", statusText);
            putPatch(patches, "summary:status", "color", hex(statusColor));
            putPatch(patches, "summary:primary", "text", primaryLine);
            putPatch(patches, "summary:secondary", "text", secondaryLine);
            putPatch(patches, "summary:confidence", "text", confidenceText);
            putPatch(patches, "summary:confidence", "color", hex(statusColor));
            for (LinkedHashMap<String, Object> row : rows) {
                String key = string(row.get("key"));
                String prefix = "row:" + key;
                putPatch(patches, prefix + ":marker", "fill", string(row.get("markerColor")));
                putPatch(patches, prefix + ":divider", "fill", string(row.get("dividerColor")));
                putPatch(patches, prefix + ":label", "text", string(row.get("label")));
                putPatch(patches, prefix + ":label", "color", string(row.get("labelColor")));
                putPatch(patches, prefix + ":value", "text", string(row.get("value")));
                putPatch(patches, prefix + ":value", "color", string(row.get("valueColor")));
            }
            return patches;
        }

        private long mixPalette(long h) {
            h = CachedUiScriptRuntime.mix(h, palette.headerLeft);
            h = CachedUiScriptRuntime.mix(h, palette.headerRight);
            h = CachedUiScriptRuntime.mix(h, palette.bodyLeft);
            h = CachedUiScriptRuntime.mix(h, palette.bodyRight);
            h = CachedUiScriptRuntime.mix(h, palette.outline);
            h = CachedUiScriptRuntime.mix(h, palette.text);
            h = CachedUiScriptRuntime.mix(h, palette.muted);
            h = CachedUiScriptRuntime.mix(h, palette.counter);
            h = CachedUiScriptRuntime.mix(h, palette.titleText);
            h = CachedUiScriptRuntime.mix(h, palette.divider);
            h = CachedUiScriptRuntime.mix(h, palette.blurTint);
            h = CachedUiScriptRuntime.mix(h, palette.success);
            h = CachedUiScriptRuntime.mix(h, palette.warn);
            h = CachedUiScriptRuntime.mix(h, palette.danger);
            return h;
        }
    }
}
