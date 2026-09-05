/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Bounded LRU residency table with frame pinning and generation-safe handles. */
public final class MapTileResidencyCache {
    private final int pageCapacity;
    private final int capacity;
    private final Map<MapTileResidencyKey, Entry> entries = new HashMap<>();
    private final ArrayDeque<Integer> freeSlots = new ArrayDeque<>();
    private final long[] generations;
    private long currentFrame;
    private long hits;
    private long misses;
    private long evictions;

    public MapTileResidencyCache(int pages, int pageCapacity) {
        if (pages <= 0 || pageCapacity <= 0) {
            throw new IllegalArgumentException("Atlas pages and page capacity must be positive.");
        }
        this.pageCapacity = pageCapacity;
        this.capacity = Math.multiplyExact(pages, pageCapacity);
        this.generations = new long[capacity];
        for (int i = 0; i < capacity; i++) freeSlots.addLast(i);
    }

    public synchronized void beginFrame(long frameId) {
        if (frameId < currentFrame) throw new IllegalArgumentException("Frame IDs must be monotonic.");
        currentFrame = frameId;
    }

    public synchronized Acquisition acquire(MapTileResidencyKey key, long revision) {
        if (key == null) throw new NullPointerException("key");
        Entry resident = entries.get(key);
        if (resident != null) {
            hits++;
            resident.lastUsedFrame = currentFrame;
            boolean upload = resident.revision != revision;
            if (upload) {
                int flatSlot = flatIndex(resident.slot);
                long generation = ++generations[flatSlot];
                resident.slot = new MapTileSlot(
                        resident.slot.page(),
                        resident.slot.slot(),
                        generation
                );
            }
            resident.revision = revision;
            return new Acquisition(resident.slot, upload, null, true);
        }

        misses++;
        Integer flatSlot = freeSlots.pollFirst();
        Evicted evicted = null;
        if (flatSlot == null) {
            Map.Entry<MapTileResidencyKey, Entry> victim = entries.entrySet().stream()
                    .filter(entry -> entry.getValue().lastUsedFrame != currentFrame)
                    .min(Comparator
                            .comparingLong((Map.Entry<MapTileResidencyKey, Entry> entry) -> entry.getValue().lastUsedFrame)
                            .thenComparingInt(entry -> flatIndex(entry.getValue().slot)))
                    .orElse(null);
            if (victim == null) return Acquisition.UNAVAILABLE;
            entries.remove(victim.getKey());
            flatSlot = flatIndex(victim.getValue().slot);
            evicted = new Evicted(victim.getKey(), victim.getValue().revision, victim.getValue().slot);
            evictions++;
        }

        long generation = ++generations[flatSlot];
        MapTileSlot slot = new MapTileSlot(flatSlot / pageCapacity, flatSlot % pageCapacity, generation);
        entries.put(key, new Entry(slot, revision, currentFrame));
        return new Acquisition(slot, true, evicted, true);
    }

    public synchronized boolean release(MapTileResidencyKey key) {
        Entry removed = entries.remove(key);
        if (removed == null) return false;
        freeSlots.addLast(flatIndex(removed.slot));
        return true;
    }

    public synchronized MapTileSlot residentSlot(MapTileResidencyKey key, long revision) {
        Entry entry = entries.get(key);
        if (entry == null || entry.revision != revision) return null;
        return entry.slot;
    }

    public synchronized boolean isCurrent(MapTileResidencyKey key, MapTileSlot slot) {
        Entry entry = entries.get(key);
        return entry != null && entry.slot.equals(slot);
    }

    public synchronized Stats stats() {
        return new Stats(capacity, entries.size(), freeSlots.size(), hits, misses, evictions);
    }

    private int flatIndex(MapTileSlot slot) {
        return Math.addExact(Math.multiplyExact(slot.page(), pageCapacity), slot.slot());
    }

    private static final class Entry {
        private MapTileSlot slot;
        private long revision;
        private long lastUsedFrame;

        private Entry(MapTileSlot slot, long revision, long lastUsedFrame) {
            this.slot = slot;
            this.revision = revision;
            this.lastUsedFrame = lastUsedFrame;
        }
    }

    public record Acquisition(MapTileSlot slot,
                              boolean uploadRequired,
                              Evicted evicted,
                              boolean available) {
        public static final Acquisition UNAVAILABLE = new Acquisition(null, false, null, false);
    }

    public record Evicted(MapTileResidencyKey key, long revision, MapTileSlot slot) {
    }

    public record Stats(int capacity, int resident, int free, long hits, long misses, long evictions) {
    }
}
