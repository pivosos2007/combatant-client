/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.util.resources.asset.UiScriptAsset;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.engine.text.TextRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@UiScriptAsset("combatant:modules/hud/draggable/list_panel")
final class ScriptedListHudPanel {
    static final Variant POTIONS = new Variant(
            "potions", "Potions", String.valueOf((char) 0x42),
            95.0f, 22.0f, 3.0f,
            8.0f, 7.0f, 8.0f, 2.1f
    );
    static final Variant COOLDOWNS = new Variant(
            "cooldowns", "Cooldowns", "C",
            110.0f, 22.0f, 2.5f,
            8.0f, 6.0f, 8.0f, 1.9f
    );
    static final Variant ADMINS = new Variant(
            "admins", "Admins", String.valueOf((char) 0x45),
            96.0f, 22.0f, 3.0f,
            8.0f, 6.0f, 8.0f, 1.95f
    );
    static final Variant KEYBINDS = new Variant(
            "keybinds", "Keybinds", "M",
            102.0f, 22.0f, 3.0f,
            8.0f, 6.0f, 8.0f, 1.95f,
            "Icons", 1.18f
    );
    static final float HEADER_HEIGHT = 15.5f;
    static final float BODY_Y_OFFSET = 18.5f;
    static final float CONTENT_START_Y = 25.0f;
    static final float BODY_INSET_Y = 6.5f;
    static final float ROW_STEP = 11.0f;
    static final float PANEL_RADIUS = 4.0f;
    static final float PANEL_STROKE = 0.55f;

