/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.render;

import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;

import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.features.gui.hud.HudClimateSurface;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.misc.ClickGui;
import combatant.client.render.engine.renderer.ui.draw.UiPrimitive;

/** Semantic material layers shared by Settings and its controls. */
public enum SettingsGlassMaterial {
    ;

    public static void workspace(float x, float y, float w, float h, SettingsGuiPalette palette) {
        workspace(x, y, w, h, palette, 1f);
    }

    public static void workspace(float x, float y, float w, float h,
                                 SettingsGuiPalette palette, float prismProgress) {
        float alpha = AnimationUtility.clamp(LayoutRender2D.getRenderAlphaMultiplier(), 0f, 1f);
        workspace(x, y, w, h, 85f, 16f, palette, alpha, prismProgress);
    }

    public static void pickerWorkspace(float x,
                                       float y,
                                       float w,
                                       float h,
                                       float railWidth,
                                       float scale,
                                       SettingsGuiPalette palette,
                                       float transitionProgress) {
        workspace(x, y, w, h, railWidth, 8f * scale, palette, transitionProgress, transitionProgress);
    }

    private static void workspace(float x,
                                  float y,
                                  float w,
                                  float h,
                                  float railWidth,
                                  float radius,
                                  SettingsGuiPalette palette,
                                  float opacity,
                                  float prismProgress) {
        float lifecycleAlpha = AnimationUtility.clamp(opacity, 0f, 1f);
        float blurAlpha = lifecycleAlpha;
        drawBlur(x, y, w, h, radius, blurAlpha);
        LayoutRender2D.roundedQuad(
                x, y, w, h, radius,
                LayoutRender2D.alpha(palette.menuWindowBgLeft(), 0.30f),
                LayoutRender2D.alpha(palette.menuWindowBgRight(), 0.30f),
                LayoutRender2D.alpha(palette.menuWindowBgRight(), 0.30f),
                LayoutRender2D.alpha(palette.menuWindowBgLeft(), 0.30f)
        );
        float railW = Math.min(w, Math.max(0f, railWidth));
        Renderer2D.COLOR.roundedRectCornersQuad(
                x, y, railW, h,
                radius, 0f, 0f, radius,
                1.0f,
                palette.workspaceWashLeft(),
                palette.workspaceWashLeft(),
                palette.workspaceWashRight(),
                palette.workspaceWashRight()
        );
        drawClimateCoat(x, y, w, h, radius, lifecycleAlpha, false);
    }

    private static void drawBlur(float x, float y, float w, float h, float radius, float blurAlpha) {
        combatant.client.features.module.modules.misc.ClickGui module =
                combatant.client.features.module.Modules.get(
                        combatant.client.features.module.modules.misc.ClickGui.class);
        if (module == null || !module.hasCustomGlassOptics()) {
            // Restore the existing Settings blur pipeline and its opacity/quality.
            // The liquid glass lens is opt-in, not a replacement for blurRect.
            Renderer2D.COLOR.blurRect(x, y, w, h, radius,
                    LayoutRender2D.clickGuiBlurQuality(), 1.0f, blurAlpha, 0xFFFFFF);
            return;
        }
        var material = combatant.client.render.engine.renderer.ui.draw.UiLiquidGlassMaterial.DEFAULT
                .withOptics(module.glassOptics(
                        combatant.client.render.engine.renderer.ui.draw.UiGlassOpticsSpec.LIQUID));
        if (module.environmentalEffectsEnabled()) {
            var climate = HudClimateSurface.glassVisual(true);
            if (climate.custom()) material = material.withVisual(climate);
        }
        var resolvedMaterial = material;
        Renderer2D.COLOR.withLiquidGlassMaterial(resolvedMaterial, () ->
                Renderer2D.COLOR.liquidGlassRect(x, y, w, h, radius,
                        0xFFFFFFFF, blurAlpha, blurAlpha,
                        Renderer2D.LiquidGlassPreset.HUD_SURFACE_LARGE));
    }

    /** Weather is an opt-in coat over the existing Settings blur, never a blur replacement. */
    private static void drawClimateCoat(float x, float y, float w, float h,
                                        float radius, float opacity, boolean solidSurface) {
        ClickGui module = Modules.get(ClickGui.class);
        if (module == null || !module.environmentalEffectsEnabled()
                || (!solidSurface && module.hasCustomGlassOptics())) return;
        HudClimateSurface.withEnabled(true, () -> HudClimateSurface.drawMaterialClimate(
                Renderer2D.COLOR,
                UiPrimitive.builder(x, y, w, h)
                        .roundedCorners(radius, radius, radius, radius).build(),
                opacity));
    }

    public static void navigation(float x, float y, float w, float h, float scale, SettingsGuiPalette palette) {
        float radius = 7f * scale;
        LayoutRender2D.roundedQuad(
                x, y, w, h, radius,
                palette.navigationPlaneTop(),
                palette.navigationPlaneTop(),
                palette.navigationPlaneBottom(),
                palette.navigationPlaneBottom()
        );
        LayoutRender2D.roundedStroke(x, y, w, h, radius, 0.42f * scale, palette.glassEdgeSoft());
        drawClimateCoat(x, y, w, h, radius, 1f, true);
    }

    public static void content(float x, float y, float w, float h, float scale, SettingsGuiPalette palette) {
        float radius = 7f * scale;
        LayoutRender2D.roundedQuad(
                x, y, w, h, radius,
                palette.contentPlaneTop(),
                palette.contentPlaneTop(),
                palette.contentPlaneBottom(),
                palette.contentPlaneBottom()
        );
        LayoutRender2D.roundedStroke(x, y, w, h, radius, 0.42f * scale, palette.glassEdgeSoft());
        drawClimateCoat(x, y, w, h, radius, 1f, true);
    }

    public static void elevated(float x, float y, float w, float h, float radius, float scale, SettingsGuiPalette palette) {
        elevated(x, y, w, h, radius, scale, palette, 1f);
    }

    public static void elevated(float x, float y, float w, float h, float radius, float scale, SettingsGuiPalette palette, float alpha) {
        float lifecycleAlpha = AnimationUtility.clamp(LayoutRender2D.getRenderAlphaMultiplier(), 0f, 1f);
        float opacity = Math.max(0f, Math.min(1f, alpha)) * lifecycleAlpha;
        LayoutRender2D.rounded(x, y, w, h, radius, LayoutRender2D.alpha(palette.contentPlaneTop(), 0.30f * opacity));
        LayoutRender2D.roundedStroke(x, y, w, h, radius, 0.42f * scale,
                LayoutRender2D.alpha(palette.glassEdgeSoft(), opacity));
        drawClimateCoat(x, y, w, h, radius, opacity, false);
    }

    public static void control(float x, float y, float w, float h, float radius, int color, int edge) {
        LayoutRender2D.rounded(x, y, w, h, radius, color);
        LayoutRender2D.roundedStroke(x, y, w, h, radius, 0.45f, edge);
    }

    public static void selection(float x, float y, float w, float h, float radius, int left, int right) {
        LayoutRender2D.roundedQuad(x, y, w, h, radius, left, right, right, left);
    }
}
