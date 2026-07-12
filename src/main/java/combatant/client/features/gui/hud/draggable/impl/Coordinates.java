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
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
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

//todo Description
@HudElementInfo(
        id = "xyz",
        displayName = "XYZ",
        enabledByDefault = true,
        order = 100
)
public final class Coordinates extends DraggableHudElement implements ScriptableHudStatWidget {

    private static final Identifier COORDS_ICON = Identifier.fromNamespaceAndPath("combatant", "textures/hud/elements/coords.png");
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
    private static final float SEGMENT_GAP = 8f;
    private static final float LABEL_VALUE_GAP = 2f;
    private static final float RIGHT_PAD = 5f;
    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";
    private static final String EFFECT_GLASS = "Glass";

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final EnumValue<NetherMode> netherMode =
            enumSetting("xyz_nether", NetherMode.ON, NetherMode.OFF, NetherMode.ON, NetherMode.ONLY_NETHER);
    private final NumberValue<Double> scale = num("xyz_scale", 2.37, 0.5, 5.0);
    private final ModeValue colorMode = mode("xyz_color_mode", "color_mode", "Theme", new String[]{COLOR_THEME, COLOR_CUSTOM});
    private final RGBColorValue iconColor = visibleWhen(colorNoAlpha("xyz_icon_color", "#FFFFFF"), this::isCustomMode);
    private final RGBColorValue labelColor = visibleWhen(colorNoAlpha("xyz_label_color", "#9B9B9B"), this::isCustomMode);
    private final RGBColorValue valueColor = visibleWhen(colorNoAlpha("xyz_value_color", "#FFFFFF"), this::isCustomMode);
    private final RGBColorValue extraColor = visibleWhen(colorNoAlpha("xyz_extra_color", "#BEBEBE"), this::isCustomMode);
    private final ModeValue bgEffect = mode("xyz_bg_effect", "bg_effect", "Blur", new String[]{EFFECT_NONE, EFFECT_BLUR, EFFECT_GLASS});
    private final RGBAColorValue bg = visibleWhen(color("xyz_bg", "#F7343434"), () -> isCustomMode() && !isGlassEffect());
    private final RGBAColorValue bg2 = visibleWhen(color("xyz_bg_secondary", "#F7161616"), () -> isCustomMode() && !isGlassEffect());
    private final RGBColorValue stroke = visibleWhen(colorNoAlpha("xyz_stroke", "#5A5A5A"), () -> isCustomMode() && !isGlassEffect());
    private final NumberValue<Integer> bgAlpha = visibleWhen(num("xyz_bg_alpha", 184, 0, 255), () -> isThemeMode() && !isGlassEffect());
    private final NumberValue<Integer> blurAlpha = visibleWhen(num("xyz_blur_alpha", 255, 0, 255), this::hasEffect);
    private final EnumValue<HudTextEffects.Effect> labelEffect =
            enumSetting("xyz_label_effect", HudTextEffects.Effect.FLOW,
                    HudTextEffects.Effect.NONE, HudTextEffects.Effect.MIX, HudTextEffects.Effect.FLOW,
                    HudTextEffects.Effect.PULSE, HudTextEffects.Effect.STRIPE);
    private final NumberValue<Integer> labelEffectSpeed =
            visibleWhen(num("xyz_label_effect_speed", 18, 1, 60),
                    () -> labelEffect.get() != HudTextEffects.Effect.NONE);
    private final CompactHudStatModel scriptModel = new CompactHudStatModel("xyz");
    private int uiBgPrimary;
    private int uiBgSecondary;
    private int uiStroke;
    private int uiIconColor;
    private int uiLabelColor;
    private int uiValueColor;
    private int uiExtraColor;
    private float displayXValueWidth = -1f;
    private float displayYValueWidth = -1f;
    private float displayZValueWidth = -1f;
    private float displayExtraWidth = -1f;

