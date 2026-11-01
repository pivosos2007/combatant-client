package combatant.client.compat.seedcracker;

/** Stable immutable seed result; no third-party types are exposed to consumers. */
public record SeedSnapshot(long worldSeed, String server, String dimension, String worldgen,
                           Origin origin, long savedAtMillis) {
    public enum Origin { CRACKED, MANUAL, CANDIDATE }
}
