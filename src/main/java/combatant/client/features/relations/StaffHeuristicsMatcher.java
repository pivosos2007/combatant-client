/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.relations;

import combatant.client.util.text.LegacyTextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

enum StaffHeuristicsMatcher {
    ;

    static boolean matches(String name,
                           String displayName,
                           Set<String> prefixes,
                           Set<String> suffixes,
                           Set<String> contains) {
        String plainName = normalizeText(name);
        String display = normalizeText(displayName);

        if (matchesText(plainName, prefixes, suffixes, contains)) return true;
        if (display.isBlank()) return false;
        if (matchesText(display, prefixes, suffixes, contains)) return true;
        return matchesAffixesAroundName(plainName, display, prefixes, suffixes);
    }

    private static boolean matchesText(String text,
                                       Set<String> prefixes,
                                       Set<String> suffixes,
                                       Set<String> contains) {
        if (text.isBlank()) return false;
        for (String prefix : prefixes) {
            String rule = normalizeText(prefix);
            if (!rule.isBlank() && startsWithRule(text, rule)) return true;
        }
        for (String suffix : suffixes) {
            String rule = normalizeText(suffix);
            if (!rule.isBlank() && endsWithRule(text, rule)) return true;
        }
        for (String needle : contains) {
            String rule = normalizeText(needle);
            if (!rule.isBlank() && containsRule(text, rule)) return true;
        }
        return false;
    }

    private static boolean matchesAffixesAroundName(String plainName,
                                                    String display,
                                                    Set<String> prefixes,
                                                    Set<String> suffixes) {
        if (plainName.isBlank()) return false;

        int from = 0;
        while (from < display.length()) {
            int idx = display.indexOf(plainName, from);
            if (idx < 0) return false;

            String beforeName = display.substring(0, idx);
            String afterName = display.substring(idx + plainName.length());
            if (matchesRegion(beforeName, prefixes) || matchesRegion(afterName, suffixes)) {
                return true;
            }

            from = idx + Math.max(plainName.length(), 1);
        }
        return false;
    }

    private static boolean matchesRegion(String text, Set<String> rules) {
        if (text.isBlank()) return false;
        for (String raw : rules) {
            String rule = normalizeText(raw);
            if (!rule.isBlank() && containsRule(text, rule)) return true;
        }
        return false;
    }

    private static boolean startsWithRule(String text, String rule) {
        if (text.startsWith(rule)) return true;
        String compactRule = compactKey(rule);
        return !compactRule.isBlank() && compactKey(text).startsWith(compactRule);
    }

    private static boolean endsWithRule(String text, String rule) {
        if (text.endsWith(rule)) return true;
        String compactRule = compactKey(rule);
        return !compactRule.isBlank() && compactKey(text).endsWith(compactRule);
    }

    private static boolean containsRule(String text, String rule) {
        return text.contains(rule) || containsCompactTokenSequence(text, rule);
    }

    private static boolean containsCompactTokenSequence(String text, String rule) {
        String compactRule = compactKey(rule);
        if (compactRule.isBlank()) return false;

        List<String> tokens = compactTokens(text);
        for (int start = 0; start < tokens.size(); start++) {
            StringBuilder joined = new StringBuilder();
            for (int i = start; i < tokens.size(); i++) {
                joined.append(tokens.get(i));
                if (joined.length() >= compactRule.length()) {
                    if (joined.toString().equals(compactRule)) return true;
                    break;
                }
            }
        }
        return false;
    }

    private static String compactKey(String text) {
        if (text == null || text.isBlank()) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (String token : compactTokens(text)) {
            out.append(token);
        }
        return out.toString();
    }

    private static List<String> compactTokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;

        StringBuilder token = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                token.append(ch);
                continue;
            }
            if (!token.isEmpty()) {
                out.add(token.toString());
                token.setLength(0);
            }
        }
        if (!token.isEmpty()) {
            out.add(token.toString());
        }
        return out;
    }

    private static String normalizeText(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return LegacyTextUtil.stripLegacy(raw).trim().toLowerCase(Locale.ROOT);
    }
}
