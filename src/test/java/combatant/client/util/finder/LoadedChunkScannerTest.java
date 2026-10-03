/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.finder;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An unloaded chunk reads as air on the client, so NetheriteFinder must not analyze a chunk
 * until the ring around it is present; otherwise ore near a chunk border is dropped for good.
 */
class LoadedChunkScannerTest {

    private static LoadedChunkScanner.ChunkLoaded loadedSet(Set<Long> loaded) {
        return (x, z) -> loaded.contains(net.minecraft.world.level.ChunkPos.pack(x, z));
    }

    private static Set<Long> square(int centerX, int centerZ, int radius) {
        Set<Long> chunks = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                chunks.add(net.minecraft.world.level.ChunkPos.pack(centerX + dx, centerZ + dz));
            }
        }
        return chunks;
    }

    @Test
    void radiusZeroOnlyNeedsTheChunkItself() {
        assertTrue(LoadedChunkScanner.allLoaded(4, -7, 0, loadedSet(square(4, -7, 0))));
        assertFalse(LoadedChunkScanner.allLoaded(4, -7, 0, loadedSet(Set.of())));
    }

    @Test
    void fullRingIsAccepted() {
        assertTrue(LoadedChunkScanner.allLoaded(10, 10, 1, loadedSet(square(10, 10, 1))));
    }

    @Test
    void missingCornerNeighbourIsRejected() {
        Set<Long> chunks = square(10, 10, 1);
        chunks.remove(net.minecraft.world.level.ChunkPos.pack(11, 11));
        assertFalse(LoadedChunkScanner.allLoaded(10, 10, 1, loadedSet(chunks)));
    }

    @Test
    void missingEdgeNeighbourIsRejected() {
        Set<Long> chunks = square(-3, 5, 1);
        chunks.remove(net.minecraft.world.level.ChunkPos.pack(-4, 5));
        assertFalse(LoadedChunkScanner.allLoaded(-3, 5, 1, loadedSet(chunks)));
    }

    @Test
    void ringAroundAnotherChunkDoesNotCount() {
        assertFalse(LoadedChunkScanner.allLoaded(0, 0, 1, loadedSet(square(1, 0, 1))));
    }
}
