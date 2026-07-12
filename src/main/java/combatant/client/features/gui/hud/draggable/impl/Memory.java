/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.values.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import combatant.client.features.gui.hud.HudElementInfo;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.HudTextEffects;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.script.CompactHudStatModel;
import combatant.client.features.gui.hud.script.ScriptableHudStatWidget;
import combatant.client.features.gui.hud.script.ScriptedCompactHudStatRenderer;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;

import static combatant.client.features.theme.Theme.theme;

@HudElementInfo(
        id = "memory",
        displayName = "Memory",
        enabledByDefault = true,
        order = 130
)
public final class Memory extends DraggableHudElement implements ScriptableHudStatWidget {

    private static final Identifier MEMORY_ICON = Identifier.fromNamespaceAndPath("combatant", "textures/hud/elements/memory.png");
    private static final float SCALE_MULT = 0.68f;
    private static final float BOX_HEIGHT = 20f;
    private static final float BOX_RADIUS = 5f;
    private static final float BOX_SOFTNESS = 1.0f;
    private static final float STROKE_WIDTH = 0.55f;
    private static final float ICON_SIZE = 11f;
    private static final float ICON_X = 5f;
    private static final float DIVIDER_X = 21f;
    private static final float DIVIDER_Y = 4f;
    private static final float DIVIDER_WIDTH = 0.6f;
    private static final float TEXT_LEFT_X = 26f;
    private static final float VALUE_UNIT_GAP = 2f;
    private static final float EXTRA_GAP = 5f;
    private static final float RIGHT_PAD = 5f;
    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";
    private static final String EFFECT_GLASS = "Glass";

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final NumberValue<Double> scale = num("memory_scale", 2.37, 0.5, 5.0);
    private final ModeValue colorMode = mode("memory_color_mode", "color_mode", "Theme", new String[]{COLOR_THEME, COLOR_CUSTOM});
    private final RGBColorValue iconColor = visibleWhen(colorNoAlpha("memory_icon_color", "#FFFFFF"), this::isCustomMode);
    private final RGBColorValue valueColor = visibleWhen(colorNoAlpha("memory_value_color", "#FFFFFF"), this::isCustomMode);
    private final RGBColorValue metaColor = visibleWhen(colorNoAlpha("memory_meta_color", "#9B9B9B"), this::isCustomMode);
    private final RGBColorValue extraColor = visibleWhen(colorNoAlpha("memory_extra_color", "#BEBEBE"), this::isCustomMode);
    private final ModeValue bgEffect = mode("memory_bg_effect", "bg_effect", "Blur", new String[]{EFFECT_NONE, EFFECT_BLUR, EFFECT_GLASS});
    private final RGBAColorValue bg = visibleWhen(color("memory_bg", "#F7343434"), () -> isCustomMode() && !isGlassEffect());
    private final RGBAColorValue bg2 = visibleWhen(color("memory_bg_secondary", "#F7161616"), () -> isCustomMode() && !isGlassEffect());
    private final RGBColorValue stroke = visibleWhen(colorNoAlpha("memory_stroke", "#5A5A5A"), () -> isCustomMode() && !isGlassEffect());
    private final NumberValue<Integer> bgAlpha = visibleWhen(num("memory_bg_alpha", 135, 0, 255), () -> isThemeMode() && !isGlassEffect());
    private final NumberValue<Integer> blurAlpha = visibleWhen(num("memory_blur_alpha", 255, 0, 255), this::hasEffect);
    private final EnumValue<HudTextEffects.Effect> labelEffect =
            enumSetting("memory_label_effect", HudTextEffects.Effect.NONE,
                    HudTextEffects.Effect.NONE, HudTextEffects.Effect.MIX, HudTextEffects.Effect.FLOW,
                    HudTextEffects.Effect.PULSE, HudTextEffects.Effect.STRIPE);
    private final NumberValue<Integer> labelEffectSpeed =
            visibleWhen(num("memory_label_effect_speed", 18, 1, 60),
                    () -> labelEffect.get() != HudTextEffects.Effect.NONE);
    private final CompactHudStatModel scriptModel = new CompactHudStatModel("memory");
    private final StringBuilder valueBuilder = new StringBuilder(24);
    private long lastUsedMiB = Long.MIN_VALUE;
    private long lastMaxMiB = Long.MIN_VALUE;
    private int lastPercent = Integer.MIN_VALUE;
    private String cachedValue = "0/0";
    private String cachedExtra = "[0%]";
    private float displayValueWidth = -1f;
    private float displayExtraWidth = -1f;
    private int uiBgPrimary;
    private int uiBgSecondary;
    private int uiStroke;
    private int uiIconColor;
    private int uiValueColor;
    private int uiMetaColor;
    private int uiExtraColor;

