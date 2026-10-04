/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.finder;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * Checks a candidate world seed against the one the connected server uses.
 *
 * <p>On login the server sends a SHA-256 hash of its seed (so the client can blend biomes) and
 * the client stores it in {@link BiomeManager}. Hashing the candidate with the same function
 * gives a yes/no answer; the seed itself cannot be recovered from the hash.</p>
 *
 * <p>The answer is only as honest as the server: one that sends a fake hash makes every seed
 * look wrong, so callers should warn, not refuse to work.</p>
 */
public final class ServerSeedCheck {

    private static volatile Field zoomSeedField;
    private static volatile boolean fieldUnavailable;

    private ServerSeedCheck() {
    }

    /** {@code empty} when the hashed seed cannot be read, which says nothing about the seed. */
    public static Optional<Boolean> matches(Level level, long candidateSeed) {
        return level == null ? Optional.empty() : matches(level.getBiomeManager(), candidateSeed);
    }

    static Optional<Boolean> matches(BiomeManager manager, long candidateSeed) {
        Field field = zoomSeedField();
        if (field == null || manager == null) return Optional.empty();
        try {
            return Optional.of(field.getLong(manager) == BiomeManager.obfuscateSeed(candidateSeed));
        } catch (IllegalAccessException e) {
            return Optional.empty();
        }
    }

    private static Field zoomSeedField() {
        if (fieldUnavailable) return null;
        Field field = zoomSeedField;
        if (field != null) return field;
        try {
            field = BiomeManager.class.getDeclaredField("biomeZoomSeed");
            field.setAccessible(true);
            zoomSeedField = field;
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fieldUnavailable = true;
            return null;
        }
    }
}
