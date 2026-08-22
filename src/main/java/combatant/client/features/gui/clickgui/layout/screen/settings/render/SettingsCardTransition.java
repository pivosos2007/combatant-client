/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.render;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;

/**
 * Card-local transition used when switching Settings sections.
 * The shell/backplate never participates: only explicit card/panel bounds opt into the effect.
 */
public enum SettingsCardTransition {
    ;

    private static float progress = 1.0f;

    public static SectionScope push(float sectionProgress) {
        float previous = progress;
        progress = AnimationUtility.clamp01(sectionProgress);
        return new SectionScope(previous);
    }

    public static CardScope beginCard(float x,
                                      float y,
                                      float w,
                                      float h,
                                      float radius,
                                      float scale,
                                      SettingsGuiPalette palette) {
        float p = progress;
        if (p < 0.999f) {
            drawSoftArrival(x, y, w, h, radius, scale, palette, p);
        }

        float sharp = sharpAlpha(p);
        double previousRendererAlpha = Renderer2D.COLOR.getAlpha();
        float previousGuiAlpha = ClickGuiRenderer.getRenderAlphaMultiplier();
        Renderer2D.COLOR.setAlpha(previousRendererAlpha * sharp);
        ClickGuiRenderer.setRenderAlphaMultiplier(previousGuiAlpha * sharp);
        return new CardScope(previousRendererAlpha, previousGuiAlpha);
    }

    private static void drawSoftArrival(float x,
                                        float y,
                                        float w,
                                        float h,
                                        float radius,
                                        float scale,
                                        SettingsGuiPalette palette,
                                        float p) {
        float blurAlpha = blurAlpha(p);
        float bloomAlpha = bloomAlpha(p);
        if (blurAlpha <= 0.002f && bloomAlpha <= 0.002f) return;

        if (blurAlpha > 0.002f) {
            // Backdrop blur is strictly limited to the card rectangle. The extra soft form is
            // a blurred silhouette of the card itself, not a glow on the Settings backplate.
            ClickGuiRenderer.drawBlur(
                    x,
                    y,
                    w,
                    h,
                    radius,
                    0xFF000000,
                    0.42f * blurAlpha
            );

            float formBlur = (5.0f + 8.5f * blurAlpha) * scale;
            int top = LayoutRender2D.alpha(palette.moduleCardTopStrong(), 0.18f * blurAlpha);
            int bottom = LayoutRender2D.alpha(palette.moduleCardBottomStrong(), 0.15f * blurAlpha);
            Renderer2D.COLOR.roundedRectSoftShadowGradient(
                    x,
                    y,
                    w,
                    h,
                    radius,
                    formBlur,
                    0.26f,
                    top,
                    bottom,
                    90f
            );
        }

        if (bloomAlpha > 0.002f) {
            int bloomColor = SettingsGuiPalette.mix(
                    palette.moduleCardTopStrong(),
                    palette.menuCategorySelectedRight(),
                    0.24f
            );
            ClickGuiRenderer.drawRoundedRectGlow(
                    x,
                    y,
                    w,
                    h,
                    radius,
                    2.6f * scale,
                    LayoutRender2D.alpha(bloomColor, 0.052f * bloomAlpha)
            );
        }
    }

    private static float sharpAlpha(float p) {
        float delayed = AnimationUtility.clamp((p - 0.10f) / 0.90f, 0f, 1f);
        return AnimationUtility.easeOutCubic(delayed);
    }

    private static float blurAlpha(float p) {
        float rise = AnimationUtility.easeOutCubic(AnimationUtility.clamp(p / 0.24f, 0f, 1f));
        float fall = 1f - AnimationUtility.smoothstep(AnimationUtility.clamp((p - 0.20f) / 0.68f, 0f, 1f));
        return AnimationUtility.clamp01(rise * fall);
    }

    private static float bloomAlpha(float p) {
        float rise = AnimationUtility.easeOutCubic(AnimationUtility.clamp(p / 0.20f, 0f, 1f));
        float fall = 1f - AnimationUtility.smoothstep(AnimationUtility.clamp((p - 0.12f) / 0.42f, 0f, 1f));
        return AnimationUtility.clamp01(rise * fall);
    }

    public static final class SectionScope implements AutoCloseable {
        private final float previous;
        private boolean closed;

        private SectionScope(float previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            progress = previous;
        }
    }

    public static final class CardScope implements AutoCloseable {
        private final double previousRendererAlpha;
        private final float previousGuiAlpha;
        private boolean closed;

        private CardScope(double previousRendererAlpha, float previousGuiAlpha) {
            this.previousRendererAlpha = previousRendererAlpha;
            this.previousGuiAlpha = previousGuiAlpha;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            ClickGuiRenderer.restoreRenderAlphaMultiplier(previousGuiAlpha);
            Renderer2D.COLOR.setAlpha(previousRendererAlpha);
        }
    }
}
