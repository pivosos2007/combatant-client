/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

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
import combatant.client.features.module.ModuleManager;
import combatant.client.render.engine.animation.AnimatedRenderColors;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
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
            new EnumValue<>("module_list_bg_effect", BgEffect.BLUR, BgEffect.NONE, BgEffect.BLUR, BgEffect.GLASS);
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
    private float renderX;
    private float renderY;
    private int cachedModulesHash;
    private int cachedModulesCount;
    private float cachedTextScale = -1f;
    public ModuleList() {
        super("module_list", "ModuleList", false);
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
        defs.add(SettingDef.number(blurAlpha).visibleWhen(this::hasEffect));
        defs.add(SettingDef.color(bgTopColor).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.color(bgBottomColor).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(textTopColor).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(textBottomColor).visibleWhen(this::isCustomMode));
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

        TextRenderer listRenderer = Fonts.renderer("Comfortaa", FontInfo.Type.Regular, TextRenderer.get());
        if (listRenderer == null) {
            listRenderer = textRenderer;
        }

        float baseScale = HudScale.scale(screenW, screenH) * scaleValue.get().floatValue();
        float lineH = BASE_LINE_H * baseScale;
        float textOffsetY = BASE_TEXT_Y * baseScale;
        float textOffsetX = BASE_TEXT_X * baseScale;
        float textOffsetXRev = BASE_TEXT_X_REV * baseScale;
        float stringPad = BASE_STRING_PAD * baseScale;
        float rectExtra = BASE_RECT_EXTRA * baseScale;
        float radius = BASE_RADIUS * baseScale;
        float softness = BASE_SOFTNESS * baseScale;

        float fontHeight = Math.max(1.0f, (float) listRenderer.getHeight(false));
        float targetTextHeight = Math.max(4.4f * baseScale, lineH - 4.2f * baseScale);
        float textScale = Math.max(0.2f, targetTextHeight / fontHeight);

        enabledModules.clear();
        int modulesHash = 1;
        for (Module module : ModuleManager.getModules()) {
            if (module != null && module.isEnabled() && module.isShownInModuleList()) {
                enabledModules.add(module);
                String label = module.getDisplayName();
                modulesHash = 31 * modulesHash + System.identityHashCode(module);
                modulesHash = 31 * modulesHash + (label != null ? label.hashCode() : 0);
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
                || Math.abs(textScale - cachedTextScale) > 0.0001f;

        if (layoutChanged) {
            rows.clear();
            listRenderer.begin(textScale, false, false);
            for (Module module : enabledModules) {
                Row row = new Row(module, module.getDisplayName());
                row.textWidth = (float) listRenderer.getWidth(row.label, false);
                rows.add(row);
            }
            listRenderer.end();
            rows.sort(Comparator.comparingDouble((Row row) -> row.textWidth).reversed());
            cachedModulesHash = modulesHash;
            cachedModulesCount = enabledModules.size();
            cachedTextScale = textScale;
        }

        float maxWidth = 0f;
        for (Row row : rows) {
            maxWidth = Math.max(maxWidth, row.textWidth + stringPad + rectExtra);
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
        boolean glass = isGlassEffect();
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
                        composite.roundedRect(row.x, row.y, row.w, lineH, radius, blurQuality, 1.0f, blurAlpha.get() / 255f, tint);
                    }
                });
            } else if (glass) {
                for (int i = 0; i < rows.size(); i++) {
                    Row row = rows.get(i);
                    drawGlass(row.x, row.y, row.w, lineH, radius);
                }
            }

            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (glass) {
                    int glassTop = HudRenderUtil.scaleAlpha(row.fillTop, 0.22f);
                    int glassBottom = HudRenderUtil.scaleAlpha(row.fillBottom, 0.18f);
                    renderer.roundedRectGradientQuad(row.x, row.y, row.w, lineH, radius, softness,
                            glassTop, glassTop, glassBottom, glassBottom);
                } else {
                    int drawTop = blur ? HudRenderUtil.scaleAlpha(row.fillTop, 0.42f) : row.fillTop;
                    int drawBottom = blur ? HudRenderUtil.scaleAlpha(row.fillBottom, 0.42f) : row.fillBottom;
                    renderer.roundedRectGradientQuad(row.x, row.y, row.w, lineH, radius, softness,
                            drawTop, drawTop, drawBottom, drawBottom);
                }
            }
        } finally {
            if (autoBatch) renderer.render();
        }

        int gradientTop = resolveTextGradientTop();
        int gradientBottom = resolveTextGradientBottom();
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
        }
        listRenderer.end();
    }

    @Override
    public boolean contains(float mx, float my) {
        return mx >= renderX && mx <= renderX + width
                && my >= renderY && my <= renderY + height;
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

    private void drawBlur(float x, float y, float w, float h, float radius, int tintArgb, float quality) {
        float alpha = blurAlpha.get() / 255f;
        Renderer2D.COLOR.blurRect(x, y, w, h, radius, quality, 1.0f, alpha, 0xFFFFFF);
    }

    private void drawGlass(float x, float y, float w, float h, float radius) {
        float blurStrength = blurAlpha.get() / 255f;
        if (blurStrength <= 0.001f) return;
        Renderer2D.COLOR.liquidGlassRect(
                x, y, w, h, radius,
                0xFFFFFFFF,
                1.0f,
                blurStrength,
                Renderer2D.LiquidGlassPreset.BALANCED
        );
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

    private boolean isGlassEffect() {
        return bgEffect.get() == BgEffect.GLASS;
    }

    private boolean hasEffect() {
        return bgEffect.get() != BgEffect.NONE;
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

    private enum BgEffect implements EnumValue.IdProvider {
        NONE("None"),
        BLUR("Blur"),
        GLASS("Glass");

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
        private final Module module;
        private final String label;
        private float textWidth;
        private float x;
        private float y;
        private float w;
        private int fillTop;
        private int fillBottom;

        private Row(Module module, String label) {
            this.module = module;
            this.label = label;
        }
    }
}