    // Shared timed-list geometry. Text width itself is never guessed from character count:
    // callers pass a TextRenderer-measured stable glyph reservation into the row props.
    static final float ROW_TEXT_START_X = 18.0f;
    static final float ROW_TEXT_RIGHT_GAP = 4.0f;
    static final float TIME_PILL_HEIGHT = 8.4f;
    static final float TIME_PILL_LEFT_PAD = 2.0f;
    static final float TIME_PILL_RING_BOX = 6.5f;
    static final float TIME_PILL_RING_TEXT_GAP = 1.35f;
    static final float TIME_PILL_RIGHT_PAD = 2.3f;
    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(ScriptedListHudPanel.class);
    private final CachedUiScriptRuntime runtime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());

    static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    static LinkedHashMap<String, Object> textPart(String text, int color, float x) {
        LinkedHashMap<String, Object> part = new LinkedHashMap<>();
        part.put("text", text != null ? text : "");
        part.put("color", hex(color));
        part.put("x", x);
        return part;
    }

    static LinkedHashMap<String, Object> row(String key,
                                                        String iconKind,
                                                        String icon,
                                                        List<LinkedHashMap<String, Object>> nameParts,
                                                        String rightText,
                                                        int rightColor,
                                                        int dividerColor,
                                                        float alpha) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("key", key != null ? key : "");
        row.put("iconKind", iconKind != null ? iconKind : "");
        row.put("icon", icon != null ? icon : "");
        row.put("nameParts", nameParts != null ? nameParts.toArray() : new Object[0]);
        row.put("rightText", rightText != null ? rightText : "");
        row.put("rightColor", hex(rightColor));
        row.put("dividerColor", hex(dividerColor));
        row.put("alpha", alpha);
        return row;
    }


    static void timePill(LinkedHashMap<String, Object> row,
                         float arcStart,
                         float arcEnd,
                         int fillStart,
                         int fillEnd,
                         int strokeStart,
                         int strokeEnd,
                         int arcBase,
                         int arcStartColor,
                         int arcEndColor) {
        if (row == null) return;
        row.put("rightMode", "time-pill");
        row.put("timeArcStart", arcStart);
        row.put("timeArcEnd", arcEnd);
        row.put("timeFillStart", hex(fillStart));
        row.put("timeFillEnd", hex(fillEnd));
        row.put("timeStrokeStart", hex(strokeStart));
        row.put("timeStrokeEnd", hex(strokeEnd));
        row.put("timeArcBase", hex(arcBase));
        row.put("timeArcStartColor", hex(arcStartColor));
        row.put("timeArcEndColor", hex(arcEndColor));
    }

    static void rowLayoutProgress(LinkedHashMap<String, Object> row, float layoutProgress) {
        if (row == null) return;
        row.put("layoutProgress", Math.max(0.0f, Math.min(1.0f, layoutProgress)));
    }

    static void timePillTextWidth(LinkedHashMap<String, Object> row, float rightTextWidth) {
        if (row == null) return;
        row.put("rightTextWidth", Math.max(0.0f, rightTextWidth));
    }

    static float widestDigitWidth(TextRenderer renderer) {
        if (renderer == null) return 0.0f;
        float widest = 0.0f;
        for (char c = '0'; c <= '9'; c++) {
            widest = Math.max(widest, (float) renderer.getWidth(String.valueOf(c), false));
        }
        return widest;
    }

    static float stableNumericTextWidth(TextRenderer renderer, String text, float widestDigitWidth) {
        if (renderer == null) return 0.0f;
        String value = text != null ? text : "";
        float width = 0.0f;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                width += widestDigitWidth;
            } else {
                width += (float) renderer.getWidth(String.valueOf(c), false);
            }
        }
        return Math.max(width, (float) renderer.getWidth(value, false));
    }

    static float reserveMeasuredWidth(Map<String, Float> reserve,
                                      String key,
                                      float measuredWidth) {
        if (reserve == null) return Math.max(0.0f, measuredWidth);
        String safeKey = key != null ? key : "";
        float next = Math.max(Math.max(0.0f, measuredWidth), reserve.getOrDefault(safeKey, 0.0f));
        reserve.put(safeKey, next);
        return next;
    }

    static float timePillWidth(float measuredTextWidth, float baseScale) {
        float chrome = TIME_PILL_LEFT_PAD + TIME_PILL_RING_BOX
                + TIME_PILL_RING_TEXT_GAP + TIME_PILL_RIGHT_PAD;
        return Math.max(0.0f, measuredTextWidth) + chrome * Math.max(0.0f, baseScale);
    }

    static float rowRequiredWidth(Variant variant,
                                  float baseScale,
                                  float labelWidth,
                                  float rightVisualWidth) {
        float scale = Math.max(0.0f, baseScale);
        float rightPad = (variant != null ? variant.rowRightPad : 8.0f) * scale;
        return ROW_TEXT_START_X * scale
                + Math.max(0.0f, labelWidth)
                + ROW_TEXT_RIGHT_GAP * scale
                + Math.max(0.0f, rightVisualWidth)
                + rightPad;
    }

    static String idString(Identifier id) {
        return id != null ? id.toString() : "";
    }

    private static String string(Object value) {
        return value instanceof String s ? s : "";
    }

    private static int intValue(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
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
                panel.variant.id,
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

    record Variant(String id,
                   String title,
                   String headerIcon,
                   float minWidth,
                   float countLabelOffset,
                   float countValueOffset,
                   float rowIconSize,
                   float rowDividerH,
                   float rowRightPad,
                   float rowCenterOffset,
                   String headerIconFont,
                   float headerIconScale) {
        Variant(String id,
                String title,
                String headerIcon,
                float minWidth,
                float countLabelOffset,
                float countValueOffset,
                float rowIconSize,
                float rowDividerH,
                float rowRightPad,
                float rowCenterOffset) {
            this(id, title, headerIcon, minWidth, countLabelOffset, countValueOffset,
                    rowIconSize, rowDividerH, rowRightPad, rowCenterOffset, "IconsNur", 1.0f);
        }
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
                   int blurTint) {
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
            return out;
        }
    }

    record Panel(Variant variant, Palette palette, float x, float y, float width, float height, float drawScale,
                 float baseScale, float fontScale, float headerIconHeight, float headerTextHeight, float rowTextHeight,
                 float countLabelWidth, float countValueWidth,
                 int activeCount, boolean blur, float blurAlpha, int headerIconColor,
                 boolean headerIconGradient, int headerIconGradientStart, int headerIconGradientEnd, float headerIconGradientAngle,
                 String layout, boolean strokeEnabled, float strokeAlpha, boolean strokeGradient,
                 int strokeStartColor, int strokeEndColor, boolean shadowControlled,
                 List<LinkedHashMap<String, Object>> rows) {
        Panel(Variant variant,
              Palette palette,
              float x,
              float y,
              float width,
              float height,
              float drawScale,
              float baseScale,
              float fontScale,
              float headerIconHeight,
              float headerTextHeight,
              float rowTextHeight,
              float countLabelWidth,
              float countValueWidth,
              int activeCount,
              boolean blur,
              float blurAlpha,
              int headerIconColor,
              boolean headerIconGradient,
              int headerIconGradientStart,
              int headerIconGradientEnd,
              float headerIconGradientAngle,
              String layout,
              boolean strokeEnabled,
              float strokeAlpha,
              boolean strokeGradient,
              int strokeStartColor,
              int strokeEndColor,
              boolean shadowControlled,
              List<LinkedHashMap<String, Object>> rows) {
            this.variant = variant;
            this.palette = palette;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.drawScale = drawScale;
            this.baseScale = baseScale;
            this.fontScale = fontScale;
            this.headerIconHeight = headerIconHeight;
            this.headerTextHeight = headerTextHeight;
            this.rowTextHeight = rowTextHeight;
            this.countLabelWidth = countLabelWidth;
            this.countValueWidth = countValueWidth;
            this.activeCount = activeCount;
            this.blur = blur;
            this.blurAlpha = blurAlpha;
            this.headerIconColor = headerIconColor;
            this.headerIconGradient = headerIconGradient;
            this.headerIconGradientStart = headerIconGradientStart;
            this.headerIconGradientEnd = headerIconGradientEnd;
            this.headerIconGradientAngle = headerIconGradientAngle;
            this.layout = layout != null ? layout : HudPanelLayoutModes.SPLIT_HEADER;
            this.strokeEnabled = strokeEnabled;
            this.strokeAlpha = Math.max(0.0f, Math.min(1.0f, strokeAlpha));
            this.strokeGradient = strokeGradient;
            this.strokeStartColor = strokeStartColor;
            this.strokeEndColor = strokeEndColor;
            this.shadowControlled = shadowControlled;
            this.rows = rows != null ? rows : List.of();
        }

        LinkedHashMap<String, Object> toProps() {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("id", variant.id);
            out.put("title", variant.title);
            out.put("headerIcon", variant.headerIcon);
            out.put("width", width);
            out.put("height", height);
            out.put("drawScale", drawScale);
            out.put("baseScale", baseScale);
            out.put("fontScale", fontScale);
            out.put("headerIconHeight", headerIconHeight);
            out.put("headerTextHeight", headerTextHeight);
            out.put("rowTextHeight", rowTextHeight);
            out.put("countLabelWidth", countLabelWidth);
            out.put("countValueWidth", countValueWidth);
            out.put("activeCount", activeCount);
            out.put("blur", blur);
            out.put("blurAlpha", blurAlpha);
            out.put("headerIconColor", hex(headerIconColor));
            out.put("headerIconGradient", headerIconGradient);
            out.put("headerIconGradientStart", hex(headerIconGradientStart));
            out.put("headerIconGradientEnd", hex(headerIconGradientEnd));
            out.put("headerIconGradientAngle", headerIconGradientAngle);
            out.put("layout", layout);
            out.put("strokeEnabled", strokeEnabled);
            out.put("strokeAlpha", strokeAlpha);
            out.put("strokeGradient", strokeGradient);
            out.put("strokeStartColor", hex(strokeStartColor));
            out.put("strokeEndColor", hex(strokeEndColor));
            out.put("shadowControlled", shadowControlled);
            out.put("palette", palette.toProps());
            out.put("rows", rows.toArray());
            out.put("variant", variantProps());
            return out;
        }

        private Map<String, Object> variantProps() {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("minWidth", variant.minWidth);
            out.put("countLabelOffset", variant.countLabelOffset);
            out.put("countValueOffset", variant.countValueOffset);
            out.put("rowIconSize", variant.rowIconSize);
            out.put("rowDividerH", variant.rowDividerH);
            out.put("rowRightPad", variant.rowRightPad);
            out.put("rowCenterOffset", variant.rowCenterOffset);
            out.put("headerIconFont", variant.headerIconFont);
            out.put("headerIconScale", variant.headerIconScale);
            out.put("timePillHeight", TIME_PILL_HEIGHT);
            out.put("timePillLeftPad", TIME_PILL_LEFT_PAD);
            out.put("timePillRingBox", TIME_PILL_RING_BOX);
            out.put("timePillRingTextGap", TIME_PILL_RING_TEXT_GAP);
            out.put("timePillRightPad", TIME_PILL_RIGHT_PAD);
            return out;
        }

        long treeSignature() {
            long h = 0xcbf29ce484222325L;
            h = CachedUiScriptRuntime.mix(h, variant.id);
            h = mixVariant(h);
            h = mixPalette(h);
            h = CachedUiScriptRuntime.mix(h, width);
            h = CachedUiScriptRuntime.mix(h, height);
            h = CachedUiScriptRuntime.mix(h, drawScale);
            h = CachedUiScriptRuntime.mix(h, baseScale);
            h = CachedUiScriptRuntime.mix(h, fontScale);
            h = CachedUiScriptRuntime.mix(h, headerIconHeight);
            h = CachedUiScriptRuntime.mix(h, headerTextHeight);
            h = CachedUiScriptRuntime.mix(h, rowTextHeight);
            h = CachedUiScriptRuntime.mix(h, countLabelWidth);
            h = CachedUiScriptRuntime.mix(h, countValueWidth);
            h = CachedUiScriptRuntime.mix(h, blur);
            h = CachedUiScriptRuntime.mix(h, blurAlpha);
            h = CachedUiScriptRuntime.mix(h, headerIconGradient);
            h = CachedUiScriptRuntime.mix(h, headerIconGradientAngle);
            h = CachedUiScriptRuntime.mix(h, layout);
            h = CachedUiScriptRuntime.mix(h, strokeEnabled);
            h = CachedUiScriptRuntime.mix(h, strokeAlpha);
            h = CachedUiScriptRuntime.mix(h, strokeGradient);
            h = CachedUiScriptRuntime.mix(h, strokeStartColor);
            h = CachedUiScriptRuntime.mix(h, strokeEndColor);
            h = CachedUiScriptRuntime.mix(h, shadowControlled);
            h = CachedUiScriptRuntime.mix(h, String.valueOf(Math.max(0, activeCount)).length());
            h = CachedUiScriptRuntime.mix(h, activeCount);
            h = CachedUiScriptRuntime.mix(h, rows.size());
            for (LinkedHashMap<String, Object> row : rows) {
                String key = string(row.get("key"));
                h = CachedUiScriptRuntime.mix(h, key);
                h = CachedUiScriptRuntime.mix(h, string(row.get("iconKind")));
                h = CachedUiScriptRuntime.mix(h, string(row.get("rightMode")));
                h = CachedUiScriptRuntime.mix(h, string(row.get("rightText")).length());
                h = CachedUiScriptRuntime.mix(h, Math.round(floatValue(row.get("layoutProgress")) * 100.0f));
                h = CachedUiScriptRuntime.mix(h, Math.round(floatValue(row.get("rightTextWidth")) * 100.0f));
                Object partsValue = row.get("nameParts");
                Object[] parts = partsValue instanceof Object[] arr ? arr : new Object[0];
                h = CachedUiScriptRuntime.mix(h, parts.length);
                for (Object partValue : parts) {
                    if (!(partValue instanceof Map<?, ?> part)) continue;
                    h = CachedUiScriptRuntime.mix(h, string(part.get("text")).length());
                    h = CachedUiScriptRuntime.mix(h, floatValue(part.get("x")));
                }
            }
            return h;
        }

        long dataSignature() {
            long h = 0xcbf29ce484222325L;
            h = CachedUiScriptRuntime.mix(h, activeCount);
            h = CachedUiScriptRuntime.mix(h, headerIconColor);
            h = CachedUiScriptRuntime.mix(h, headerIconGradientStart);
            h = CachedUiScriptRuntime.mix(h, headerIconGradientEnd);
            for (LinkedHashMap<String, Object> row : rows) {
                h = CachedUiScriptRuntime.mix(h, string(row.get("key")));
                h = CachedUiScriptRuntime.mix(h, string(row.get("icon")));
                h = CachedUiScriptRuntime.mix(h, string(row.get("iconTint")));
                h = CachedUiScriptRuntime.mix(h, string(row.get("rightText")));
                h = CachedUiScriptRuntime.mix(h, string(row.get("rightColor")));
                h = CachedUiScriptRuntime.mix(h, string(row.get("dividerColor")));
                if ("time-pill".equals(string(row.get("rightMode")))) {
                    h = CachedUiScriptRuntime.mix(h, floatValue(row.get("timeArcStart")));
                    h = CachedUiScriptRuntime.mix(h, floatValue(row.get("timeArcEnd")));
                    h = CachedUiScriptRuntime.mix(h, string(row.get("timeFillStart")));
                    h = CachedUiScriptRuntime.mix(h, string(row.get("timeFillEnd")));
                    h = CachedUiScriptRuntime.mix(h, string(row.get("timeStrokeStart")));
                    h = CachedUiScriptRuntime.mix(h, string(row.get("timeStrokeEnd")));
                    h = CachedUiScriptRuntime.mix(h, string(row.get("timeArcBase")));
                    h = CachedUiScriptRuntime.mix(h, string(row.get("timeArcStartColor")));
                    h = CachedUiScriptRuntime.mix(h, string(row.get("timeArcEndColor")));
                }
                Object partsValue = row.get("nameParts");
                Object[] parts = partsValue instanceof Object[] arr ? arr : new Object[0];
                for (Object partValue : parts) {
                    if (!(partValue instanceof Map<?, ?> part)) continue;
                    h = CachedUiScriptRuntime.mix(h, string(part.get("text")));
                    h = CachedUiScriptRuntime.mix(h, string(part.get("color")));
                }
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
            putPatch(patches, "header:icon", "color", hex(headerIconColor));
            putPatch(patches, "header:icon", "gradientStartColor", hex(headerIconGradientStart));
            putPatch(patches, "header:icon", "gradientEndColor", hex(headerIconGradientEnd));
            for (LinkedHashMap<String, Object> row : rows) {
                String key = string(row.get("key"));
                String rowPrefix = "row:" + key;
                String iconKind = string(row.get("iconKind"));
                String icon = string(row.get("icon"));
                String iconTint = string(row.get("iconTint"));
                if ("item".equals(iconKind)) {
                    putPatch(patches, rowPrefix + ":item", "item", icon);
                } else if ("head".equals(iconKind) && !icon.isEmpty()) {
                    putPatch(patches, rowPrefix + ":head", "asset", icon);
                    putPatch(patches, rowPrefix + ":head", "tint", iconTint);
                } else if ("texture".equals(iconKind) && !icon.isEmpty()) {
                    putPatch(patches, rowPrefix + ":texture", "asset", icon);
                    putPatch(patches, rowPrefix + ":texture", "tint", iconTint);
                } else if ("svg".equals(iconKind) && !icon.isEmpty()) {
                    putPatch(patches, rowPrefix + ":svg", "asset", icon);
                    putPatch(patches, rowPrefix + ":svg", "tint", iconTint);
                }
                putPatch(patches, rowPrefix + ":divider", "fill", string(row.get("dividerColor")));
                if ("time-pill".equals(string(row.get("rightMode")))) {
                    putPatch(patches, rowPrefix + ":time-pill", "startColor", string(row.get("timeFillStart")));
                    putPatch(patches, rowPrefix + ":time-pill", "endColor", string(row.get("timeFillEnd")));
                    putPatch(patches, rowPrefix + ":time-pill", "stroke", string(row.get("timeStrokeStart")));
                    putPatch(patches, rowPrefix + ":time-pill", "strokeStartColor", string(row.get("timeStrokeStart")));
                    putPatch(patches, rowPrefix + ":time-pill", "strokeEndColor", string(row.get("timeStrokeEnd")));
                    putPatch(patches, rowPrefix + ":time-arc-base", "stroke", string(row.get("timeArcBase")));
                    putPatch(patches, rowPrefix + ":time-arc", "startAngle", floatValue(row.get("timeArcStart")));
                    putPatch(patches, rowPrefix + ":time-arc", "endAngle", floatValue(row.get("timeArcEnd")));
                    putPatch(patches, rowPrefix + ":time-arc", "stroke", string(row.get("timeArcStartColor")));
                    putPatch(patches, rowPrefix + ":time-arc", "startColor", string(row.get("timeArcStartColor")));
                    putPatch(patches, rowPrefix + ":time-arc", "endColor", string(row.get("timeArcEndColor")));
                    putPatch(patches, rowPrefix + ":time-text", "text", string(row.get("rightText")));
                    putPatch(patches, rowPrefix + ":time-text", "color", string(row.get("rightColor")));
                } else {
                    putPatch(patches, rowPrefix + ":right", "text", string(row.get("rightText")));
                    putPatch(patches, rowPrefix + ":right", "color", string(row.get("rightColor")));
                }
                Object partsValue = row.get("nameParts");
                Object[] parts = partsValue instanceof Object[] arr ? arr : new Object[0];
                for (int i = 0; i < parts.length; i++) {
                    if (!(parts[i] instanceof Map<?, ?> part)) continue;
                    String partKey = rowPrefix + ":name:" + i;
                    putPatch(patches, partKey, "text", string(part.get("text")));
                    putPatch(patches, partKey, "color", string(part.get("color")));
                }
            }
            return patches;
        }

        private long mixVariant(long h) {
            h = CachedUiScriptRuntime.mix(h, variant.title);
            h = CachedUiScriptRuntime.mix(h, variant.headerIcon);
            h = CachedUiScriptRuntime.mix(h, variant.minWidth);
            h = CachedUiScriptRuntime.mix(h, variant.countLabelOffset);
            h = CachedUiScriptRuntime.mix(h, variant.countValueOffset);
            h = CachedUiScriptRuntime.mix(h, variant.rowIconSize);
            h = CachedUiScriptRuntime.mix(h, variant.rowDividerH);
            h = CachedUiScriptRuntime.mix(h, variant.rowRightPad);
            h = CachedUiScriptRuntime.mix(h, variant.rowCenterOffset);
            h = CachedUiScriptRuntime.mix(h, variant.headerIconFont);
            h = CachedUiScriptRuntime.mix(h, variant.headerIconScale);
            return h;
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
            return h;
        }
    }
}
