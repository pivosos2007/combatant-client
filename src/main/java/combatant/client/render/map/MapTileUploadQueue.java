/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded, revision-aware upload queue that coalesces repeated updates per tile. */
public final class MapTileUploadQueue {
    private final int maxPendingUploads;
    private final long maxPendingBytes;
    private final LinkedHashMap<MapTileResidencyKey, Upload> pending = new LinkedHashMap<>();
    private long pendingBytes;
    private long dropped;

    public MapTileUploadQueue(int maxPendingUploads, long maxPendingBytes) {
        if (maxPendingUploads <= 0 || maxPendingBytes <= 0L) {
            throw new IllegalArgumentException("Upload queue limits must be positive.");
        }
        this.maxPendingUploads = maxPendingUploads;
        this.maxPendingBytes = maxPendingBytes;
    }

    public synchronized OfferResult offer(MapTileResidencyKey key,
                                          long revision,
                                          MapTileSlot slot,
                                          MapTilePixels pixels) {
        if (key == null || slot == null || pixels == null) throw new NullPointerException();
        if (pixels.byteSize() > maxPendingBytes) {
            dropped++;
            return OfferResult.REJECTED_TOO_LARGE;
        }
        Upload previous = pending.get(key);
        if (previous != null && previous.revision() > revision) {
            return OfferResult.IGNORED_STALE;
        }
        if (previous != null) {
            pending.remove(key);
            pendingBytes -= previous.pixels().byteSize();
        }

        while (!pending.isEmpty()
                && (pending.size() >= maxPendingUploads || pendingBytes + pixels.byteSize() > maxPendingBytes)) {
            Iterator<Map.Entry<MapTileResidencyKey, Upload>> iterator = pending.entrySet().iterator();
            Upload removed = iterator.next().getValue();
            iterator.remove();
            pendingBytes -= removed.pixels().byteSize();
            dropped++;
        }
        pending.put(key, new Upload(key, revision, slot, pixels));
        pendingBytes += pixels.byteSize();
        return previous == null ? OfferResult.ACCEPTED : OfferResult.COALESCED;
    }

    public synchronized List<Upload> drain(int maxUploads, long maxBytes) {
        if (maxUploads <= 0 || maxBytes <= 0L || pending.isEmpty()) return List.of();
        List<Upload> result = new ArrayList<>(Math.min(maxUploads, pending.size()));
        Iterator<Map.Entry<MapTileResidencyKey, Upload>> iterator = pending.entrySet().iterator();
        long bytes = 0L;
        while (iterator.hasNext() && result.size() < maxUploads) {
            Upload upload = iterator.next().getValue();
            if (!result.isEmpty() && bytes + upload.pixels().byteSize() > maxBytes) break;
            if (upload.pixels().byteSize() > maxBytes) break;
            result.add(upload);
            bytes += upload.pixels().byteSize();
            pendingBytes -= upload.pixels().byteSize();
            iterator.remove();
        }
        return List.copyOf(result);
    }

    public synchronized Stats stats() {
        return new Stats(pending.size(), pendingBytes, dropped);
    }

    public enum OfferResult {
        ACCEPTED,
        COALESCED,
        IGNORED_STALE,
        REJECTED_TOO_LARGE
    }

    public record Upload(MapTileResidencyKey key,
                         long revision,
                         MapTileSlot slot,
                         MapTilePixels pixels) {
    }

    public record Stats(int pendingUploads, long pendingBytes, long dropped) {
    }
}
