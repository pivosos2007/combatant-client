package combatant.client.compat.seedcracker;

import kaptainwutax.seedcrackerX.api.SeedCrackerAPI;

/** Fabric discovers this bridge via SeedCrackerX's existing extension entrypoint. */
public final class SeedCrackerEntrypoint implements SeedCrackerAPI {
    public SeedCrackerEntrypoint() { SeedCrackerIntegration.onEntrypoint(); }
    @Override public void pushWorldSeed(long seed) { SeedCrackerRuntime.onRecovered(seed); }
}
