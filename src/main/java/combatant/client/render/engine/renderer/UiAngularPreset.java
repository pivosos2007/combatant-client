/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer;

/**
 * Semantic angular deformations for ordinary UI primitives. This is deliberately a
 * {@link RenderWarp} factory rather than a separate shape family: an unwarped primitive keeps
 * its compact vertex layout, while an active preset is routed through the existing warped path.
 */
public enum UiAngularPreset {
    TRAILING_DIAGONAL {
        @Override
        RenderWarp create(float x, float y, float width, float height, float cut) {
            return corners(x, y, width, height, 0, 0, -cut, 0, 0, 0, 0, 0);
        }
    },
    LEADING_TRAILING {
        @Override
        RenderWarp create(float x, float y, float width, float height, float cut) {
            return corners(x, y, width, height, cut, 0, -cut, 0, 0, 0, 0, 0);
        }
    },
    ASYMMETRIC_TOP_BOTTOM {
        @Override
        RenderWarp create(float x, float y, float width, float height, float cut) {
            return corners(x, y, width, height, cut, 0, 0, 0, -cut, 0, 0, 0);
        }
    },
    COMPACT_HUD_CHIP {
        @Override
        RenderWarp create(float x, float y, float width, float height, float cut) {
            float leading = cut * 0.65f;
            return corners(x, y, width, height, leading, 0, -cut, 0, -leading, 0, cut, 0);
        }
    },
    PROGRESS_TRAILING_EDGE {
        @Override
        RenderWarp create(float x, float y, float width, float height, float cut) {
            return corners(x, y, width, height, 0, 0, -cut, 0, 0, 0, 0, 0);
        }
    };

    public RenderWarp warp(float x, float y, float width, float height, float cut) {
        if (Math.abs(width) <= 0.0001f || Math.abs(height) <= 0.0001f) return RenderWarp.IDENTITY;
        float safeCut = Math.min(Math.abs(cut), Math.min(Math.abs(width), Math.abs(height)) * 0.49f);
        if (safeCut <= 0.0001f) return RenderWarp.IDENTITY;
        return create(x, y, width, height, safeCut);
    }

    abstract RenderWarp create(float x, float y, float width, float height, float cut);

    private static RenderWarp corners(float x, float y, float width, float height,
                                      float tlX, float tlY, float trX, float trY,
                                      float brX, float brY, float blX, float blY) {
        return RenderWarp.corners(x, y, width, height,
                tlX, tlY, trX, trY, brX, brY, blX, blY);
    }
}
