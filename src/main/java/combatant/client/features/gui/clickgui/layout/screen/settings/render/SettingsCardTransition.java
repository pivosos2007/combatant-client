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
 *
 * Keep this deliberately restrained. The previous blur/bloom arrival made every
 * card briefly read as a separate floating layer and exaggerated viewport clip
 * boundaries. Cards now keep most of their opacity and only settle the final
 * alpha during a short section transition.
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
        float alpha = arrivalAlpha(progress);
        double previousRendererAlpha = Renderer2D.COLOR.getAlpha();
        float previousGuiAlpha = ClickGuiRenderer.getRenderAlphaMultiplier();
        Renderer2D.COLOR.setAlpha(previousRendererAlpha * alpha);
        ClickGuiRenderer.setRenderAlphaMultiplier(previousGuiAlpha * alpha);
        return new CardScope(previousRendererAlpha, previousGuiAlpha);
    }

    private static float arrivalAlpha(float p) {
        // Never make a section materialize from zero. A very shallow 92 -> 100%
        // settle is enough to register a category change without dimming the cards.
        return 0.92f + 0.08f * AnimationUtility.easeOutCubic(AnimationUtility.clamp01(p));
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
