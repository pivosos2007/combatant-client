/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.seedcracker;

/** Stable immutable seed result; no third-party types are exposed to consumers. */
public record SeedSnapshot(long worldSeed, String server, String dimension, String worldgen,
                           Origin origin, long savedAtMillis) {
    public enum Origin { CRACKED, MANUAL, CANDIDATE }
}
