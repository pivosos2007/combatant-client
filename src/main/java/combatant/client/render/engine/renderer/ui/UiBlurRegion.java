/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import combatant.client.render.engine.renderer.ui.draw.UiRect;
import org.jetbrains.annotations.Nullable;

/**
 * Padded framebuffer-space blur workload in texture coordinates (origin at bottom-left).
 *
 * <p>The Kawase targets intentionally remain full-sized per level. Restricting only the work
 * region keeps every existing screen-UV consumer valid while avoiding fragment/compute work for
 * pixels that no glass/blur surface can sample.</p>
 */
record UiBlurRegion(int x, int y, int width, int height) {
    UiBlurRegion {
        x = Math.max(0, x);
        y = Math.max(0, y);
        width = Math.max(1, width);
        height = Math.max(1, height);
    }

    static UiBlurRegion full(int width, int height) {
        return new UiBlurRegion(0, 0, Math.max(1, width), Math.max(1, height));
    }

    static UiBlurRegion fromLogicalBounds(@Nullable UiRect bounds,
                                          int framebufferWidth,
                                          int framebufferHeight,
                                          float logicalWidth,
                                          float logicalHeight,
                                          int iterations,
                                          float offsetPx) {
        int fbW = Math.max(1, framebufferWidth);
        int fbH = Math.max(1, framebufferHeight);
        if (bounds == null || bounds.empty()) return full(fbW, fbH);

        float logicalW = logicalWidth > 0.0f && Float.isFinite(logicalWidth) ? logicalWidth : fbW;
        float logicalH = logicalHeight > 0.0f && Float.isFinite(logicalHeight) ? logicalHeight : fbH;
        float scaleX = fbW / logicalW;
        float scaleY = fbH / logicalH;
        int padding = kernelPaddingPx(iterations, offsetPx);

        int left = (int) Math.floor(bounds.x() * scaleX) - padding;
        int top = (int) Math.floor(bounds.y() * scaleY) - padding;
        int right = (int) Math.ceil((bounds.x() + bounds.width()) * scaleX) + padding;
        int bottom = (int) Math.ceil((bounds.y() + bounds.height()) * scaleY) + padding;

        left = clamp(left, 0, fbW);
        right = clamp(right, 0, fbW);
        top = clamp(top, 0, fbH);
        bottom = clamp(bottom, 0, fbH);
        if (right <= left || bottom <= top) return full(fbW, fbH);

        // Logical/UI coordinates are top-left based; texture/image coordinates are bottom-left based.
        int textureY = fbH - bottom;
        return new UiBlurRegion(left, textureY, right - left, bottom - top);
    }

    UiBlurRegion scaleTo(int fullWidth, int fullHeight, int targetWidth, int targetHeight) {
        int srcW = Math.max(1, fullWidth);
        int srcH = Math.max(1, fullHeight);
        int dstW = Math.max(1, targetWidth);
        int dstH = Math.max(1, targetHeight);

        int x0 = (int) Math.floor((double) x * dstW / srcW);
        int y0 = (int) Math.floor((double) y * dstH / srcH);
        int x1 = (int) Math.ceil((double) (x + width) * dstW / srcW);
        int y1 = (int) Math.ceil((double) (y + height) * dstH / srcH);

        x0 = clamp(x0, 0, dstW - 1);
        y0 = clamp(y0, 0, dstH - 1);
        x1 = clamp(x1, x0 + 1, dstW);
        y1 = clamp(y1, y0 + 1, dstH);
        return new UiBlurRegion(x0, y0, x1 - x0, y1 - y0);
    }

    boolean contains(UiBlurRegion other) {
        return other != null
                && x <= other.x
                && y <= other.y
                && x + width >= other.x + other.width
                && y + height >= other.y + other.height;
    }

    private static int kernelPaddingPx(int iterations, float offsetPx) {
        int n = Math.max(1, Math.min(8, iterations));
        double sampleOffset = Math.max(0.0, Float.isFinite(offsetPx) ? offsetPx : 1.0f) + 0.5;
        // Down path samples at full-resolution scales 1,2,4,... . The up path uses a 1.5x
        // spread and samples from scales 2^n, 2^(n-1), ... ,4. This is the conservative maximum
        // support radius of the complete chain, plus a small bilinear/AA guard.
        double downSupport = (1 << n) - 1.0;
        double upSupport = n > 1 ? (1 << (n + 1)) - 4.0 : 0.0;
        return Math.max(2, (int) Math.ceil(sampleOffset * (downSupport + 1.5 * upSupport) + 3.0));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
