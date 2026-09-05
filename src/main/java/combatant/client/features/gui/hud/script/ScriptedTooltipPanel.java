/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.script;

import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.RuntimeTextLayout;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.resources.asset.UiScriptAsset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared bridge for the scripted tooltip visual used by both vanilla-style HUD tooltips
 * and the item visual preview screen. Layout and appearance live in tooltip_panel.js;
 * this class only supplies data, performs the intrinsic measurement pass and renders
 * the baked tree in the caller's current projection.
 */
@UiScriptAsset("combatant:modules/hud/static/tooltip_panel")
public final class ScriptedTooltipPanel {
    private static final float MEASURE_EXTRA_WIDTH = 512.0f;
    private static final float MEASURE_HEIGHT = 4096.0f;
    private static final float TOOLTIP_FONT_SCALE_RATIO = 14.5f / 18.0f;

    private final String runtimeKey;
    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(ScriptedTooltipPanel.class);
    private final CachedUiScriptRuntime runtime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());

    public ScriptedTooltipPanel(String runtimeKey) {
        this.runtimeKey = runtimeKey == null || runtimeKey.isBlank() ? "tooltip" : runtimeKey;
    }

    public Prepared prepare(Minecraft mc,
                            TextRenderer fallbackText,
                            List<Line> lines,
                            float scale,
                            float rasterDetailScale,
                            float maxContentWidth,
                            float footerWidth,
                            float footerHeight,
                            float alpha,
                            Style style,
                            Context context) {
        if (mc == null || fallbackText == null) return null;
        HudScriptLayouts.pollReloadCombo(mc);
        if (moduleHandle.consumeChanged()) runtime.reset();
        if (moduleHandle.isRuntimeBlocked()) return null;
        UiScriptModule module = ensureModule(mc);
        if (module == null) return null;

        TextRenderer layoutText = Fonts.renderer("Iosevka", FontInfo.Type.Regular, fallbackText);
        PreparedLines preparedLines = prepareLines(
                layoutText,
                lines,
                Math.max(0.05f, scale),
                Math.max(1.0f, maxContentWidth)
        );
        List<LinkedHashMap<String, Object>> lineProps = preparedLines.lines();
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        Style resolvedStyle = style != null ? style : Style.DEFAULT;
        Context resolvedContext = context != null ? context : Context.GENERIC;
        LinkedHashMap<String, Object> base = baseProps(
                lineProps,
                Math.max(0.05f, scale),
                Math.max(0.01f, rasterDetailScale),
                Math.max(1.0f, maxContentWidth),
                preparedLines.contentWidthFloor(),
                Math.max(0.0f, footerWidth),
                Math.max(0.0f, footerHeight),
                Math.max(0.0f, Math.min(1.0f, alpha)),
                palette,
                resolvedStyle,
                resolvedContext
        );

        LinkedHashMap<String, Object> measureProps = new LinkedHashMap<>(base);
        measureProps.put("phase", "measure");
        long measureSignature = CachedUiScriptRuntime.signature(measureProps);
        float measureW = Math.max(1.0f, maxContentWidth + MEASURE_EXTRA_WIDTH);
        UiRuntime measured = runtime.bake(
                moduleHandle,
                module,
                runtimeKey + ":measure",
                measureSignature,
                measureSignature,
                measureW,
                MEASURE_HEIGHT,
                fallbackText,
                0.0f,
                0.0f,
                measureW,
                MEASURE_HEIGHT,
                () -> measureProps
        );
        if (measured == null || measured.root() == null) return null;

        float width = Math.max(1.0f, measured.root().measuredWidth());
        float height = Math.max(1.0f, measured.root().measuredHeight());
        UiNode firstLine = findByKey(measured.root(), "line:0");
        float lineHeight = firstLine != null
                ? Math.max(1.0f, firstLine.measuredHeight())
                : Math.max(1.0f, 14.5f * scale);

        return new Prepared(
                List.copyOf(lineProps),
                Math.max(0.05f, scale),
                Math.max(0.01f, rasterDetailScale),
                Math.max(1.0f, maxContentWidth),
                preparedLines.contentWidthFloor(),
                Math.max(0.0f, footerWidth),
                Math.max(0.0f, footerHeight),
                Math.max(0.0f, Math.min(1.0f, alpha)),
                paletteProps(palette),
                resolvedStyle,
                resolvedContext,
                width,
                height,
                lineHeight
        );
    }

    public Rendered render(Minecraft mc,
                           Prepared prepared,
                           Renderer2D renderer,
                           TextRenderer fallbackText,
                           GuiGraphicsExtractor drawContext,
                           float tickDelta,
                           float x,
                           float y,
                           UiProjectionMode projectionMode) {
        if (mc == null || prepared == null || renderer == null || fallbackText == null) {
            return null;
        }
        HudScriptLayouts.pollReloadCombo(mc);
        if (moduleHandle.consumeChanged()) runtime.reset();
        if (moduleHandle.isRuntimeBlocked()) return null;
        UiScriptModule module = ensureModule(mc);
        if (module == null) return null;

        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("phase", "render");
        props.put("lines", prepared.lines());
        props.put("scale", prepared.scale());
        props.put("rasterDetailScale", prepared.rasterDetailScale());
        props.put("maxContentWidth", prepared.maxContentWidth());
        props.put("contentWidthFloor", prepared.contentWidthFloor());
        props.put("footerWidth", prepared.footerWidth());
        props.put("footerHeight", prepared.footerHeight());
        props.put("alpha", prepared.alpha());
        putStyleProps(props, prepared.style());
        props.put("context", prepared.context().id());
        props.putAll(prepared.palette());
        props.put("width", prepared.width());
        props.put("height", prepared.height());
        props.put("lineHeight", prepared.lineHeight());

        long treeSignature = CachedUiScriptRuntime.signature(props);
        long layoutSignature = CachedUiScriptRuntime.mix(
                CachedUiScriptRuntime.mix(
                        CachedUiScriptRuntime.mix(treeSignature, x),
                        y
                ),
                prepared.width() * 31.0f + prepared.height()
        );
        UiRuntime baked = runtime.bake(
                moduleHandle,
                module,
                runtimeKey + ":render",
                treeSignature,
                layoutSignature,
                prepared.width(),
                prepared.height(),
                fallbackText,
                x,
                y,
                prepared.width(),
                prepared.height(),
                () -> props
        );
        if (baked == null) return null;

        baked.render(new UiRenderContext(
                renderer,
                fallbackText,
                drawContext,
                tickDelta,
                projectionMode != null ? projectionMode : UiProjectionMode.CURRENT,
                1.0f
        ));
        UiNode footer = findByKey(baked.root(), "footer");
        return new Rendered(
                new UiBounds(x, y, prepared.width(), prepared.height()),
                footer != null ? footer.bounds() : UiBounds.ZERO
        );
    }

    public void reset() {
        runtime.reset();
    }

    private UiScriptModule ensureModule(Minecraft mc) {
        if (mc == null || mc.getResourceManager() == null) return null;
        if (!moduleHandle.ensureLoaded(mc.getResourceManager())) {
            HudScriptLayouts.reportLoadError(moduleHandle);
            return null;
        }
        moduleHandle.consumeChanged();
        return moduleHandle.module();
    }

    private static LinkedHashMap<String, Object> baseProps(List<LinkedHashMap<String, Object>> lines,
                                                            float scale,
                                                            float rasterDetailScale,
                                                            float maxContentWidth,
                                                            float contentWidthFloor,
                                                            float footerWidth,
                                                            float footerHeight,
                                                            float alpha,
                                                            SettingsGuiPalette palette,
                                                            Style style,
                                                            Context context) {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("lines", lines);
        props.put("scale", scale);
        props.put("rasterDetailScale", Math.max(0.01f, rasterDetailScale));
        props.put("maxContentWidth", maxContentWidth);
        props.put("contentWidthFloor", Math.max(0.0f, Math.min(maxContentWidth, contentWidthFloor)));
        props.put("footerWidth", footerWidth);
        props.put("footerHeight", footerHeight);
        props.put("alpha", alpha);
        putStyleProps(props, style);
        props.put("context", (context != null ? context : Context.GENERIC).id());
        props.putAll(paletteProps(palette));
        return props;
    }

    private static void putStyleProps(Map<String, Object> props, Style style) {
        Style resolved = style != null ? style : Style.DEFAULT;
        props.put("backgroundAlpha", resolved.backgroundAlpha());
        props.put("themeGradientStrength", resolved.themeGradientStrength());
        props.put("strokeAlpha", resolved.strokeAlpha());
        props.put("shadowAlpha", resolved.shadowAlpha());
        props.put("headerAlpha", resolved.headerAlpha());
        props.put("dividerAlpha", resolved.dividerAlpha());
        props.put("gradientAngleOffset", resolved.gradientAngleOffset());
    }

    private static LinkedHashMap<String, Object> paletteProps(SettingsGuiPalette palette) {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("panelBgLeft", hex(palette.panelBgLeft()));
        props.put("panelBgRight", hex(palette.panelBgRight()));
        props.put("moduleCardTop", hex(palette.moduleCardTop()));
        props.put("moduleCardTopStrong", hex(palette.moduleCardTopStrong()));
        props.put("moduleCardBottom", hex(palette.moduleCardBottom()));
        props.put("moduleCardBottomStrong", hex(palette.moduleCardBottomStrong()));
        props.put("menuCategorySelectedLeft", hex(palette.menuCategorySelectedLeft()));
        props.put("menuCategorySelectedRight", hex(palette.menuCategorySelectedRight()));
        props.put("panelStroke", hex(palette.panelStroke()));
        props.put("glassEdgeSoft", hex(palette.glassEdgeSoft()));
        props.put("panelDivider", hex(palette.panelDivider()));
        props.put("panelText", hex(palette.panelText()));
        props.put("panelShadow", hex(palette.panelShadow()));

        Themes.GradientSpec panelGradient = Themes.hudPanelGradient();
        props.put("themePanelGradientStart", hex(panelGradient.start()));
        props.put("themePanelGradientEnd", hex(panelGradient.end()));
        props.put("themePanelGradientAngle", panelGradient.angleDeg());
        return props;
    }

    private static PreparedLines prepareLines(TextRenderer renderer,
                                              List<Line> lines,
                                              float scale,
                                              float maxContentWidth) {
        ArrayList<LinkedHashMap<String, Object>> out = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            return new PreparedLines(List.of(), 0.0f);
        }

        float textScale = TOOLTIP_FONT_SCALE_RATIO * Math.max(0.05f, scale);
        float widthLimit = Math.max(1.0f, maxContentWidth);
        boolean started = renderer != null && !renderer.isBuilding();
        if (started) renderer.begin(textScale, true, false);
        try {
            float longest = 0.0f;
            for (Line line : lines) {
                if (line == null) continue;
                String text = normalizeTooltipLine(line.text());
                if (text.isBlank()) continue;
                longest = Math.max(longest, textWidth(renderer, text));
            }

            // The panel first grows to the real required width. Wrapping is only a fallback once
            // the longest logical line exceeds the configured/screen-safe content cap. Keeping
            // this floor prevents a wrapped line from making the card shrink back to the width
            // of its longest individual continuation row.
            float contentWidthFloor = Math.min(longest, widthLimit);
            float wrapWidth = Math.max(1.0f, contentWidthFloor > 0.0f ? contentWidthFloor : widthLimit);

            int logicalGroup = 0;
            for (Line line : lines) {
                if (line == null) continue;
                String text = normalizeTooltipLine(line.text());
                String color = line.color() != 0 ? hex(line.color()) : "";

                if (text.isBlank()) {
                    LinkedHashMap<String, Object> blank = new LinkedHashMap<>(4);
                    blank.put("text", "");
                    blank.put("color", color);
                    blank.put("group", -1);
                    blank.put("continuation", false);
                    out.add(blank);
                    continue;
                }

                List<String> rows = textWidth(renderer, text) <= wrapWidth
                        ? List.of(text)
                        : wrapLine(renderer, text, wrapWidth);
                if (rows.isEmpty()) rows = List.of(text);

                for (int i = 0; i < rows.size(); i++) {
                    LinkedHashMap<String, Object> value = new LinkedHashMap<>(4);
                    value.put("text", rows.get(i));
                    value.put("color", color);
                    value.put("group", logicalGroup);
                    value.put("continuation", i > 0);
                    out.add(value);
                }
                logicalGroup++;
            }
            return new PreparedLines(List.copyOf(out), Math.max(0.0f, contentWidthFloor));
        } finally {
            if (started && renderer.isBuilding()) renderer.end();
        }
    }

    private static String normalizeTooltipLine(String text) {
        return RuntimeTextLayout.singleLine(text != null ? text : "");
    }

    private static float textWidth(TextRenderer renderer, String text) {
        if (renderer == null || text == null || text.isEmpty()) return 0.0f;
        return (float) renderer.getWidth(text, false);
    }

    /**
     * Wrap one logical tooltip line using the exact renderer/scale used by the scripted panel.
     * Whitespace is only consumed at an actual wrap boundary; the original line is otherwise kept
     * intact. Long unbroken tokens fall back to code-point-safe hard wrapping.
     */
    private static List<String> wrapLine(TextRenderer renderer, String text, float maxWidth) {
        ArrayList<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        if (renderer == null || maxWidth <= 1.0f || textWidth(renderer, text) <= maxWidth) {
            out.add(text);
            return out;
        }

        int start = 0;
        while (start < text.length()) {
            String remaining = text.substring(start);
            if (textWidth(renderer, remaining) <= maxWidth) {
                out.add(remaining);
                break;
            }

            int relativeFitEnd = maxFittingCharIndex(renderer, remaining, maxWidth);
            if (relativeFitEnd <= 0) {
                relativeFitEnd = Character.charCount(remaining.codePointAt(0));
            }

            int fitEnd = start + relativeFitEnd;
            int breakStart = lastWhitespaceStart(text, start, fitEnd);
            int rowEnd = breakStart > start ? breakStart : fitEnd;
            if (rowEnd <= start) rowEnd = fitEnd;

            String row = stripTrailingWrapWhitespace(text.substring(start, rowEnd));
            if (row.isEmpty()) {
                row = text.substring(start, fitEnd);
                rowEnd = fitEnd;
            }
            out.add(row);

            start = rowEnd;
            while (start < text.length()) {
                int cp = text.codePointAt(start);
                if (!Character.isWhitespace(cp)) break;
                start += Character.charCount(cp);
            }
        }
        return out;
    }

    private static int maxFittingCharIndex(TextRenderer renderer, String text, float maxWidth) {
        int codePoints = text.codePointCount(0, text.length());
        int low = 0;
        int high = codePoints;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            int end = text.offsetByCodePoints(0, mid);
            if (textWidth(renderer, text.substring(0, end)) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.offsetByCodePoints(0, low);
    }

    private static int lastWhitespaceStart(String text, int minInclusive, int beforeOrAt) {
        int cursor = Math.max(minInclusive, Math.min(beforeOrAt, text.length()));
        while (cursor > minInclusive) {
            int cp = text.codePointBefore(cursor);
            int start = cursor - Character.charCount(cp);
            if (Character.isWhitespace(cp)) return start;
            cursor = start;
        }
        return -1;
    }

    private static String stripTrailingWrapWhitespace(String value) {
        int end = value.length();
        while (end > 0) {
            int cp = value.codePointBefore(end);
            if (!Character.isWhitespace(cp)) break;
            end -= Character.charCount(cp);
        }
        return end == value.length() ? value : value.substring(0, end);
    }

    private record PreparedLines(List<LinkedHashMap<String, Object>> lines, float contentWidthFloor) {
    }

    private static UiNode findByKey(UiNode node, String key) {
        if (node == null || key == null) return null;
        if (key.equals(node.key())) return node;
        for (UiNode child : node.children()) {
            UiNode found = findByKey(child, key);
            if (found != null) return found;
        }
        return null;
    }

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    public record Style(int backgroundAlpha,
                        int themeGradientStrength,
                        int strokeAlpha,
                        int shadowAlpha,
                        int headerAlpha,
                        int dividerAlpha,
                        int gradientAngleOffset) {
        /**
         * Exact legacy ItemVisualPreviewProvider appearance. Theme gradient is opt-in;
         * all alpha controls are layer multipliers; 255 preserves the provider constants.
         */
        public static final Style DEFAULT = new Style(255, 0, 255, 255, 255, 255, 0);

        public Style {
            backgroundAlpha = clamp255(backgroundAlpha);
            themeGradientStrength = Math.max(0, Math.min(250, themeGradientStrength));
            strokeAlpha = clamp255(strokeAlpha);
            shadowAlpha = clamp255(shadowAlpha);
            headerAlpha = clamp255(headerAlpha);
            dividerAlpha = clamp255(dividerAlpha);
            gradientAngleOffset = Math.max(-180, Math.min(180, gradientAngleOffset));
        }

        private static int clamp255(int value) {
            return Math.max(0, Math.min(255, value));
        }
    }

    public enum Context {
        GENERIC("generic"),
        ITEM("item");

        private final String id;

        Context(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record Line(String text, int color) {
    }

    public record Prepared(List<LinkedHashMap<String, Object>> lines,
                           float scale,
                           float rasterDetailScale,
                           float maxContentWidth,
                           float contentWidthFloor,
                           float footerWidth,
                           float footerHeight,
                           float alpha,
                           Map<String, Object> palette,
                           Style style,
                           Context context,
                           float width,
                           float height,
                           float lineHeight) {
        public Prepared {
            lines = List.copyOf(lines);
            palette = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(palette));
        }
    }

    public record Rendered(UiBounds panelBounds, UiBounds footerBounds) {
    }
}
