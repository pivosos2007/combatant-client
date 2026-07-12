/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.animation;

public final class AnimatedTextStyle {
    private final AnimatedTextTransition transition;
    private final long durationMs;
    private final float rollOffsetScale;
    private final float clipPaddingScale;
    private final boolean tabularDigits;
    private final boolean animateDigitsOnly;
    private final boolean bigGlyphs;

    public AnimatedTextStyle(AnimatedTextTransition transition,
                             long durationMs,
                             float rollOffsetScale,
                             float clipPaddingScale,
                             boolean tabularDigits,
                             boolean animateDigitsOnly,
                             boolean bigGlyphs) {
        this.transition = transition != null ? transition : AnimatedTextTransition.NONE;
        this.durationMs = Math.max(1L, durationMs);
        this.rollOffsetScale = Math.max(0.0f, rollOffsetScale);
        this.clipPaddingScale = Math.max(0.0f, clipPaddingScale);
        this.tabularDigits = tabularDigits;
        this.animateDigitsOnly = animateDigitsOnly;
        this.bigGlyphs = bigGlyphs;
    }

    public static AnimatedTextStyle clockLiquidGlass() {
        return new AnimatedTextStyle(
                AnimatedTextTransition.ROLL_UP,
                520L,
                0.58f,
                0.18f,
                true,
                true,
                true
        );
    }

    public AnimatedTextTransition transition() {
        return transition;
    }

    public long durationMs() {
        return durationMs;
    }

    public float rollOffsetScale() {
        return rollOffsetScale;
    }

    public float clipPaddingScale() {
        return clipPaddingScale;
    }

    public boolean tabularDigits() {
        return tabularDigits;
    }

    public boolean animateDigitsOnly() {
        return animateDigitsOnly;
    }

    public boolean bigGlyphs() {
        return bigGlyphs;
    }
}
