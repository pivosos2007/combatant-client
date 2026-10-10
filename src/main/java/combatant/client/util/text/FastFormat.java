/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.text;

/**
 * Number formatting for text that is rebuilt every frame (HUD clocks, cooldown timers, ESP health).
 *
 * <p>{@link String#format} parses its pattern, boxes every argument and builds a {@code Formatter} on each
 * call, which showed up as a steady allocation source in render-thread profiles. These helpers produce the
 * same text for the patterns the client uses ({@code %02d}, {@code %d:%02d}, {@code %.1f}) from plain
 * integer math.</p>
 */
public enum FastFormat {
    ;

    private static final String[] TWO_DIGITS = new String[100];

    static {
        for (int i = 0; i < TWO_DIGITS.length; i++) {
            TWO_DIGITS[i] = i < 10 ? "0" + i : Integer.toString(i);
        }
    }

    /** {@code %02d}: cached for 0..99; anything else prints its plain digits, exactly like the format pattern. */
    public static String pad2(long value) {
        if (value >= 0L && value < 100L) {
            return TWO_DIGITS[(int) value];
        }
        return Long.toString(value);
    }

    /** {@code %02d:%02d}. */
    public static String clock(long first, long second) {
        return pad2(first) + ":" + pad2(second);
    }

    /** {@code %02d:%02d:%02d}. */
    public static String clock(long first, long second, long third) {
        return pad2(first) + ":" + pad2(second) + ":" + pad2(third);
    }

    /** {@code %d:%02d}. */
    public static String minutesSeconds(long minutes, long seconds) {
        return minutes + ":" + pad2(seconds);
    }

    /** {@code %.1f} (locale independent, half-up like {@link String#format}). */
    public static String oneDecimal(double value) {
        return fixed(value, 1);
    }

    /** {@code %.2f} (locale independent, half-up like {@link String#format}). */
    public static String twoDecimals(double value) {
        return fixed(value, 2);
    }

    /** {@code %.0f}. */
    public static String noDecimals(double value) {
        if (!Double.isFinite(value)) return Double.toString(value);
        long rounded = Math.round(Math.abs(value));
        return (value < 0.0 && rounded != 0L) ? "-" + rounded : Long.toString(rounded);
    }

    private static String fixed(double value, int decimals) {
        if (!Double.isFinite(value)) return Double.toString(value);
        long scale = decimals == 1 ? 10L : 100L;
        double magnitude = Math.abs(value);
        // Values this large lose the fractional digits in a double anyway, and the long math would overflow.
        if (magnitude >= 1.0E15) return String.format(java.util.Locale.ROOT, "%." + decimals + "f", value);
        long scaled = Math.round(magnitude * scale);
        long whole = scaled / scale;
        long fraction = scaled % scale;
        StringBuilder out = new StringBuilder(12);
        if (value < 0.0 && scaled != 0L) out.append('-');
        out.append(whole).append('.');
        if (decimals == 2 && fraction < 10L) out.append('0');
        out.append(fraction);
        return out.toString();
    }
}
