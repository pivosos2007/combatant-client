/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

/** Immutable pan/zoom projection independent of Xaero and Minecraft GUI classes. */
public record MapViewport(MapRect screenBounds,
                          double centerX,
                          double centerZ,
                          double pixelsPerBlock) {
    public static final double MIN_PIXELS_PER_BLOCK = 1.0 / 4096.0;
    public static final double MAX_PIXELS_PER_BLOCK = 4096.0;

    public MapViewport {
        if (screenBounds == null) throw new NullPointerException("screenBounds");
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            throw new IllegalArgumentException("Map center must be finite.");
        }
        if (!Double.isFinite(pixelsPerBlock)
                || pixelsPerBlock < MIN_PIXELS_PER_BLOCK
                || pixelsPerBlock > MAX_PIXELS_PER_BLOCK) {
            throw new IllegalArgumentException("Map zoom is outside supported bounds: " + pixelsPerBlock);
        }
    }

    public MapScreenPoint project(double worldX, double worldZ) {
        return new MapScreenPoint(
                screenBounds.centerX() + (worldX - centerX) * pixelsPerBlock,
                screenBounds.centerY() + (worldZ - centerZ) * pixelsPerBlock
        );
    }

    public MapPoint unproject(double screenX, double screenY) {
        return new MapPoint(
                centerX + (screenX - screenBounds.centerX()) / pixelsPerBlock,
                centerZ + (screenY - screenBounds.centerY()) / pixelsPerBlock
        );
    }

    public MapRect visibleWorldBounds() {
        MapPoint min = unproject(screenBounds.x(), screenBounds.y());
        MapPoint max = unproject(screenBounds.maxX(), screenBounds.maxY());
        return new MapRect(min.x(), min.z(), max.x() - min.x(), max.z() - min.z());
    }

    /** Applies a screen-space drag delta while keeping content attached to the pointer. */
    public MapViewport panPixels(double deltaX, double deltaY) {
        return new MapViewport(
                screenBounds,
                centerX - deltaX / pixelsPerBlock,
                centerZ - deltaY / pixelsPerBlock,
                pixelsPerBlock
        );
    }

    /** Zooms around a screen anchor, preserving the world point under that anchor. */
    public MapViewport zoomAt(double screenX, double screenY, double factor) {
        if (!Double.isFinite(factor) || factor <= 0.0) {
            throw new IllegalArgumentException("Zoom factor must be finite and positive.");
        }
        MapPoint anchor = unproject(screenX, screenY);
        double nextScale = clamp(pixelsPerBlock * factor, MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK);
        double nextCenterX = anchor.x() - (screenX - screenBounds.centerX()) / nextScale;
        double nextCenterZ = anchor.z() - (screenY - screenBounds.centerY()) / nextScale;
        return new MapViewport(screenBounds, nextCenterX, nextCenterZ, nextScale);
    }

    public MapViewport withScreenBounds(MapRect bounds) {
        return new MapViewport(bounds, centerX, centerZ, pixelsPerBlock);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
