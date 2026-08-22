/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat;

import net.minecraft.network.chat.Component;

/** One logical incoming chat message. Repeated adjacent messages stay one logical line. */
public final class ChatLine {
    private final Component rawText;
    private final Component displayText;
    private final long timestampMs;
    private final int repeatCount;

    public ChatLine(Component text, long timestampMs) {
        this(text, timestampMs, 1);
    }

    public ChatLine(Component text, long timestampMs, int repeatCount) {
        this.rawText = text == null ? Component.empty() : text;
        this.timestampMs = timestampMs;
        this.repeatCount = Math.max(1, repeatCount);
        this.displayText = this.repeatCount > 1
                ? this.rawText.copy().append(Component.literal(" [x" + this.repeatCount + "]"))
                : this.rawText;
    }

    public Component text() {
        return displayText;
    }

    public Component rawText() {
        return rawText;
    }

    public long timestampMs() {
        return timestampMs;
    }

    public int repeatCount() {
        return repeatCount;
    }

    public ChatLine repeated(long latestTimestampMs) {
        return new ChatLine(rawText, latestTimestampMs, repeatCount + 1);
    }

    public float ageSeconds() {
        return (System.currentTimeMillis() - timestampMs) / 1000f;
    }
}