    private static float smoothWidth(float current, float target) {
        if (current < 0.0f) return target;
        if (target >= current) return target;
        float next = AnimationUtility.approach(current, target, AnimationUtility.deltaTime(), 12.0f);
        return AnimationUtility.snap(next, target, 0.25f);
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        this.x = 16f;
        this.y = screenH - 92f;
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
        if (mc == null || (mc.player == null && !preview)) {
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

        boolean inNether = mc != null && mc.level != null && mc.level.dimension() == Level.NETHER;
        double rawX = mc != null && mc.player != null ? mc.player.getX() : 0.0;
        double rawY = mc != null && mc.player != null ? mc.player.getY() : 0.0;
        double rawZ = mc != null && mc.player != null ? mc.player.getZ() : 0.0;
        int px = Mth.floor(rawX);
        int py = Mth.floor(rawY);
        int pz = Mth.floor(rawZ);
        boolean showNether = shouldShowNether(inNether);
        int hx = showNether ? (int) (rawX * (inNether ? 8.0 : 0.125)) : 0;
        int hz = showNether ? (int) (rawZ * (inNether ? 8.0 : 0.125)) : 0;
        updatePalette();

        String xValue = Integer.toString(px);
        String yValue = Integer.toString(py);
        String zValue = Integer.toString(pz);
        String extra = showNether ? "[" + hx + " " + hz + "]" : "";

        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        TextRenderer tr = Fonts.renderer("Onest", FontInfo.Type.Regular, fallback);

        float drawScale = HudScale.scale(screenW, screenH)
                * (hud.getFontSize() / 18f)
                * SCALE_MULT
                * this.scale.get().floatValue();

        tr.begin(drawScale, true, false);
        float labelXW = (float) tr.getWidth("x", false);
        float labelYW = (float) tr.getWidth("y", false);
        float labelZW = (float) tr.getWidth("z", false);
        float valueXW = (float) tr.getWidth(xValue, false);
        float valueYW = (float) tr.getWidth(yValue, false);
        float valueZW = (float) tr.getWidth(zValue, false);
        float extraW = showNether ? (float) tr.getWidth(extra, false) : 0f;
        float textH = (float) tr.getHeight(false);
        tr.end();

        float boxH = BOX_HEIGHT * drawScale;
        float iconSize = ICON_SIZE * drawScale;
        float cursorX = x + TEXT_LEFT_X * drawScale;
        float gap = SEGMENT_GAP * drawScale;
        float pairGap = LABEL_VALUE_GAP * drawScale;
        displayXValueWidth = smoothWidth(displayXValueWidth, valueXW);
        displayYValueWidth = smoothWidth(displayYValueWidth, valueYW);
        displayZValueWidth = smoothWidth(displayZValueWidth, valueZW);
        displayExtraWidth = showNether ? smoothWidth(displayExtraWidth, extraW) : 0.0f;
        float boxW = (cursorX - x)
                + labelXW + pairGap + displayXValueWidth
                + gap
                + labelYW + pairGap + displayYValueWidth
                + gap
                + labelZW + pairGap + displayZValueWidth
                + (showNether ? gap + displayExtraWidth : 0f)
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
        float textY = baseY + (boxH - textH) * 0.5f;

        int iconColor = uiIconColor;
        boolean useLabelEffect = labelEffect.get() != HudTextEffects.Effect.NONE;
        float time = useLabelEffect ? (float) (Util.getMillis() / 1000.0) : 0.0f;
        if (useLabelEffect) {
            iconColor = HudTextEffects.animatedColor(iconColor, labelEffect.get(),
                    labelEffectSpeed.get(), time, 0.0f);
        }

        float radius = BOX_RADIUS * drawScale;
        scriptModel.setVisible(true);
        scriptModel.setRoot(baseX, baseY, boxW, boxH, radius, drawScale);
        scriptModel.background().set(bgEffect.get(), isThemeMode(), blurAlpha.get() / 255.0f,
                uiBgPrimary, uiBgSecondary, uiStroke, Math.max(0.5f, STROKE_WIDTH * drawScale), BOX_SOFTNESS);
        scriptModel.icon().texture(COORDS_ICON.toString(), iconX - baseX, iconY - baseY, iconSize, iconSize, iconColor);
        scriptModel.divider().set(dividerX - baseX, dividerY - baseY, dividerW, dividerH,
                HudRenderUtil.setAlpha(uiLabelColor, 0x52));
        scriptModel.value().set(xValue + " " + yValue + " " + zValue, "Onest", drawScale,
                cursorX - baseX, textY - baseY, boxW - (cursorX - baseX), uiValueColor);
        scriptModel.unit().hidden();
        if (showNether) {
            scriptModel.extra().set(extra, "OnestMedium", drawScale,
                    boxW - RIGHT_PAD * drawScale - displayExtraWidth, textY - baseY, displayExtraWidth, uiExtraColor);
        } else {
            scriptModel.extra().hidden();
        }
        scriptModel.animation().setLabelEffect(labelEffect.get().name(), labelEffectSpeed.get(), time)
                .setDigitAnimation(false, "", 1.0f, 0.0f);
        scriptModel.data("x", px)
                .data("y", py)
                .data("z", pz)
                .data("xText", xValue)
                .data("yText", yValue)
                .data("zText", zValue)
                .data("showNether", showNether)
                .data("netherX", hx)
                .data("netherZ", hz)
                .data("netherText", extra)
                .data("labelXW", labelXW)
                .data("labelYW", labelYW)
                .data("labelZW", labelZW)
                .data("valueXW", displayXValueWidth)
                .data("valueYW", displayYValueWidth)
                .data("valueZW", displayZValueWidth)
                .data("xyzExtraW", displayExtraWidth)
                .data("labelColor", CompactHudStatModel.colorString(uiLabelColor))
                .data("valueColor", CompactHudStatModel.colorString(uiValueColor));
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

    private boolean shouldShowNether(boolean inNether) {
        NetherMode mode = netherMode.get();
        if (mode == NetherMode.ON) return true;
        if (mode == NetherMode.ONLY_NETHER) return !inNether;
        return false;
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
            uiLabelColor = theme().textMuted();
            uiValueColor = theme().textPrimary();
            uiExtraColor = HudRenderUtil.mixColor(theme().textMuted(), theme().textPrimary(), 0.18f);
            return;
        }

        uiBgPrimary = bg.getArgb();
        uiBgSecondary = bg2.getArgb();
        uiStroke = stroke.getArgb();
        uiIconColor = iconColor.getArgb();
        uiLabelColor = labelColor.getArgb();
        uiValueColor = valueColor.getArgb();
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

    private enum NetherMode implements EnumValue.IdProvider {
        OFF("Off"),
        ON("On"),
        ONLY_NETHER("OnlyNether");

        private final String id;

        NetherMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }
}
