/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.finder;

import net.minecraft.world.level.biome.BiomeManager;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The server sends clients a SHA-256 hash of the world seed; the client keeps it inside its
 * BiomeManager. Hashing a candidate seed the same way proves or disproves it without knowing
 * the real one.
 */
class ServerSeedCheckTest {

    private static final long DONUT_SEED = 6608149111735331168L;

    private static BiomeManager managerFor(long worldSeed) {
        // Same as the client: the manager is constructed with the already-hashed seed.
        return new BiomeManager((x, y, z) -> null, BiomeManager.obfuscateSeed(worldSeed));
    }

    @Test
    void acceptsTheSeedTheServerHashed() {
        assertEquals(Optional.of(true), ServerSeedCheck.matches(managerFor(DONUT_SEED), DONUT_SEED));
        assertEquals(Optional.of(true), ServerSeedCheck.matches(managerFor(-42L), -42L));
    }

    @Test
    void rejectsAnyOtherSeed() {
        assertEquals(Optional.of(false), ServerSeedCheck.matches(managerFor(DONUT_SEED), DONUT_SEED + 1));
        assertEquals(Optional.of(false), ServerSeedCheck.matches(managerFor(DONUT_SEED), 0L));
    }
}
