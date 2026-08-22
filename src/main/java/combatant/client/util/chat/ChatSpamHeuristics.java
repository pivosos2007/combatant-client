/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.chat;

import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Small, allocation-conscious chat heuristics shared by BetterChat storage/filtering.
 * The detector is intentionally conservative: it only suppresses strongly malformed/noisy
 * payloads and leaves ordinary player messages, links and server formatting alone.
 */
public enum ChatSpamHeuristics {
    ;

    private static final int MIN_NOISE_LENGTH = 16;
    private static final int LONG_TOKEN_LENGTH = 32;
    private static final int MAX_REPEAT_RUN = 8;

    public static boolean sameMessage(Component first, Component second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (Objects.equals(first, second)) return true;
        return normalizeVisible(first.getString()).equals(normalizeVisible(second.getString()));
    }

    public static boolean isLikelyGibberish(Component component) {
        return component != null && isLikelyGibberish(component.getString());
    }

    public static boolean isLikelyGibberish(String raw) {
        if (raw == null) return false;
        String text = raw.strip();
        if (text.length() < MIN_NOISE_LENGTH) return false;

        int codePoints = 0;
        int letters = 0;
        int digits = 0;
        int whitespace = 0;
        int punctuationOrSymbols = 0;
        int combining = 0;
        int controls = 0;
        int vowels = 0;
        int maxRun = 0;
        int run = 0;
        int previous = -1;
        int latin = 0;
        int cyrillic = 0;
        int scriptSwitches = 0;
        Character.UnicodeScript lastLetterScript = null;
        Set<Integer> distinct = new HashSet<>();

        int longestToken = 0;
        int currentToken = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            codePoints++;
            distinct.add(Character.toLowerCase(cp));

            if (cp == previous) {
                run++;
            } else {
                previous = cp;
                run = 1;
            }
            maxRun = Math.max(maxRun, run);

            int type = Character.getType(cp);
            if (Character.isISOControl(cp) && !Character.isWhitespace(cp)) {
                controls++;
            }
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                combining++;
            }

            if (Character.isLetter(cp)) {
                letters++;
                currentToken++;
                Character.UnicodeScript script = Character.UnicodeScript.of(cp);
                if (script == Character.UnicodeScript.LATIN) latin++;
                if (script == Character.UnicodeScript.CYRILLIC) cyrillic++;
                if ((script == Character.UnicodeScript.LATIN || script == Character.UnicodeScript.CYRILLIC)
                        && lastLetterScript != null && script != lastLetterScript) {
                    scriptSwitches++;
                }
                if (script == Character.UnicodeScript.LATIN || script == Character.UnicodeScript.CYRILLIC) {
                    lastLetterScript = script;
                }
                if (isVowel(cp)) vowels++;
            } else if (Character.isDigit(cp)) {
                digits++;
                currentToken++;
            } else if (Character.isWhitespace(cp)) {
                whitespace++;
                longestToken = Math.max(longestToken, currentToken);
                currentToken = 0;
            } else {
                punctuationOrSymbols++;
                currentToken++;
            }
        }
        longestToken = Math.max(longestToken, currentToken);
        if (codePoints <= 0) return false;

        if (controls > 0) return true;
        if (maxRun >= MAX_REPEAT_RUN) return true;
        if (combining >= 8 && combining * 3 >= codePoints) return true;

        int visible = Math.max(1, codePoints - whitespace);
        if (visible >= 16 && punctuationOrSymbols * 100 >= visible * 72) return true;

        // Mixed-script garbage such as rapid Cyrillic/Latin homoglyph alternation. Normal names
        // containing one or two foreign characters do not reach this threshold.
        if (latin >= 6 && cyrillic >= 6 && scriptSwitches >= 8) return true;

        // Long random-looking alpha/alphanumeric blobs. Keep the threshold high so UUIDs, short
        // hashes, ordinary URLs and player names are not filtered.
        if (longestToken >= LONG_TOKEN_LENGTH && letters >= 20) {
            float vowelRatio = vowels / (float) Math.max(1, letters);
            float distinctRatio = distinct.size() / (float) Math.max(1, visible);
            float alphaNumRatio = (letters + digits) / (float) visible;
            if (alphaNumRatio >= 0.86f && vowelRatio <= 0.08f && distinctRatio >= 0.36f) {
                return true;
            }
        }

        return hasRepeatedShortPattern(text);
    }

    private static boolean hasRepeatedShortPattern(String text) {
        String compact = text.replace(" ", "").replace("\t", "");
        if (compact.length() < 18) return false;
        int maxPattern = Math.min(12, compact.length() / 5);
        for (int patternLength = 1; patternLength <= maxPattern; patternLength++) {
            int repeats = 1;
            for (int offset = patternLength; offset + patternLength <= compact.length(); offset += patternLength) {
                if (compact.regionMatches(true, 0, compact, offset, patternLength)) {
                    repeats++;
                } else {
                    break;
                }
            }
            if (repeats >= 5 && repeats * patternLength >= 18) return true;
        }
        return false;
    }

    private static boolean isVowel(int cp) {
        return switch (Character.toLowerCase(cp)) {
            case 'a', 'e', 'i', 'o', 'u', 'y',
                    'а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я' -> true;
            default -> false;
        };
    }

    private static String normalizeVisible(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp)) {
                pendingSpace = out.length() > 0;
                continue;
            }
            if (pendingSpace) {
                out.append(' ');
                pendingSpace = false;
            }
            out.appendCodePoint(cp);
        }
        return out.toString().strip();
    }
}
