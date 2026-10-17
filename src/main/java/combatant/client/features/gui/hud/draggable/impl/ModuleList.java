/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.render.engine.text.BuiltinFontCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import combatant.client.config.ConfigValueAliasProvider;
import combatant.client.config.SettingDef;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.config.values.RGBColorValue;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleListInfoResolver;
import combatant.client.features.module.ModuleManager;
import combatant.client.render.engine.animation.AnimatedRenderColors;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 10)
public final class ModuleList extends DraggableHudElement implements ConfigValueAliasProvider {

    private static final float BASE_LINE_H = 9f;
    private static final float BASE_TEXT_Y = 3f;
    private static final float BASE_TEXT_X = 3f;
    private static final float BASE_TEXT_X_REV = 2f;
    private static final float BASE_STRING_PAD = 3f;
    private static final float BASE_DETAIL_GAP = 3.1f;
    private static final float BASE_BRACKET_INSET = 0.85f;
    private static final float BASE_RECT_EXTRA = 1f;
    private static final float BASE_RADIUS = 2f;
    private static final float BASE_SOFTNESS = 0.55f;
    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final NumberValue<Double> scaleValue =
            new NumberValue<>("module_list_scale", 3.67, 0.5, 5.0);
    private final EnumValue<ColorMode> colorMode =
            new EnumValue<>("module_list_color_mode", ColorMode.CUSTOM, ColorMode.THEME, ColorMode.CUSTOM);
    private final EnumValue<BgEffect> bgEffect =
            new EnumValue<>("module_list_bg_effect", BgEffect.BLUR, BgEffect.NONE, BgEffect.BLUR);
    private final NumberValue<Integer> bgAlpha =
            new NumberValue<>("module_list_bg_alpha", 226, 0, 255);
    private final NumberValue<Integer> blurAlpha =
            new NumberValue<>("module_list_blur_alpha", 255, 0, 255);
    private final RGBAColorValue bgTopColor =
            new RGBAColorValue("module_list_bg", "#FF444242");
    private final RGBAColorValue bgBottomColor =
            new RGBAColorValue("module_list_bg_secondary", "#22000000");
    private final RGBColorValue textTopColor =
            new RGBColorValue("module_list_text", "RNB#E87171");
    private final RGBColorValue textBottomColor =
            new RGBColorValue("module_list_text_secondary", "#93118C");
    // Detail colors belong to ModuleList; source modules expose only text.
    private final EnumValue<InfoColorMode> infoColorMode =
            new EnumValue<>("module_list_info_color_mode",
                    InfoColorMode.AUTO, InfoColorMode.AUTO, InfoColorMode.CUSTOM);
    private final RGBColorValue infoTopColor =
            new RGBColorValue("module_list_info_primary", "#E1E8F0");
    private final RGBColorValue infoBottomColor =
            new RGBColorValue("module_list_info_secondary", "#B3C0D0");
    private final EnumValue<AnimatedRenderColors.Mode> textGradientMode =
            new EnumValue<>("module_list_text_gradient_mode",
                    AnimatedRenderColors.Mode.RAINBOW,
                    AnimatedRenderColors.Mode.STATIC,
                    AnimatedRenderColors.Mode.RAINBOW,
                    AnimatedRenderColors.Mode.LIGHT_RAINBOW,
                    AnimatedRenderColors.Mode.SKY,
                    AnimatedRenderColors.Mode.FADE,
                    AnimatedRenderColors.Mode.DOUBLE_COLOR,
                    AnimatedRenderColors.Mode.ANALOGOUS,
                    AnimatedRenderColors.Mode.THEME);
    private final NumberValue<Integer> textGradientSpeed =
            new NumberValue<>("module_list_text_gradient_speed", 18, 2, 54);
    private final EnumValue<TextEffect> textEffect =
            new EnumValue<>("module_list_text_effect", TextEffect.NONE, TextEffect.NONE, TextEffect.SHIMMER);
    private final NumberValue<Integer> textEffectSpeed =
            new NumberValue<>("module_list_text_effect_speed", 30, 1, 60);
    private final List<Row> rows = new ArrayList<>();
    private final List<Module> enabledModules = new ArrayList<>();
    private final List<String> enabledLabels = new ArrayList<>();
    private final List<String> enabledDetails = new ArrayList<>();
    private float renderX;
    private float renderY;
    private int cachedModulesHash;
    private int cachedModulesCount;
    private float cachedTextScale = -1f;
    private int cachedScreenWidth = -1;
    public ModuleList() {
        super("module_list", "ModuleList", "hud.draggable.module_list.description", false);
    }

