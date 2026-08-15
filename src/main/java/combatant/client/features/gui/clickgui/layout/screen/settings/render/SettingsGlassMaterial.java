/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.render;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.material.PrismaticGlassTransition;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;

/** Semantic material layers shared by Settings and its controls. */
public enum SettingsGlassMaterial {
    ;

    public static void workspace(float x, float y, float w, float h, float scale, SettingsGuiPalette palette) {
        float lifecycleAlpha = AnimationUtility.clamp(ClickGuiRenderer.getRenderAlphaMultiplier(), 0f, 1f);
        workspace(x, y, w, h, 42.5f * scale, scale, palette, lifecycleAlpha, lifecycleAlpha);
    }

    public static void workspace(float x,
                                 float y,
                                 float w,
                                 float h,
                                 float scale,
                                 SettingsGuiPalette palette,
                                 float prismProgress) {
        float lifecycleAlpha = AnimationUtility.clamp(ClickGuiRenderer.getRenderAlphaMultiplier(), 0f, 1f);
        workspace(x, y, w, h, 42.5f * scale, scale, palette, lifecycleAlpha, prismProgress);
    }

    public static void pickerWorkspace(float x,
                                       float y,
                                       float w,
                                       float h,
                                       float railWidth,
                                       float scale,
                                       SettingsGuiPalette palette,
                                       float transitionProgress) {
        workspace(x, y, w, h, railWidth, scale, palette, transitionProgress, transitionProgress);
    }

    private static void workspace(float x,
                                  float y,
                                  float w,
                                  float h,
                                  float railWidth,
                                  float scale,
                                  SettingsGuiPalette palette,
                                  float opacity,
                                  float prismProgress) {
        float radius = 8f * scale;
        float lifecycleAlpha = AnimationUtility.clamp(opacity, 0f, 1f);
        float materialAlpha = lifecycleAlpha;
        float blurAlpha = lifecycleAlpha;
        drawBlur(x, y, w, h, radius, blurAlpha);
        drawLiquidLayer(x, y, w, h, radius, scale, materialAlpha, blurAlpha, true, prismProgress);
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
    }

    private static void drawBlur(float x, float y, float w, float h, float radius, float blurAlpha) {
        Renderer2D.COLOR.blurRect(
                x, y, w, h, radius,
                ClickGuiRenderer.clickGuiBlurQuality(),
                1.0f,
                blurAlpha,
                0xFFFFFF
        );
    }

    private static void drawLiquidLayer(float x,
                                        float y,
                                        float w,
                                        float h,
                                        float radius,
                                        float scale,
                                        float materialAlpha,
                                        float blurAlpha,
                                        boolean panel,
                                        float transitionProgress) {
        PrismaticGlassTransition prism = PrismaticGlassTransition.fromProgress(transitionProgress);
        float distortion = (panel ? 0.190f : 0.155f) * materialAlpha * (1f + prism.strength() * 0.55f);
        Renderer2D.COLOR.liquidGlassRect(
                x, y, w, h, radius,
                (panel ? 15.5f : 12.5f) * scale,
                0xFFFFFFFF,
                materialAlpha,
                blurAlpha,
                panel ? -18.0f : -16.0f,
                1.0f,
                panel ? 0.78f : 0.84f,
                panel ? 0.52f : 0.48f,
                distortion,
                0.0f,
                prism.strength(),
                prism.phase()
        );
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
    }

    public static void elevated(float x, float y, float w, float h, float radius, float scale, SettingsGuiPalette palette) {
        elevated(x, y, w, h, radius, scale, palette, 1f);
    }

    public static void elevated(float x, float y, float w, float h, float radius, float scale, SettingsGuiPalette palette, float alpha) {
        float lifecycleAlpha = AnimationUtility.clamp(ClickGuiRenderer.getRenderAlphaMultiplier(), 0f, 1f);
        float opacity = Math.max(0f, Math.min(1f, alpha)) * lifecycleAlpha;
        float blurAlpha = AnimationUtility.clamp(opacity * 1.04f, 0f, 1f);
        drawBlur(x, y, w, h, radius, blurAlpha);
        drawLiquidLayer(x, y, w, h, radius, scale, opacity, blurAlpha, false, 1f);
    }

    public static void control(float x, float y, float w, float h, float radius, int color, int edge) {
        LayoutRender2D.rounded(x, y, w, h, radius, color);
        LayoutRender2D.roundedStroke(x, y, w, h, radius, 0.45f, edge);
    }

    public static void selection(float x, float y, float w, float h, float radius, int left, int right) {
        LayoutRender2D.roundedQuad(x, y, w, h, radius, left, right, right, left);
    }
}
