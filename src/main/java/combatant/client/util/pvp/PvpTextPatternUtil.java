/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp;

import combatant.client.util.text.LegacyTextUtil;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

enum PvpTextPatternUtil {
    ;

    static String normalize(String raw) {
        if (raw == null) return "";
        String msg = LegacyTextUtil.stripLegacy(raw);
        return msg == null ? "" : msg.trim();
    }

    static boolean matchesAny(String raw, Iterable<String> patterns) {
        return match(raw, patterns) != null;
    }

    static Match match(String raw, Iterable<String> patterns) {
        String msg = normalize(raw);
        if (msg.isBlank() || patterns == null) return null;

        for (String line : patterns) {
            String pattern = normalizePattern(line);
            if (pattern == null) continue;
            Matcher matcher = compile(pattern);
            if (matcher == null) continue;
            matcher.reset(msg);
            if (!matcher.find()) continue;

            String opponent = firstNonBlank(group(matcher, "opponent"), group(matcher, 1));
            Float seconds = parseFloat(firstNonBlank(group(matcher, "seconds"), group(matcher, "time")));
            Integer health = parseInt(firstNonBlank(group(matcher, "hp"), group(matcher, "health")));
            return new Match(opponent, seconds, health);
        }

        return null;
    }

    private static Matcher compile(String pattern) {
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher("");
        } catch (PatternSyntaxException ignored) {
            return null;
        }
    }

    private static String normalizePattern(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        return s;
    }

    private static String group(Matcher matcher, String name) {
        if (matcher == null || name == null) return null;
        try {
            String value = matcher.group(name);
            return value == null ? null : value.trim();
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return null;
        }
    }

    private static String group(Matcher matcher, int index) {
        if (matcher == null || index <= 0) return null;
        try {
            if (matcher.groupCount() < index) return null;
            String value = matcher.group(index);
            return value == null ? null : value.trim();
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static Float parseFloat(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String norm = raw.trim().replace(',', '.');
        try {
            return Float.parseFloat(norm);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static Set<String> linkedSet(String... patterns) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (patterns != null) {
            for (String pattern : patterns) {
                if (pattern != null && !pattern.isBlank()) out.add(pattern);
            }
        }
        return out;
    }

    record Match(String opponentName, Float secondsLeft, Integer opponentHealth) {
    }
}
