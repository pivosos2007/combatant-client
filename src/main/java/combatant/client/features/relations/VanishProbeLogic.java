/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.relations;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Pure decision rules for {@link VanishProbe}; no Minecraft state. */
final class VanishProbeLogic {

    private VanishProbeLogic() {
    }

    /**
     * Names the server offered for tab completion that are not in the tab list. Selectors
     * ({@code @a}), the local player and blank entries are ignored. Result keeps the server's
     * spelling and the order of first appearance.
     */
    static Set<String> hiddenNames(Collection<String> suggestions, Collection<String> tabListNames, String selfName) {
        Set<String> known = new HashSet<>();
        for (String name : tabListNames) known.add(name.toLowerCase(Locale.ROOT));
        if (selfName != null) known.add(selfName.toLowerCase(Locale.ROOT));

        Set<String> hidden = new LinkedHashSet<>();
        for (String raw : suggestions) {
            if (raw == null) continue;
            String name = raw.trim();
            if (!isPlayerName(name)) continue;
            if (!known.contains(name.toLowerCase(Locale.ROOT))) hidden.add(name);
        }
        return hidden;
    }

    /** Java usernames are 3-16 chars of [A-Za-z0-9_]; this also drops selectors and command words. */
    static boolean isPlayerName(String text) {
        int length = text.length();
        if (length < 3 || length > 16) return false;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) return false;
        }
        return true;
    }

    /**
     * Names hidden in {@code confirmations} probes in a row; {@code streaks} carries the counts between probes
     * (lower-case keys). Names that stopped being hidden are dropped from it.
     */
    static Set<String> confirmed(Set<String> hidden, java.util.Map<String, Integer> streaks, int confirmations) {
        Set<String> keys = new HashSet<>();
        for (String name : hidden) keys.add(name.toLowerCase(Locale.ROOT));
        streaks.keySet().retainAll(keys);
        Set<String> out = new LinkedHashSet<>();
        for (String name : hidden) {
            if (streaks.merge(name.toLowerCase(Locale.ROOT), 1, Integer::sum) >= confirmations) out.add(name);
        }
        return out;
    }
}
