/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Pure decision rules for {@link AntiVanish}; no Minecraft state. */
final class AntiVanishLogic {

    private AntiVanishLogic() {
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
     * A tab-list removal is a vanish candidate (rather than a logout) when the player's entity
     * is still loaded after the grace period; real logouts remove the entity in the same tick.
     */
    static boolean desyncAfterRemoval(boolean entityStillLoaded, boolean backInTabList) {
        return entityStillLoaded && !backInTabList;
    }
}
