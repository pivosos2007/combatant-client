/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.server;

import java.util.Locale;

/** Formats a position as text for the clipboard or chat. Game-free so it can be tested. */
public final class CoordFormatter {

    private CoordFormatter() {
    }

    public enum Format {
        /** {@code 12 64 -30} */
        SPACES,
        /** {@code 12, 64, -30} */
        COMMAS,
        /** {@code [12, 64, -30]} */
        BRACKETS,
        /** {@code X: 12 Y: 64 Z: -30} */
        LABELED,
        /** {@code 12 -30} (no height) */
        XZ,
        /** {@code /tp 12 64 -30} */
        TELEPORT_COMMAND
    }

    /**
     * @param decimals  digits after the point (0 floors to whole blocks, like the F3 block position)
     * @param dimension appended in parentheses when not null or blank
     */
    public static String format(Format format, double x, double y, double z, int decimals, String dimension) {
        String sx = number(x, decimals);
        String sy = number(y, decimals);
        String sz = number(z, decimals);
        String body = switch (format) {
            case SPACES -> sx + " " + sy + " " + sz;
            case COMMAS -> sx + ", " + sy + ", " + sz;
            case BRACKETS -> "[" + sx + ", " + sy + ", " + sz + "]";
            case LABELED -> "X: " + sx + " Y: " + sy + " Z: " + sz;
            case XZ -> sx + " " + sz;
            case TELEPORT_COMMAND -> "/tp " + sx + " " + sy + " " + sz;
        };
        if (dimension == null || dimension.isBlank() || format == Format.TELEPORT_COMMAND) return body;
        return body + " (" + dimension.strip() + ")";
    }

    static String number(double value, int decimals) {
        if (decimals <= 0) return Long.toString((long) Math.floor(value));
        return String.format(Locale.ROOT, "%." + Math.min(decimals, 6) + "f", value);
    }
}
