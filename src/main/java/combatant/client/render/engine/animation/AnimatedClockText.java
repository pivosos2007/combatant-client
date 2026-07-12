/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.animation;

import combatant.client.render.engine.text.TextRenderer;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class AnimatedClockText {
    private static final DateTimeFormatter FORMAT_MINUTES = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FORMAT_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AnimatedTextLine line = new AnimatedTextLine();
    private AnimatedTextStyle style = AnimatedTextStyle.clockLiquidGlass();
    private boolean showSeconds;

    public void setStyle(AnimatedTextStyle style) {
        this.style = style != null ? style : AnimatedTextStyle.clockLiquidGlass();
    }

    public void update(LocalTime time, boolean showSeconds, long nowMs) {
        this.showSeconds = showSeconds;
        LocalTime safeTime = time != null ? time : LocalTime.now();
        line.setText(safeTime.format(showSeconds ? FORMAT_SECONDS : FORMAT_MINUTES), nowMs, style);
    }

    public String text() {
        return line.text();
    }

    public boolean showSeconds() {
        return showSeconds;
    }

    public float width(TextRenderer renderer, float size) {
        return line.width(renderer, size, style);
    }

    public float height(TextRenderer renderer, float size) {
        return line.height(renderer, size, style);
    }

    public void renderLiquidGlassCentered(TextRenderer renderer,
                                          float centerX,
                                          float y,
                                          float size,
                                          int argb,
                                          long nowMs) {
        line.renderLiquidGlassCentered(renderer, centerX, y, size, argb, nowMs, style);
    }
}
