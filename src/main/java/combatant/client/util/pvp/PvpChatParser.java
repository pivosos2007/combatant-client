/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp;

import java.util.Set;

public enum PvpChatParser {
    ;
    private static final Set<String> DEFAULT_GIVE_PATTERNS = PvpTextPatternUtil.linkedSet(
            "Вы\\s+бросили\\s+PvP\\s+вызов\\s+игроку\\s+(?<opponent>[A-Za-z0-9_]{3,32})"
    );
    private static final Set<String> DEFAULT_RECEIVE_PATTERNS = PvpTextPatternUtil.linkedSet(
            "Вам\\s+был\\s+брошен\\s+PvP\\s+вызов\\s+игроком\\s+(?<opponent>[A-Za-z0-9_]{3,32})"
    );
    private static final Set<String> DEFAULT_ACTIVE_PATTERNS = PvpTextPatternUtil.linkedSet(
            "Вы\\s+вошли\\s+в\\s+режим\\s+PVP",
            "PvP\\s+начат"
    );
    private static final Set<String> DEFAULT_EXIT_PATTERNS = PvpTextPatternUtil.linkedSet(
            "больше\\s+не\\s+находитесь\\s+в\\s+бою",
            "больше\\s+не\\s+в\\s+бою",
            "PVP\\s+окончено"
    );

    public static Result parse(String raw) {
        return parse(raw, DEFAULT_GIVE_PATTERNS, DEFAULT_RECEIVE_PATTERNS, DEFAULT_ACTIVE_PATTERNS, DEFAULT_EXIT_PATTERNS);
    }

    public static Result parse(String raw,
                               Iterable<String> givePatterns,
                               Iterable<String> receivePatterns,
                               Iterable<String> activePatterns,
                               Iterable<String> exitPatterns) {
        if (raw == null || raw.isBlank()) return null;

        if (PvpTextPatternUtil.matchesAny(raw, exitPatterns)) {
            return Result.exited();
        }

        PvpTextPatternUtil.Match give = PvpTextPatternUtil.match(raw, givePatterns);
        if (give != null) {
            return new Result(PvpTagSource.GIVEN, give.opponentName(), give.secondsLeft(), give.opponentHealth(), true, false);
        }

        PvpTextPatternUtil.Match receive = PvpTextPatternUtil.match(raw, receivePatterns);
        if (receive != null) {
            return new Result(PvpTagSource.RECEIVED, receive.opponentName(), receive.secondsLeft(), receive.opponentHealth(), true, false);
        }

        PvpTextPatternUtil.Match active = PvpTextPatternUtil.match(raw, activePatterns);
        if (active != null) {
            return new Result(PvpTagSource.UNKNOWN, active.opponentName(), active.secondsLeft(), active.opponentHealth(), true, false);
        }

        return null;
    }

    public static Set<String> defaultGivePatterns() {
        return DEFAULT_GIVE_PATTERNS;
    }

    public static Set<String> defaultReceivePatterns() {
        return DEFAULT_RECEIVE_PATTERNS;
    }

    public static Set<String> defaultActivePatterns() {
        return DEFAULT_ACTIVE_PATTERNS;
    }

    public static Set<String> defaultExitPatterns() {
        return DEFAULT_EXIT_PATTERNS;
    }

    public record Result(PvpTagSource source,
                         String opponentName,
                         Float secondsLeft,
                         Integer opponentHealth,
                         boolean active,
                         boolean exit) {
        public static Result exited() {
            return new Result(PvpTagSource.UNKNOWN, null, null, null, false, true);
        }
    }
}
