/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.seedcracker;

import kaptainwutax.seedcrackerX.api.SeedCrackerAPI;

/** Fabric discovers this bridge via SeedCrackerX's existing extension entrypoint. */
public final class SeedCrackerEntrypoint implements SeedCrackerAPI {
    public SeedCrackerEntrypoint() { SeedCrackerIntegration.onEntrypoint(); }
    @Override public void pushWorldSeed(long seed) { SeedCrackerRuntime.onRecovered(seed); }
}