    private static long toMiB(long bytes) {
        return bytes / 1024L / 1024L;
    }

    private static float smoothWidth(float current, float target) {
        if (current < 0.0f) return target;
        if (target >= current) return target;
        float next = AnimationUtility.approach(current, target, AnimationUtility.deltaTime(), 12.0f);
        return AnimationUtility.snap(next, target, 0.25f);
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        this.x = 16f;
        this.y = 44f;
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
        boolean preview = DraggableHudElementRegistry.isForceVisible();
        if (mc == null && !preview) {
            width = 0f;
            height = 0f;
            scriptModel.setVisible(false);
            return;
        }
        if (!preview && !isEnabled()) {
            width = 0f;
            height = 0f;
            scriptModel.setVisible(false);
            return;
        }

        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        long total = rt.totalMemory();
        long free = rt.freeMemory();
        long used = Math.max(0L, total - free);
        long usedMiB = toMiB(used);
        long maxMiB = toMiB(max > 0L ? max : total);
        int percent = max > 0L ? (int) Math.round((double) used * 100.0 / (double) max) : 0;
        percent = Math.max(0, Math.min(100, percent));
        updateMemoryStrings(usedMiB, maxMiB, percent);
        updatePalette();

        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        TextRenderer valueRenderer = Fonts.renderer("Onest", FontInfo.Type.Regular, fallback);
        TextRenderer metaRenderer = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, valueRenderer);

        float drawScale = HudScale.scale(screenW, screenH)
                * (hud.getFontSize() / 18f)
                * SCALE_MULT
                * scale.get().floatValue();

        valueRenderer.begin(drawScale, true, false);
        float valueW = (float) valueRenderer.getWidth(cachedValue, false);
        float valueH = (float) valueRenderer.getHeight(false);
        valueRenderer.end();

        metaRenderer.begin(drawScale, true, false);
        float unitW = (float) metaRenderer.getWidth("MB", false);
        float unitH = (float) metaRenderer.getHeight(false);
        float extraW = (float) metaRenderer.getWidth(cachedExtra, false);
        metaRenderer.end();

        float boxH = BOX_HEIGHT * drawScale;
        float iconSize = ICON_SIZE * drawScale;
        float valueX = x + TEXT_LEFT_X * drawScale;
        float unitGap = VALUE_UNIT_GAP * drawScale;
        float extraGap = EXTRA_GAP * drawScale;
        displayValueWidth = smoothWidth(displayValueWidth, valueW);
        displayExtraWidth = smoothWidth(displayExtraWidth, extraW);
        float boxW = (valueX - x)
                + displayValueWidth
                + unitGap
                + unitW
                + extraGap
                + displayExtraWidth
                + RIGHT_PAD * drawScale;
        width = boxW;
        height = boxH;

        float baseX = x;
        float baseY = y;
        float iconX = baseX + ICON_X * drawScale;
        float iconY = baseY + (boxH - iconSize) * 0.5f;
        float dividerX = baseX + DIVIDER_X * drawScale;
        float dividerY = baseY + DIVIDER_Y * drawScale;
        float dividerW = Math.max(0.5f, DIVIDER_WIDTH * drawScale);
        float dividerH = Math.max(0.5f, boxH - 2f * DIVIDER_Y * drawScale);
        float textY = baseY + (boxH - Math.max(valueH, unitH)) * 0.5f;
        float unitX = valueX + displayValueWidth + unitGap;
        float extraX = unitX + unitW + extraGap;

        int resolvedIconColor = uiIconColor;
        boolean useLabelEffect = labelEffect.get() != HudTextEffects.Effect.NONE;
        float time = useLabelEffect ? (float) (Util.getMillis() / 1000.0) : 0.0f;
        if (useLabelEffect) {
            resolvedIconColor = HudTextEffects.animatedColor(resolvedIconColor, labelEffect.get(),
                    labelEffectSpeed.get(), time, 0.0f);
        }

