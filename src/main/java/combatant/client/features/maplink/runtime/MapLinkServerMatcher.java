/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.maplink.runtime;

import java.util.Locale;
import java.util.regex.Pattern;

public enum MapLinkServerMatcher {
    ;

    public static boolean matches(String configured, String currentAddress) {
        String matcher = normalize(configured);
        String current = normalize(currentAddress);
        if (matcher.isEmpty() || current.isEmpty()) return false;
        if (matcher.equals(current)) return true;

        String matcherHost = stripPort(matcher);
        String currentHost = stripPort(current);
        boolean matcherHasExplicitPort = hasExplicitPort(matcher);
        if (!matcherHasExplicitPort && matcherHost.equals(currentHost)) return true;

        if (matcher.indexOf('*') >= 0) {
            String candidate = matcherHasExplicitPort ? current : currentHost;
            String wildcard = matcherHasExplicitPort ? matcher : matcherHost;
            StringBuilder regex = new StringBuilder("^");
            String[] parts = wildcard.split("\\*", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) regex.append(".*");
                regex.append(Pattern.quote(parts[i]));
            }
            regex.append('$');
            return candidate.matches(regex.toString());
        }
        return false;
    }


    private static boolean hasExplicitPort(String address) {
        if (address == null || address.isBlank()) return false;
        if (address.startsWith("[")) {
            int close = address.indexOf(']');
            return close >= 0 && close + 1 < address.length() && address.charAt(close + 1) == ':';
        }
        int colon = address.lastIndexOf(':');
        if (colon <= 0 || colon == address.length() - 1) return false;
        for (int i = colon + 1; i < address.length(); i++) {
            if (!Character.isDigit(address.charAt(i))) return false;
        }
        return true;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String stripPort(String address) {
        if (address == null || address.isBlank()) return "";
        if (address.startsWith("[")) {
            int close = address.indexOf(']');
            return close > 0 ? address.substring(1, close) : address;
        }
        int colon = address.lastIndexOf(':');
        if (colon <= 0 || colon == address.length() - 1) return address;
        for (int i = colon + 1; i < address.length(); i++) {
            if (!Character.isDigit(address.charAt(i))) return address;
        }
        return address.substring(0, colon);
    }
}
