/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.text;

import combatant.client.render.engine.core.ViewportContext;

/**
 * Selects the raster source resolution for UI text in physical framebuffer pixels.
 * Text metrics and vertex coordinates remain expressed in the caller's logical units.
 */
final class FontRasterSelection {
    private FontRasterSelection() {
    }

    static double activePixelDensity() {
        ViewportContext viewport = ViewportContext.current();
        if (viewport == null) return 1.0;
        if (ViewportContext.activeMode() == ViewportContext.ProjectionMode.CUSTOM) return 1.0;
        return pixelDensity(viewport.framebufferWidth(), viewport.framebufferHeight(),
                viewport.width(), viewport.height());
    }

    static double pixelDensity(int framebufferWidth, int framebufferHeight,
                               double projectedWidth, double projectedHeight) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0 || !Double.isFinite(projectedWidth)
                || !Double.isFinite(projectedHeight) || projectedWidth <= 0.0 || projectedHeight <= 0.0) {
            return 1.0;
        }
        // Anisotropic/custom viewports: avoid choosing a raster too small on either axis.
        double density = Math.max(framebufferWidth / projectedWidth, framebufferHeight / projectedHeight);
        return Double.isFinite(density) && density > 0.0 ? density : 1.0;
    }

    static boolean supportsPixelMasks() {
        return ViewportContext.activeMode() != ViewportContext.ProjectionMode.CUSTOM;
    }

    static boolean preferSmallBitmap(double requestedScale, double pixelDensity, boolean big,
                                     boolean warped) {
        if (big || warped || !Double.isFinite(requestedScale) || requestedScale <= 0.0
                || !Double.isFinite(pixelDensity) || pixelDensity <= 0.0) return false;
        double physicalHeight = 18.0 * requestedScale * pixelDensity;
        return physicalHeight > 0.0 && physicalHeight <= 32.0;
    }

    static int selectAtlas(GlyphFont[] fonts, double requestedScale, double pixelDensity, boolean big) {
        if (fonts == null || fonts.length == 0) return 0;
        if (big) return fonts.length - 1;

        double scale = Double.isFinite(requestedScale) && requestedScale > 0.0 ? requestedScale : 1.0;
        double density = Double.isFinite(pixelDensity) && pixelDensity > 0.0 ? pixelDensity : 1.0;
        double targetRasterHeight = 27.0 * scale * density;
        // Prefer a source raster at least as large as the framebuffer footprint.
        // For MSDF fonts all entries share the same texture; this only changes the
        // nominal metric size, not the atlas allocation.
        for (int index = 0; index < fonts.length; index++) {
            GlyphFont font = fonts[index];
            if (font != null && font.height() >= targetRasterHeight) return index;
        }
        return fonts.length - 1;
    }
}
