/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.component.solid;

/** Exact responsive geometry of the reusable Modern/Solid browser shell. */
public record SolidMetrics(float x,
                           float y,
                           float width,
                           float height,
                           float navigationWidth,
                           float collectionWidth,
                           float detailWidth,
                           float headerHeight) {
    public static final float DESIGN_WIDTH = 488.0f;
    public static final float DESIGN_HEIGHT = 318.0f;
    public static final float MIN_WIDTH = 360.0f;
    public static final float MIN_HEIGHT = 235.0f;

    public static SolidMetrics floating(float viewportWidth, float viewportHeight) {
        float width = Math.min(DESIGN_WIDTH, Math.max(MIN_WIDTH, viewportWidth - 12.0f));
        float height = Math.min(DESIGN_HEIGHT, Math.max(MIN_HEIGHT, viewportHeight - 12.0f));
        float x = Math.round((viewportWidth - width) * 0.5f);
        float y = Math.round((viewportHeight - height) * 0.5f);
        return at(x, y, width, height);
    }

    public static SolidMetrics at(float x, float y, float width, float height) {
        float safeWidth = Math.max(MIN_WIDTH, width);
        float safeHeight = Math.max(MIN_HEIGHT, height);
        float navigation = safeWidth * 33.0f / DESIGN_WIDTH;
        float collection = safeWidth * 101.0f / DESIGN_WIDTH;
        return new SolidMetrics(
                x,
                y,
                safeWidth,
                safeHeight,
                navigation,
                collection,
                safeWidth - navigation - collection,
                safeHeight * 24.0f / DESIGN_HEIGHT
        );
    }

    public float firstDividerX() {
        return x + navigationWidth - 1.0f;
    }

    public float secondDividerX() {
        return x + navigationWidth + collectionWidth - 1.0f;
    }

    public float detailX() {
        return x + navigationWidth + collectionWidth;
    }
}
