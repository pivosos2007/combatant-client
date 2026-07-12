/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp.opponents;

import java.util.UUID;

public record TotemPopSnapshot(UUID playerId,
                               String name,
                               int count,
                               long firstPopMs,
                               long lastPopMs) {
    public static TotemPopSnapshot empty(UUID playerId) {
        return new TotemPopSnapshot(playerId, null, 0, 0L, 0L);
    }

    public boolean visible() {
        return count > 0;
    }

    public String compactText() {
        return count > 0 ? String.valueOf(count) : "";
    }
}
