/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public enum ChatNameUtil {
    ;

    private static final Pattern NICK = Pattern.compile("^[A-Za-z0-9_]{3,32}$");

    public static String extractNick(String line) {
        List<String> nicks = extractNicks(line);
        return nicks.isEmpty() ? "" : nicks.get(0);
    }

    /**
     * Собираем все никоподобные токены в префиксе сообщения (до первого разделителя >, : или »),
     * игнорируя явные теги в квадратных скобках и разделители вида "*".
     */
    public static List<String> extractNicks(String line) {
        if (line == null) return Collections.emptyList();

        String s = LegacyTextUtil.stripLegacy(line).strip();
        if (s.isEmpty()) return Collections.emptyList();

        int delim = findFirstDelimiter(s);
        if (delim >= 0) {
            s = s.substring(0, delim);
        }

        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
            if (i >= s.length()) break;

            char ch = s.charAt(i);
            if (ch == '[') {
                int end = s.indexOf(']', i + 1);
                if (end < 0) break;
                i = end + 1;
                continue; // тег/клан пропускаем
            }
            if (ch == '*') { // разделитель между ником и кланом
                i++;
                continue;
            }

            int start = i;
            while (i < s.length() && !Character.isWhitespace(s.charAt(i))) i++;
            String token = s.substring(start, i);
            token = cutAtSpecial(token);

            String cleaned = normalizeNickCandidate(token);
            if (isNickLike(cleaned) && !containsIgnoreCase(result, cleaned)) {
                result.add(cleaned);
            }
        }
        return result;
    }

    public static boolean isNickLike(String token) {
        if (token == null) return false;
        String cleaned = normalizeNickCandidate(token);
        return !cleaned.isEmpty() && NICK.matcher(cleaned).matches();
    }

    public static String normalizeNickCandidate(String token) {
        if (token == null) return "";
        return token.replaceAll("[^A-Za-z0-9_]", "");
    }

    private static int findFirstDelimiter(String s) {
        int min = Integer.MAX_VALUE;
        char[] delims = new char[]{'>', ':', '»'};
        for (char d : delims) {
            int idx = s.indexOf(d);
            if (idx >= 0 && idx < min) {
                min = idx;
            }
        }
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    private static String cutAtSpecial(String token) {
        int cut = token.length();
        int bracket = token.indexOf('[');
        int star = token.indexOf('*');
        if (bracket >= 0) cut = Math.min(cut, bracket);
        if (star >= 0) cut = Math.min(cut, star);
        if (cut < token.length()) {
            return token.substring(0, cut);
        }
        return token;
    }

    private static boolean containsIgnoreCase(List<String> list, String nick) {
        for (String s : list) {
            if (s.equalsIgnoreCase(nick)) return true;
        }
        return false;
    }
}
