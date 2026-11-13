/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.nondraggable.impl;

import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.config.SettingDef;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.HudGlassSettings;
import combatant.client.features.gui.preview.api.PreviewCatalog;
import combatant.client.features.gui.preview.api.HudPreviewCanvas;
import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiGlassOpticsSpec;
import combatant.client.render.engine.renderer.ui.draw.UiLiquidGlassMaterial;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.runtime.RuntimeGate;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.util.List;

@HudElementRegister(order = 25)
public final class SwapTooltip extends AbstractHudElement {

    public static final SwapTooltip INSTANCE = new SwapTooltip();

    private static final float PAD_X = 5.0f;
    private static final float PAD_Y = 3.8f;
    private static final float EXTRA_OFFSET_NO_BARS = 14.0f;
    private static final int GLASS_TINT = 0xFFFFFFFF;
    private static final int GLASS_VEIL = 0x2A141414;
    private static final UiGlassOpticsSpec SWAP_GLASS_PRESET = UiGlassOpticsSpec.LIQUID
            .withRefraction(0.08f).withBevel(0.15f, 0.055f)
            .withFluid(0.35f, 0.65f).withCleanReveal(0.55f)
            .withInteractionStrength(1.9157f).withChromaticAberration(0.95f)
            .withMagnification(1.0f).withSpecular(true)
            .withOpticalThickness(2.5f).withRefractiveIndex(1.567f)
            .withSurfaceRoughness(0.0f).withFrost(0.9349f)
            .withThinFilm(430f, 0.0f).withReactiveCoating(0.0f);

    private static String text;
    private static int color;
    private static float targetAlpha;
    private static boolean hasBars;

    private final HudGlassSettings glassSettings = new HudGlassSettings("swap_tooltip", UiGlassOpticsSpec.LIQUID, SWAP_GLASS_PRESET);
    private final NumberValue<Float> scale =
            num("swap_scale", 0.24f, 0.2f, 1.0f);
    private final NumberValue<Float> offsetY =
            num("swap_offset_y", 66.6f, 30.0f, 120.0f);
    private final EnumValue<TextEffect> textEffect =
            enumSetting("swap_text_effect", TextEffect.MIX, TextEffect.values());
    private final NumberValue<Integer> textEffectSpeed =
            visibleWhen(num("swap_text_effect_speed", 18, 1, 60), () -> textEffect.get() != TextEffect.NONE);

    private SwapTooltip() {
        super("swap_tooltip", "Swap Tooltip", "hud.static.swap_tooltip.description", true);
    }

    @Override
    public PreviewCatalog.Definition<String, HudPreviewCanvas> previewDefinition() {
        return PreviewCatalog.definition(
                new PreviewCatalog.Id(PreviewCatalog.Surface.HUD, getId()), HudPreviewCanvas.class,
                frame -> PreviewCatalog.Availability.READY,
                frame -> "Example Item",
                (canvas, value) -> renderLabel(value, 0xFFE9F3FF, 1f, true,
                        canvas.guiWidth(), canvas.guiHeight(),
                        canvas.hudScale(), canvas.guiScale(), true), PreviewCatalog.PreviewSpace.VANILLA_GUI);
    }

    public static SwapTooltip get() {
        return INSTANCE;
    }

    public static void capture(String value, int argb, float fade, boolean barsVisible) {
        if (value == null || value.isEmpty()) {
            clear();
            return;
        }
        text = value;
        color = argb;
        targetAlpha = Math.max(0.0f, Math.min(1.0f, fade));
        hasBars = barsVisible;
    }

    public static void clear() {
        targetAlpha = 0.0f;
    }

    public static boolean hasWork() {
        return text != null && INSTANCE.useSwapTooltip();
    }

    public static void render() {
        if (text == null) return;

        SwapTooltip settings = INSTANCE;
        if (!settings.useSwapTooltip()) {
            resetState();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            resetState();
            return;
        }

        renderLabel(text, color, targetAlpha, hasBars,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                HudScale.scale(mc.getWindow().getWidth(), mc.getWindow().getHeight()),
                ViewportContext.getScaleFactor(), false);
    }

    private static void renderLabel(String label, int argb, float opacity, boolean barsVisible,
                                    float screenW, float screenH, float uiScale, float fbScale,
                                    boolean preview) {
        SwapTooltip settings = INSTANCE;
        TextRenderer tr = BuiltinFontCatalog.ONEST_MEDIUM.renderer(TextRenderer.get());
        float textScale = settings.scale.get() * uiScale * 1.1f;
        float padX = PAD_X * uiScale;
        float padY = PAD_Y * uiScale;

        tr.begin(textScale, false, false);
        float textW;
        float textH;
        try {
            textW = (float) tr.getWidth(label, false);
            textH = (float) tr.getHeight(false);
        } finally {
            tr.end();
        }

        float totalW = textW + padX * 2.0f;
        float totalH = textH + padY * 2.0f;
        float radius = Math.max(4.5f, totalH * 0.28f);

        float x = (screenW - totalW) * 0.5f;
        float y = screenH - settings.offsetY.get();
        if (!barsVisible) y += EXTRA_OFFSET_NO_BARS;
        // The bottom-of-screen live anchor is covered by ClickGui controls in
        // the HUD editor. Render the same presenter in its unobstructed sample area.
        if (preview) {
            x = screenW * 0.07f;
            y = screenH * 0.32f;
        }

        float alpha = Math.max(0.0f, Math.min(1.0f, opacity));
        if (alpha <= 0.01f && opacity <= 0.0f) {
            if (!preview) resetState();
            return;
        }

        float xb = x * fbScale;
        float yb = y * fbScale;
        float wb = totalW * fbScale;
        float hb = totalH * fbScale;
        float rb = radius * fbScale;

        ViewportContext.unscaledProjection();
        drawGlass(xb, yb, wb, hb, rb, alpha);
        Renderer2D.COLOR.roundedRect(
                xb,
                yb,
                wb,
                hb,
                rb,
                1.0f,
                HudRenderUtil.scaleAlpha(glassVeil(), alpha)
        );

        ViewportContext.scaledProjection();
        tr.begin(textScale, false, false);
        try {
            int textColor = HudRenderUtil.scaleAlpha(argb != 0 ? argb : themeText(), alpha);
            renderText(tr, settings, label, x + padX, y + padY, textColor, alpha);
        } finally {
            tr.end();
        }

        ViewportContext.unscaledProjection();
    }

