/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium;

public record SodiumVisibilityStatsSnapshot(long queries,
                                            long accepts,
                                            long rejects,
                                            long bypasses,
                                            long unavailableBypasses,
                                            long optOutBypasses,
                                            long errors) {
    public static final SodiumVisibilityStatsSnapshot EMPTY = new SodiumVisibilityStatsSnapshot(0, 0, 0, 0, 0, 0, 0);
}