    @Override
    public List<String> getLegacyValueNames(String currentValueName) {
        if (currentValueName == null) {
            return List.of();
        }
        if (currentValueName.startsWith("module_list_")) {
            return List.of("active_modules_" + currentValueName.substring("module_list_".length()));
        }
        return List.of();
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.number(scaleValue));
        defs.add(SettingDef.mode(colorMode));
        defs.add(SettingDef.mode(bgEffect));
        defs.add(SettingDef.number(bgAlpha).visibleWhen(this::isThemeMode));
        defs.add(SettingDef.number(blurAlpha).visibleWhen(this::isBlurEffect));
        defs.add(SettingDef.color(bgTopColor).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.color(bgBottomColor).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(textTopColor).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(textBottomColor).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.mode(infoColorMode));
        defs.add(SettingDef.colorNoAlpha(infoTopColor).visibleWhen(this::isCustomInfoColors));
        defs.add(SettingDef.colorNoAlpha(infoBottomColor).visibleWhen(this::isCustomInfoColors));
        defs.add(SettingDef.mode(textGradientMode));
        defs.add(SettingDef.number(textGradientSpeed).visibleWhen(this::hasAnimatedTextGradient));
        defs.add(SettingDef.mode(textEffect));
        defs.add(SettingDef.number(textEffectSpeed).visibleWhen(() -> textEffect.get() != TextEffect.NONE));
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        this.x = screenW - 80f;
        this.y = 20f;
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
        if (!isEnabled() || mc == null || mc.player == null) {
            rows.clear();
            width = 0f;
            height = 0f;
            return;
        }

        TextRenderer listRenderer = BuiltinFontCatalog.COMFORTAA_SEMIBOLD.renderer(
                BuiltinFontCatalog.COMFORTAA.renderer(TextRenderer.get()));
        if (listRenderer == null) {
            listRenderer = textRenderer;
        }

        float baseScale = HudScale.scale(screenW, screenH) * scaleValue.get().floatValue();
        float lineH = BASE_LINE_H * baseScale;
        float textOffsetY = BASE_TEXT_Y * baseScale;
        float textOffsetX = BASE_TEXT_X * baseScale;
        float textOffsetXRev = BASE_TEXT_X_REV * baseScale;
        float stringPad = BASE_STRING_PAD * baseScale;
        float detailGap = BASE_DETAIL_GAP * baseScale;
        float bracketInset = BASE_BRACKET_INSET * baseScale;
        float rectExtra = BASE_RECT_EXTRA * baseScale;
        float radius = BASE_RADIUS * baseScale;
        float softness = BASE_SOFTNESS * baseScale;

        float fontHeight = Math.max(1.0f, (float) listRenderer.getHeight(false));
        float targetTextHeight = Math.max(4.4f * baseScale, lineH - 4.2f * baseScale);
        float textScale = Math.max(0.2f, targetTextHeight / fontHeight);

        enabledModules.clear();
        enabledLabels.clear();
        enabledDetails.clear();
        int modulesHash = 1;
        for (Module module : ModuleManager.getModules()) {
            if (module != null && module.isEnabled() && module.isShownInModuleList()) {
                String label = module.getDisplayName();
                String detail = ModuleListInfoResolver.query(module);
                if (detail != null && detail.isEmpty()) detail = null;
                enabledModules.add(module);
                enabledLabels.add(label != null ? label : "");
                enabledDetails.add(detail);
                modulesHash = 31 * modulesHash + System.identityHashCode(module);
                modulesHash = 31 * modulesHash + (label != null ? label.hashCode() : 0);
                modulesHash = 31 * modulesHash + (detail != null ? detail.hashCode() : 0);
            }
        }

