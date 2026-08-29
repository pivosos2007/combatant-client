/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat;

import combatant.client.features.gui.chat.rich.BetterChatMessage;
import combatant.client.features.gui.chat.rich.TextNode;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

/** One logical incoming chat message. Repeated adjacent messages stay one logical line. */
public final class ChatLine {
    private static final int[] REPEAT_GRADIENT_LEFT = {
            0x66BFFF, // x2   blue
            0x55DCC7, // x4   cyan/teal
            0x7EE36B, // x8   green
            0xFFD166, // x16  yellow
            0xFF9F4A, // x32  orange
            0xFF5F5F, // x64  red
            0xE45CFF, // x128 magenta
            0xB26CFF  // x256+ violet
    };
    private static final int[] REPEAT_GRADIENT_RIGHT = {
            0x7BE7FF,
            0x8EF0A5,
            0xCDEC67,
            0xFFE98A,
            0xFFC45C,
            0xFF8B7F,
            0xFF6EB8,
            0xE38BFF
    };

    private final BetterChatMessage rawMessage;
    private final BetterChatMessage displayMessage;
    private final long timestampMs;
    private final int repeatCount;

    public ChatLine(Component text, long timestampMs) {
        this(BetterChatMessage.text(text), timestampMs, 1);
    }

    public ChatLine(Component text, long timestampMs, int repeatCount) {
        this(BetterChatMessage.text(text), timestampMs, repeatCount);
    }

    public ChatLine(BetterChatMessage message, long timestampMs) {
        this(message, timestampMs, 1);
    }

    public ChatLine(BetterChatMessage message, long timestampMs, int repeatCount) {
        this.rawMessage = message == null ? BetterChatMessage.empty() : message;
        this.timestampMs = timestampMs;
        this.repeatCount = Math.max(1, repeatCount);
        this.displayMessage = this.repeatCount > 1
                ? this.rawMessage.append(new TextNode(repeatBadge(this.repeatCount)))
                : this.rawMessage;
    }


    private static Component repeatBadge(int count) {
        String token = "[x" + count + "]";
        int gradientStart = repeatGradientColor(count, REPEAT_GRADIENT_LEFT);
        int gradientEnd = repeatGradientColor(count, REPEAT_GRADIENT_RIGHT);
        MutableComponent result = Component.literal(" ");
        int length = Math.max(1, token.length() - 1);

        for (int i = 0; i < token.length(); i++) {
            float t = i / (float) length;
            int rgb = mixRgb(gradientStart, gradientEnd, t);
            result.append(Component.literal(String.valueOf(token.charAt(i)))
                    .withStyle(style -> style.withColor(TextColor.fromRgb(rgb))));
        }
        return result;
    }

    /**
     * Logarithmic repeat scale: common duplicates stay cool, while increasingly
     * spammy counts move through green/yellow/orange/red into magenta. Each level
     * still interpolates, so e.g. x5 and x7 are not exactly the same color.
     */
    private static int repeatGradientColor(int count, int[] anchors) {
        double level = Math.log(Math.max(2, count)) / Math.log(2.0) - 1.0;
        int index = Math.max(0, Math.min(anchors.length - 1, (int) Math.floor(level)));
        int next = Math.min(anchors.length - 1, index + 1);
        float t = (float) Math.max(0.0, Math.min(1.0, level - Math.floor(level)));
        return mixRgb(anchors[index], anchors[next], t);
    }

    private static int mixRgb(int from, int to, float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        int fr = (from >>> 16) & 0xFF;
        int fg = (from >>> 8) & 0xFF;
        int fb = from & 0xFF;
        int tr = (to >>> 16) & 0xFF;
        int tg = (to >>> 8) & 0xFF;
        int tb = to & 0xFF;
        int r = Math.round(fr + (tr - fr) * clamped);
        int g = Math.round(fg + (tg - fg) * clamped);
        int b = Math.round(fb + (tb - fb) * clamped);
        return (r << 16) | (g << 8) | b;
    }

    public Component text() {
        return displayMessage.accessibleComponent();
    }

    public Component rawText() {
        return rawMessage.accessibleComponent();
    }

    public BetterChatMessage message() {
        return displayMessage;
    }

    public BetterChatMessage rawMessage() {
        return rawMessage;
    }

    public long timestampMs() {
        return timestampMs;
    }

    public int repeatCount() {
        return repeatCount;
    }

    public ChatLine repeated(long latestTimestampMs) {
        return new ChatLine(rawMessage, latestTimestampMs, repeatCount + 1);
    }

    public float ageSeconds() {
        return (System.currentTimeMillis() - timestampMs) / 1000f;
    }
}
