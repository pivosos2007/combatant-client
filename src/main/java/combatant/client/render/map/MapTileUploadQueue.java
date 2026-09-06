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
        if (pixels == null) throw new NullPointerException("pixels");
        return offer(new Upload(key, revision, slot, pixels, null));
    }

    public synchronized OfferResult offer(MapTileResidencyKey key,
                                          long revision,
                                          MapTileSlot slot,
                                          MapTileGpuCopy gpuCopy) {
        if (gpuCopy == null) throw new NullPointerException("gpuCopy");
        return offer(new Upload(key, revision, slot, null, gpuCopy));
    }

    private OfferResult offer(Upload upload) {
        if (upload.key() == null || upload.slot() == null) throw new NullPointerException();
        if (upload.byteSize() > maxPendingBytes) {
            dropped++;
            return OfferResult.REJECTED_TOO_LARGE;
        }
        Upload previous = pending.get(upload.key());
        if (previous != null && previous.revision() > upload.revision()) {
            return OfferResult.IGNORED_STALE;
        }
        if (previous != null) {
            pending.remove(upload.key());
            pendingBytes -= previous.byteSize();
        }

        while (!pending.isEmpty()
                && (pending.size() >= maxPendingUploads || pendingBytes + upload.byteSize() > maxPendingBytes)) {
            Iterator<Map.Entry<MapTileResidencyKey, Upload>> iterator = pending.entrySet().iterator();
            Upload removed = iterator.next().getValue();
            iterator.remove();
            pendingBytes -= removed.byteSize();
            dropped++;
        }
        pending.put(upload.key(), upload);
        pendingBytes += upload.byteSize();
        return previous == null ? OfferResult.ACCEPTED : OfferResult.COALESCED;
    }

    public synchronized List<Upload> drain(int maxUploads, long maxBytes) {
        if (maxUploads <= 0 || maxBytes <= 0L || pending.isEmpty()) return List.of();
        List<Upload> result = new ArrayList<>(Math.min(maxUploads, pending.size()));
        Iterator<Map.Entry<MapTileResidencyKey, Upload>> iterator = pending.entrySet().iterator();
        long bytes = 0L;
        while (iterator.hasNext() && result.size() < maxUploads) {
            Upload upload = iterator.next().getValue();
            if (!result.isEmpty() && bytes + upload.byteSize() > maxBytes) break;
            if (upload.byteSize() > maxBytes) break;
            result.add(upload);
            bytes += upload.byteSize();
            pendingBytes -= upload.byteSize();
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
                         MapTilePixels pixels,
                         MapTileGpuCopy gpuCopy) {
        public Upload {
            if (key == null || slot == null) throw new NullPointerException();
            if ((pixels == null) == (gpuCopy == null)) {
                throw new IllegalArgumentException("Exactly one tile upload payload is required.");
            }
        }

        public int byteSize() {
            return pixels != null ? pixels.byteSize() : gpuCopy.byteSize();
        }
    }

    public record Stats(int pendingUploads, long pendingBytes, long dropped) {
    }
}