        if (enabledModules.isEmpty()) {
            rows.clear();
            width = 0f;
            height = 0f;
            return;
        }

        boolean layoutChanged = modulesHash != cachedModulesHash
                || enabledModules.size() != cachedModulesCount
                || Math.abs(textScale - cachedTextScale) > 0.0001f
                || cachedScreenWidth != screenW;

        if (layoutChanged) {
            rows.clear();
            listRenderer.begin(textScale, false, false);
            float openWidth = (float) listRenderer.getWidth("[", false);
            float closeWidth = (float) listRenderer.getWidth("]", false);
            for (int i = 0; i < enabledModules.size(); i++) {
                String label = enabledLabels.get(i);
                float labelWidth = (float) listRenderer.getWidth(label, false);
                String detail = enabledDetails.get(i);
                float detailWidth = 0f;
                if (detail != null) {
                    float detailBudget = Math.max(0f, screenW - labelWidth - detailGap
                            - openWidth - closeWidth - 2f * bracketInset
                            - stringPad - rectExtra - 5f * baseScale);
                    String fitted = fitDetail(listRenderer, detail, detailBudget);
                    if (fitted.isEmpty()) {
                        detail = null;
                    } else {
                        detail = fitted;
                        detailWidth = (float) listRenderer.getWidth(detail, false);
                    }
                }
                Row row = new Row(label, detail);
                row.labelWidth = labelWidth;
                if (detail != null) {
                    row.detailWidth = detailWidth;
                    row.detailStart = labelWidth + detailGap + openWidth + bracketInset;
                    row.closeBracketStart = row.detailStart + detailWidth + bracketInset;
                    row.textWidth = row.closeBracketStart + closeWidth;
                } else {
                    row.textWidth = labelWidth;
                }
                rows.add(row);
            }
            listRenderer.end();
            rows.sort(Comparator.comparingDouble((Row row) -> row.textWidth).reversed());
            cachedModulesHash = modulesHash;
            cachedModulesCount = enabledModules.size();
            cachedTextScale = textScale;
            cachedScreenWidth = screenW;
        }

        float maxWidth = 0f;
        for (Row row : rows) {
            maxWidth = Math.max(maxWidth, row.textWidth + stringPad + rectExtra);
        }

        // Changing live detail must not move the right edge of a right-docked list.
        if (x > screenW * 0.5f && width > 0f && Math.abs(width - maxWidth) > 0.001f) {
            x += width - maxWidth;
        }
        float maxX = Math.max(0f, screenW - maxWidth);
        if (x > maxX) x = maxX;
        if (x < 0f) x = 0f;

        boolean reverse = x > (screenW * 0.5f);
        float reversedX = x;
        float drawBaseX = reverse ? (reversedX + maxWidth) : x;
        float listHeight = rows.size() * lineH;

        renderX = reverse ? (drawBaseX - maxWidth) : drawBaseX;
        renderY = y;
        width = maxWidth;
        height = listHeight;

        int bgTop = resolveBgTop();
        int bgBottom = resolveBgBottom();
        int textTop = resolveTextTop();
        int textBottom = resolveTextBottom();
        boolean blur = isBlurEffect();
        boolean shimmer = textEffect.get() == TextEffect.SHIMMER;
        float time = (float) (Util.getMillis() / 1000.0);
        float blurQuality = hud != null ? hud.getBlurRadius() : 12.0f;
        boolean autoBatch = !Renderer2D.isBatching();
        if (autoBatch) renderer.begin();
        try {
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                float offsetY = y + i * lineH;
                float stringWidth = row.textWidth + stringPad;
                float rowX = reverse ? (drawBaseX - stringWidth) : drawBaseX;
                float rowW = stringWidth + rectExtra;

                row.x = rowX;
                row.y = offsetY;
                row.w = rowW;
                row.fillTop = rowFill(bgTop, i, rows.size());
                row.fillBottom = rowFill(bgBottom, i, rows.size());
            }

