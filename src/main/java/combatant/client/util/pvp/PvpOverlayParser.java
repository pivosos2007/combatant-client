/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp;

import java.util.Set;

public enum PvpOverlayParser {
    ;
    private static final Set<String> DEFAULT_ACTIVE_PATTERNS = PvpTextPatternUtil.linkedSet(
            "^\\[PvP\\]\\s*(?<seconds>[0-9]+(?:[.,][0-9]+)?)\\s*сек\\.?\\s*.*?\\s*(?<opponent>[^()]+?)\\s*\\((?<hp>\\d+)"
    );

    private static final Set<String> DEFAULT_EXIT_PATTERNS = PvpTextPatternUtil.linkedSet(
            "не\\s+в\\s+бою",
            "больше\\s+не\\s+в\\s+бою",
            "не\\s+находитесь\\s+в\\s+бою"
    );

    public static Result parse(String raw) {
        return parse(raw, DEFAULT_ACTIVE_PATTERNS, DEFAULT_EXIT_PATTERNS);
    }

    public static Result parse(String raw,
                               Iterable<String> activePatterns,
                               Iterable<String> exitPatterns) {
        if (raw == null || raw.isBlank()) return null;

        if (PvpTextPatternUtil.matchesAny(raw, exitPatterns)) {
            return Result.exited();
        }

        PvpTextPatternUtil.Match match = PvpTextPatternUtil.match(raw, activePatterns);
        if (match == null) return null;

        return new Result(true, match.secondsLeft(), match.opponentName(), match.opponentHealth(), false);
    }

    public static Set<String> defaultActivePatterns() {
        return DEFAULT_ACTIVE_PATTERNS;
    }

    public static Set<String> defaultExitPatterns() {
        return DEFAULT_EXIT_PATTERNS;
    }

    public record Result(boolean active,
                         Float secondsLeft,
                         String opponentName,
                         Integer opponentHealth,
                         boolean exit) {
        public static Result exited() {
            return new Result(false, null, null, null, true);
        }
    }
}
