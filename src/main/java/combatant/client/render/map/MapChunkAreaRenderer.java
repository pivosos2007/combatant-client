/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.map;

import combatant.client.render.engine.renderer.Renderer2D;

/** Shared map-space chunk area highlight for selection and information cells. */
public final class MapChunkAreaRenderer {
    private MapChunkAreaRenderer() {}

    public static void draw(MapViewport viewport, int minChunkX, int minChunkZ,
                            int maxChunkX, int maxChunkZ, int argb,
                            int fillAlpha, int outlineAlpha, float stroke) {
        if (viewport == null || minChunkX > maxChunkX || minChunkZ > maxChunkZ) return;
        MapScreenPoint a = viewport.project((double) minChunkX * 16.0, (double) minChunkZ * 16.0);
        MapScreenPoint b = viewport.project(((double) maxChunkX + 1.0) * 16.0,
                ((double) maxChunkZ + 1.0) * 16.0);
        double minX = Math.min(a.x(), b.x());
        double minY = Math.min(a.y(), b.y());
        double maxX = Math.max(a.x(), b.x());
        double maxY = Math.max(a.y(), b.y());
        MapRect screen = viewport.screenBounds();
        double x = Math.max(screen.x(), minX);
        double y = Math.max(screen.y(), minY);
        double right = Math.min(screen.maxX(), maxX);
        double bottom = Math.min(screen.maxY(), maxY);
        double width = right - x;
        double height = bottom - y;
        if (width <= 0.0 || height <= 0.0) return;
        Renderer2D renderer = Renderer2D.COLOR;
        int rgb = argb & 0x00FFFFFF;
        if (fillAlpha > 0) renderer.quad(x, y, width, height, (fillAlpha << 24) | rgb);
        if (outlineAlpha <= 0 || stroke <= 0.0f) return;
        int edge = (outlineAlpha << 24) | rgb;
        // Draw actual chunk borders, not fake borders where a larger area was clipped
        // by the screen bounds. Selection and hovering use exactly the same geometry.
        if (minY >= screen.y()) renderer.quad(x, y, width, Math.min(height, stroke), edge);
        if (maxY <= screen.maxY()) renderer.quad(x, bottom - Math.min(height, stroke), width,
                Math.min(height, stroke), edge);
        if (minX >= screen.x()) renderer.quad(x, y, Math.min(width, stroke), height, edge);
        if (maxX <= screen.maxX()) renderer.quad(right - Math.min(width, stroke), y,
                Math.min(width, stroke), height, edge);
    }
}
