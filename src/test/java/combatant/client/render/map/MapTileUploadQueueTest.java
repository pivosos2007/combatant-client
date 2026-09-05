/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapTileUploadQueueTest {
    @Test
    void coalescesNewestRevisionAndHonorsDrainBudget() {
        MapTileUploadQueue queue = new MapTileUploadQueue(4, 128);
        MapTileResidencyKey key = new MapTileResidencyKey("world", new MapTileCoordinate(0, 0, 0));
        MapTilePixels pixels = new MapTilePixels(2, 2, new byte[16]);
        queue.offer(key, 1, new MapTileSlot(0, 0, 1), pixels);
        assertEquals(MapTileUploadQueue.OfferResult.COALESCED,
                queue.offer(key, 2, new MapTileSlot(0, 0, 2), pixels));

        List<MapTileUploadQueue.Upload> uploads = queue.drain(1, 16);
        assertEquals(1, uploads.size());
        assertEquals(2, uploads.getFirst().revision());
        assertEquals(0, queue.stats().pendingUploads());
    }
}