    private static void renderText(TextRenderer tr,
                                   SwapTooltip settings,
                                   String label,
                                   float x,
                                   float y,
                                   int textColor,
                                   float alpha) {
        TextEffect effect = settings.textEffect.get();
        if (effect == TextEffect.NONE || alpha < 0.98f) {
            tr.render(label, x, y, new RenderColor(textColor), true);
            return;
        }

        int textAlpha = (textColor >>> 24) & 0xFF;
        int baseColor = (textColor & 0x00FFFFFF) | (textAlpha << 24);
        int darkGray = (textAlpha << 24) | 0x00181B1F;
        int lightGray = (textAlpha << 24) | 0x00F0F2F5;
        int darkColor = HudRenderUtil.mixColor(baseColor, darkGray, 0.22f);
        int lightColor = HudRenderUtil.mixColor(baseColor, lightGray, 0.20f);
        float time = (float) (Util.getMillis() / 1000.0);
        float speed = Math.max(0.1f, settings.textEffectSpeed.get() * 0.08f);
        float basePhase = time * speed;

        tr.renderGradient(label, x, y, (index, codePoint, glyphX, out) -> {
            float t0;
            float t1;
            switch (effect) {
                case FLOW -> {
                    float phase = basePhase + index * 0.35f;
                    t0 = 0.5f + 0.5f * (float) Math.sin(phase);
                    t1 = 0.5f + 0.5f * (float) Math.sin(phase + 1.6f);
                }
                case PULSE -> {
                    float pulse = 0.5f + 0.5f * (float) Math.sin(basePhase * 1.6f);
                    t0 = pulse;
                    t1 = pulse;
                }
                case STRIPE -> {
                    float stripe = 0.5f + 0.5f * (float) Math.sin((basePhase + index * 0.55f) * 2.4f);
                    t0 = stripe;
                    t1 = 1.0f - stripe;
                }
                case MIX -> {
                    float phase = basePhase + index * 0.55f;
                    t0 = 0.5f + 0.5f * (float) Math.sin(phase);
                    t1 = 0.5f + 0.5f * (float) Math.sin(phase + 0.85f);
                }
                default -> {
                    t0 = 0.0f;
                    t1 = 0.0f;
                }
            }
            out[0] = HudRenderUtil.mixColor(darkColor, lightColor, t0);
            out[1] = HudRenderUtil.mixColor(darkColor, lightColor, t1);
        }, true);
    }

    private static void drawGlass(float x, float y, float w, float h, float radius, float alpha) {
        if (alpha <= 0.001f || w <= 0.0f || h <= 0.0f) return;
        Runnable draw = () -> Renderer2D.COLOR.liquidGlassRect(
                x, y, w, h, radius, GLASS_TINT, alpha, 1.0f,
                Renderer2D.LiquidGlassPreset.HUD_SURFACE_SMALL);
        UiLiquidGlassMaterial material = UiLiquidGlassMaterial.DEFAULT
                .withOptics(INSTANCE.glassSettings.apply(UiGlassOpticsSpec.LIQUID));
        Runnable original = draw;
        draw = () -> Renderer2D.COLOR.withLiquidGlassMaterial(material, original);
        Renderer2D.COLOR.withLiquidGlassBlurProfile(
                Renderer2D.BlurQuality.LIQUID_GLASS, HudRenderUtil.HUD_GLASS_KAWASE_OFFSET_PX,
                draw);
    }

    private static int glassVeil() {
        Themes.Theme theme = Theme.theme();
        return theme != null ? HudRenderUtil.setAlpha(theme.windowBg(), 0x2A) : GLASS_VEIL;
    }

    private static int themeText() {
        Themes.Theme theme = Theme.theme();
        return theme != null ? theme.textPrimary() | 0xFF000000 : 0xFFFFFFFF;
    }

    private static void resetState() {
        text = null;
        targetAlpha = 0.0f;
        hasBars = false;
    }

    private boolean useSwapTooltip() {
        return !RuntimeGate.isPanic() && isEnabled();
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(0, enabledSettingDef());
        glassSettings.addSettings(defs);
    }

    public enum TextEffect implements EnumValue.IdProvider {
        NONE("None"),
        MIX("Mix"),
        FLOW("Flow"),
        PULSE("Pulse"),
        STRIPE("Stripe");

        private final String id;

        TextEffect(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }
}