        float radius = BOX_RADIUS * drawScale;
        scriptModel.setVisible(true);
        scriptModel.setRoot(baseX, baseY, boxW, boxH, radius, drawScale);
        scriptModel.background().set(bgEffect.get(), isThemeMode(), blurAlpha.get() / 255.0f,
                uiBgPrimary, uiBgSecondary, uiStroke, Math.max(0.5f, STROKE_WIDTH * drawScale), BOX_SOFTNESS);
        scriptModel.icon().texture(MEMORY_ICON.toString(), iconX - baseX, iconY - baseY, iconSize, iconSize, resolvedIconColor);
        scriptModel.divider().set(dividerX - baseX, dividerY - baseY, dividerW, dividerH,
                HudRenderUtil.setAlpha(uiMetaColor, 0x52));
        scriptModel.value().set(cachedValue, "Onest", drawScale, valueX - baseX, textY - baseY, displayValueWidth, uiValueColor);
        scriptModel.unit().set("MB", "OnestMedium", drawScale, unitX - baseX, textY - baseY, unitW, uiMetaColor);
        scriptModel.extra().set(cachedExtra, "OnestMedium", drawScale, extraX - baseX, textY - baseY, displayExtraWidth, uiExtraColor);
        scriptModel.animation().setLabelEffect(labelEffect.get().name(), labelEffectSpeed.get(), time)
                .setDigitAnimation(false, "", 1.0f, 0.0f);
        scriptModel.data("usedMiB", usedMiB)
                .data("maxMiB", maxMiB)
                .data("percent", percent)
                .data("valueText", cachedValue)
                .data("unitText", "MB")
                .data("extraText", cachedExtra);
        ScriptedCompactHudStatRenderer.INSTANCE.render(scriptModel, renderer, fallback, ctx, tickDelta);
    }

    @Override
    public boolean supportsWidgetAnchoring() {
        return true;
    }

    @Override
    public CompactHudStatModel compactStatModel() {
        return scriptModel;
    }

    private void updateMemoryStrings(long usedMiB, long maxMiB, int percent) {
        if (usedMiB == lastUsedMiB && maxMiB == lastMaxMiB && percent == lastPercent) {
            return;
        }
        lastUsedMiB = usedMiB;
        lastMaxMiB = maxMiB;
        lastPercent = percent;

        valueBuilder.setLength(0);
        valueBuilder.append(usedMiB).append('/').append(maxMiB);
        cachedValue = valueBuilder.toString();
        cachedExtra = "[" + percent + "%]";
    }

    private void updatePalette() {
        if (isThemeMode()) {
            int panelAlpha = bgAlpha.get();
            uiBgPrimary = HudRenderUtil.setAlpha(theme().windowBg(), panelAlpha);
            uiBgSecondary = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().surface(), theme().windowHeader(), 0.35f),
                    panelAlpha
            );
            uiStroke = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().windowStroke(), theme().strokeSoft(), 0.4f),
                    Math.min(panelAlpha, 190)
            );
            uiIconColor = theme().textPrimary();
            uiValueColor = theme().textPrimary();
            uiMetaColor = theme().textMuted();
            uiExtraColor = HudRenderUtil.mixColor(theme().textMuted(), theme().textPrimary(), 0.18f);
            return;
        }

        uiBgPrimary = bg.getArgb();
        uiBgSecondary = bg2.getArgb();
        uiStroke = stroke.getArgb();
        uiIconColor = iconColor.getArgb();
        uiValueColor = valueColor.getArgb();
        uiMetaColor = metaColor.getArgb();
        uiExtraColor = extraColor.getArgb();
    }

    private boolean isThemeMode() {
        return COLOR_THEME.equals(colorMode.get());
    }

    private boolean isCustomMode() {
        return COLOR_CUSTOM.equals(colorMode.get());
    }

    private boolean isGlassEffect() {
        return EFFECT_GLASS.equals(bgEffect.get());
    }

    private boolean hasEffect() {
        return !EFFECT_NONE.equals(bgEffect.get());
    }
}