            if (blur) {
                renderer.blurComposite(composite -> {
                    for (int i = 0; i < rows.size(); i++) {
                        Row row = rows.get(i);
                        int tint = HudRenderUtil.mixColor(row.fillTop, row.fillBottom, 0.5f) & 0x00FFFFFF;
                        composite.roundedRect(row.x, row.y, row.w, lineH, radius, blurQuality, 1.0f,
                                blurAlpha.get() / 255f, tint);
                    }
                });
            }

            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                int drawTop = blur ? HudRenderUtil.scaleAlpha(row.fillTop, 0.42f) : row.fillTop;
                int drawBottom = blur ? HudRenderUtil.scaleAlpha(row.fillBottom, 0.42f) : row.fillBottom;
                renderer.roundedRectGradientQuad(row.x, row.y, row.w, lineH, radius, softness,
                        drawTop, drawTop, drawBottom, drawBottom);
            }
        } finally {
            if (autoBatch) renderer.render();
        }

        int gradientTop = resolveTextGradientTop();
        int gradientBottom = resolveTextGradientBottom();
        int infoTop = isCustomInfoColors()
                ? infoTopColor.getArgb()
                : HudRenderUtil.mixColor(gradientTop, 0xFFE1E8F0, 0.73f);
        int infoBottom = isCustomInfoColors()
                ? infoBottomColor.getArgb()
                : HudRenderUtil.mixColor(gradientBottom, 0xFFB3C0D0, 0.72f);
        boolean topRainbow = isCustomMode() && textTopColor.isRainbow();
        boolean bottomRainbow = isCustomMode() && textBottomColor.isRainbow();
        AnimatedRenderColors.Mode gradientMode = textGradientMode.get();
        int gradientSpeed = hasAnimatedTextGradient() ? textGradientSpeed.get() : Math.max(1, textEffectSpeed.get());

        listRenderer.begin(textScale, false, false);
        float textHeight = (float) listRenderer.getHeight(false);
        float blockTop = renderY;
        float blockBottom = renderY + height;
        for (Row row : rows) {
            float textX = reverse ? (drawBaseX - (row.textWidth + stringPad) + textOffsetXRev) : (drawBaseX + textOffsetX);
            float textY = row.y + textOffsetY + (lineH - textHeight - textOffsetY) * 0.08f;
            listRenderer.renderQuadGradient(row.label, textX, textY, (idx, cp, x0, y0, x1, y1, out) ->
                    AnimatedRenderColors.moduleListBlockGlyphGradient(
                            gradientTop,
                            gradientBottom,
                            gradientMode,
                            gradientSpeed,
                            y0,
                            y1,
                            blockTop,
                            blockBottom,
                            idx,
                            time,
                            topRainbow,
                            bottomRainbow,
                            shimmer,
                            out
                    ), false);

            if (row.detail != null) {
                float detailX = textX + row.detailStart;
                float bracketX = textX + row.labelWidth + detailGap;
                float closeX = textX + row.closeBracketStart;
                int bracketColor = HudRenderUtil.mixColor(
                        HudRenderUtil.mixColor(gradientTop, gradientBottom, 0.5f),
                        0xFFA5AFBB, 0.78f);
                listRenderer.render("[", bracketX, textY, new RenderColor(bracketColor), false);

                listRenderer.renderQuadGradient(row.detail, detailX, textY, (idx, cp, x0, y0, x1, y1, out) -> {
                    out[0] = infoTop;
                    out[1] = infoBottom;
                    out[2] = infoBottom;
                    out[3] = infoTop;
                }, false);
                listRenderer.render("]", closeX, textY, new RenderColor(bracketColor), false);
            }
        }
        listRenderer.end();
    }

    @Override
    public boolean contains(float mx, float my) {
        return mx >= renderX && mx <= renderX + width
                && my >= renderY && my <= renderY + height;
    }

    /** Clip untrusted/variable module detail without ever cutting a UTF-16 surrogate pair. */
    private static String fitDetail(TextRenderer renderer, String detail, float availableWidth) {
        if (detail == null || detail.isEmpty()) return "";
        if (renderer.getWidth(detail, false) <= availableWidth) return detail;
        String ellipsis = "…";
        if (renderer.getWidth(ellipsis, false) > availableWidth) return "";
        int count = detail.codePointCount(0, detail.length());
        int low = 0;
        int high = count;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            int end = detail.offsetByCodePoints(0, mid);
            if (renderer.getWidth(detail.substring(0, end) + ellipsis, false) <= availableWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return detail.substring(0, detail.offsetByCodePoints(0, low)) + ellipsis;
    }

    private int resolveBgTop() {
        if (isThemeMode()) {
            int base = HudRenderUtil.mixColor(theme().windowBg(), 0xFF000000, 0.38f);
            return HudRenderUtil.setAlpha(base, bgAlpha.get());
        }
        return bgTopColor.getArgb();
    }

    private int resolveBgBottom() {
        if (isThemeMode()) {
            int base = HudRenderUtil.mixColor(theme().windowHeader(), 0xFF000000, 0.48f);
            return HudRenderUtil.setAlpha(base, bgAlpha.get());
        }
        return bgBottomColor.getArgb();
    }

    private int resolveTextTop() {
        if (isThemeMode()) {
            return HudRenderUtil.mixColor(theme().accentSoft(), theme().accent(), 0.18f);
        }
        return textTopColor.getArgb();
    }

    private int resolveTextBottom() {
        if (isThemeMode()) {
            return theme().accent();
        }
        return textBottomColor.getArgb();
    }

    private int resolveTextGradientTop() {
        if (isThemeMode()) {
            return resolveTextTop();
        }
        return textTopColor.isRainbow() ? parseRgbFallback(textTopColor.rainbowFallbackHex()) : textTopColor.getArgb();
    }

    private int resolveTextGradientBottom() {
        if (isThemeMode()) {
            return resolveTextBottom();
        }
        return textBottomColor.isRainbow() ? parseRgbFallback(textBottomColor.rainbowFallbackHex()) : textBottomColor.getArgb();
    }

    private int parseRgbFallback(String hex) {
        if (hex == null || hex.isBlank()) return 0xFFFFFFFF;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        try {
            int value = (int) Long.parseUnsignedLong(s, 16);
            if (s.length() <= 6) return 0xFF000000 | (value & 0x00FFFFFF);
            return value;
        } catch (NumberFormatException ignored) {
            return 0xFFFFFFFF;
        }
    }

    private int rowFill(int argb, int index, int total) {
        if (!isThemeMode() || total <= 1) {
            return argb;
        }
        float progress = (float) index / (float) (total - 1);
        int alpha = (argb >>> 24) & 0xFF;
        int rgb = HudRenderUtil.mixColor(argb, resolveTextBottom(), 0.03f + 0.07f * progress);
        return (rgb & 0x00FFFFFF) | (alpha << 24);
    }

    private boolean isThemeMode() {
        return colorMode.get() == ColorMode.THEME;
    }

    private boolean isCustomMode() {
        return colorMode.get() == ColorMode.CUSTOM;
    }

    private boolean isBlurEffect() {
        return bgEffect.get() == BgEffect.BLUR;
    }

    private boolean isCustomInfoColors() {
        return infoColorMode.get() == InfoColorMode.CUSTOM;
    }

    private boolean hasAnimatedTextGradient() {
        return textGradientMode.get() != AnimatedRenderColors.Mode.STATIC
                || (isCustomMode() && (textTopColor.isRainbow() || textBottomColor.isRainbow()));
    }

    private enum ColorMode implements EnumValue.IdProvider {
        THEME("Theme"),
        CUSTOM("Custom");

        private final String id;

        ColorMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private enum InfoColorMode implements EnumValue.IdProvider {
        AUTO("Auto"),
        CUSTOM("Custom");

        private final String id;

        InfoColorMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private enum BgEffect implements EnumValue.IdProvider {
        NONE("None"),
        BLUR("Blur");

        private final String id;

        BgEffect(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private enum TextEffect implements EnumValue.IdProvider {
        NONE("None"),
        SHIMMER("Shimmer");

        private final String id;

        TextEffect(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    private static final class Row {
        private final String label;
        private final String detail;
        private float labelWidth;
        private float detailWidth;
        private float detailStart;
        private float closeBracketStart;
        private float textWidth;
        private float x;
        private float y;
        private float w;
        private int fillTop;
        private int fillBottom;

        private Row(String label, String detail) {
            this.label = label;
            this.detail = detail;
        }
    }
}
