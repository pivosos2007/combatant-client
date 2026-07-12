/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import combatant.client.render.engine.core.ViewportContext;

/**
 * Deprecated compatibility facade. Use ViewportContext directly.
 */
@Deprecated(forRemoval = false)
public enum Projection2D {
    ;

    public static void beginUnscaled(GuiGraphicsExtractor ctx) {
        ViewportContext.beginUnscaled(ctx);
    }

    public static void beginUnscaledLogical(GuiGraphicsExtractor ctx) {
        ViewportContext.beginUnscaledLogical(ctx);
    }

    public static void beginScaled(GuiGraphicsExtractor ctx) {
        ViewportContext.beginScaled(ctx);
    }

    public static void end(GuiGraphicsExtractor ctx) {
        ViewportContext.end(ctx);
    }

    public static float getScaleFactor() {
        return ViewportContext.getScaleFactor();
    }

    public static float getUiScale() {
        return ViewportContext.getUiScale();
    }

    public static GuiGraphicsExtractor getCurrentContext() {
        return ViewportContext.getCurrentContext();
    }

    public static void unscaledProjection() {
        ViewportContext.unscaledProjection();
    }

    public static void logicalProjection() {
        ViewportContext.logicalProjection();
    }

    public static void scaledProjection() {
        ViewportContext.scaledProjection();
    }

    public static void projectionForSize(float width, float height) {
        ViewportContext.projectionForSize(width, height);
    }
}
