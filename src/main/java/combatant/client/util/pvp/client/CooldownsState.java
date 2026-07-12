/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp.client;

import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.PvpCooldowns;

/**
 * Глобальное хранилище кулдаунов и флагов PvP.
 */
public enum CooldownsState {
    ;
    public static final CooldownManager MANAGER = new CooldownManager();
    public static final PendingUseTracker PENDING = new PendingUseTracker();
    public static final long OPPONENT_GRACE_MS = 10_000L;

    public static boolean shouldTrackOpponents() {
        var mod = Modules.get(
                PvpCooldowns.class
        );
        if (mod == null || !mod.isSystemEnabled()) return false;
        return MANAGER.isPvpGraceActive(OPPONENT_GRACE_MS);
    }
}
