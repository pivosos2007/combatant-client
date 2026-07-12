/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.animation;

final class AnimatedGlyphSlot {
    private char current;
    private char previous;
    private long animationStartMs;
    private boolean animating;

    AnimatedGlyphSlot(char initial) {
        this.current = initial;
        this.previous = initial;
        this.animationStartMs = 0L;
        this.animating = false;
    }

    char current() {
        return current;
    }

    char previous() {
        return previous;
    }

    boolean isAnimating(long nowMs, AnimatedTextStyle style) {
        if (!animating) return false;
        if (progress(nowMs, style) >= 1.0f) {
            animating = false;
            previous = current;
            return false;
        }
        return true;
    }

    float progress(long nowMs, AnimatedTextStyle style) {
        if (!animating || style == null) return 1.0f;
        return AnimationUtility.clamp01((nowMs - animationStartMs) / (float) style.durationMs());
    }

    void set(char next, long nowMs, AnimatedTextStyle style) {
        if (next == current) return;

        boolean animate = shouldAnimate(current, next, style);
        previous = current;
        current = next;
        if (animate) {
            animationStartMs = nowMs;
            animating = true;
        } else {
            animationStartMs = 0L;
            animating = false;
            previous = current;
        }
    }

    private static boolean shouldAnimate(char oldGlyph, char newGlyph, AnimatedTextStyle style) {
        if (style == null || style.transition() == AnimatedTextTransition.NONE) return false;
        if (oldGlyph == 0 || newGlyph == 0) return false;
        if (!style.animateDigitsOnly()) return true;
        return Character.isDigit(oldGlyph) && Character.isDigit(newGlyph);
    }
}
