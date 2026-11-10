/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.spatial;

import combatant.client.render.engine.scene.SceneDrawClass;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;

/**
 * Render-thread-owned section/cell index for Combatant world geometry.
 *
 * <p>Records spanning more than the configured number of sections are kept in a separate
 * large-object collection. World identity is compared by reference, and a change invalidates
 * the existing records.</p>
 */
public final class SceneSpatialRegistry {
    public static final int DEFAULT_MAX_SECTIONS_PER_RECORD = 256;

    private final Long2ObjectOpenHashMap<SceneSpatialRecord> records = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<SectionBucket> sections = new Long2ObjectOpenHashMap<>();
    private final ObjectArrayList<SceneSpatialRecord> largeRecords = new ObjectArrayList<>();
    private final int maxSectionsPerRecord;

    private long nextHandle = 1L;
    private long worldEpoch = 1L;
    private Object worldIdentity;

    public SceneSpatialRegistry() {
        this(DEFAULT_MAX_SECTIONS_PER_RECORD);
    }

    public SceneSpatialRegistry(int maxSectionsPerRecord) {
        if (maxSectionsPerRecord < 1) throw new IllegalArgumentException("maxSectionsPerRecord must be positive");
        this.maxSectionsPerRecord = maxSectionsPerRecord;
    }

    /** Clears stale records when Minecraft changes world/dimension instance. Identity is by reference. */
    public void beginWorld(Object identity) {
        if (worldIdentity == identity) return;
        clearInternal();
        worldIdentity = identity;
        worldEpoch++;
    }

    public long register(SceneSpatialPayload payload, SceneDrawClass drawClass, AABB bounds) {
        if (payload == null) throw new IllegalArgumentException("Spatial payload is required");
        long handle = allocateHandle();
        SceneSpatialRecord record = new SceneSpatialRecord(handle, payload, drawClass, bounds);
        records.put(handle, record);
        index(record);
        return handle;
    }

    public boolean updateBounds(long handle, AABB bounds) {
        SceneSpatialRecord record = records.get(handle);
        if (record == null) return false;
        unindex(record);
        record.setBounds(bounds);
        index(record);
        return true;
    }

    public boolean unregister(long handle) {
        SceneSpatialRecord record = records.remove(handle);
        if (record == null) return false;
        unindex(record);
        return true;
    }

    public SceneSpatialRecord record(long handle) {
        return records.get(handle);
    }

    public boolean visitSections(SceneSpatialSectionVisitor visitor) {
        if (visitor == null) return true;
        for (SectionBucket section : sections.values()) {
            if (!visitor.visit(section)) return false;
        }
        return true;
    }

    public boolean visitLargeRecords(SceneSpatialVisitor visitor) {
        if (visitor == null) return true;
        for (int index = 0; index < largeRecords.size(); index++) {
            if (!visitor.visit(largeRecords.get(index))) return false;
        }
        return true;
    }

    public boolean visitAllRecords(SceneSpatialVisitor visitor) {
        if (visitor == null) return true;
        for (SceneSpatialRecord record : records.values()) {
            if (!visitor.visit(record)) return false;
        }
        return true;
    }

    public void clear() {
        clearInternal();
        worldEpoch++;
    }

    public int size() { return records.size(); }
    public int occupiedSectionCount() { return sections.size(); }
    public int largeObjectCount() { return largeRecords.size(); }
    public int maxSectionsPerRecord() { return maxSectionsPerRecord; }
    public long worldEpoch() { return worldEpoch; }
    public Object worldIdentity() { return worldIdentity; }

    public SceneSpatialStatsSnapshot statsSnapshot() {
        return new SceneSpatialStatsSnapshot(
                records.size(), sections.size(), largeRecords.size(), maxSectionsPerRecord, worldEpoch
        );
    }

    private void index(SceneSpatialRecord record) {
        record.sectionKeys.clear();
        record.largeObject = false;

        AABB bounds = record.bounds();
        int minX = sectionCoord(bounds.minX);
        int minY = sectionCoord(bounds.minY);
        int minZ = sectionCoord(bounds.minZ);
        int maxX = sectionCoord(maxMembershipCoordinate(bounds.minX, bounds.maxX));
        int maxY = sectionCoord(maxMembershipCoordinate(bounds.minY, bounds.maxY));
        int maxZ = sectionCoord(maxMembershipCoordinate(bounds.minZ, bounds.maxZ));

        long countX = (long) maxX - minX + 1L;
        long countY = (long) maxY - minY + 1L;
        long countZ = (long) maxZ - minZ + 1L;
        long cellCount = saturatedMultiply(saturatedMultiply(countX, countY), countZ);
        if (cellCount > maxSectionsPerRecord) {
            record.largeObject = true;
            largeRecords.add(record);
            return;
        }

        for (int sectionX = minX; sectionX <= maxX; sectionX++) {
            for (int sectionY = minY; sectionY <= maxY; sectionY++) {
                for (int sectionZ = minZ; sectionZ <= maxZ; sectionZ++) {
                    long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
                    SectionBucket bucket = sections.get(key);
                    if (bucket == null) {
                        bucket = new SectionBucket(key, sectionX, sectionY, sectionZ);
                        sections.put(key, bucket);
                    }
                    bucket.records.add(record);
                    record.sectionKeys.add(key);
                }
            }
        }
    }

    private void unindex(SceneSpatialRecord record) {
        if (record.largeObject) {
            largeRecords.remove(record);
            record.largeObject = false;
        }
        for (int index = 0; index < record.sectionKeys.size(); index++) {
            long key = record.sectionKeys.getLong(index);
            SectionBucket bucket = sections.get(key);
            if (bucket == null) continue;
            bucket.records.remove(record);
            if (bucket.records.isEmpty()) sections.remove(key);
        }
        record.sectionKeys.clear();
    }

    private long allocateHandle() {
        long start = nextHandle;
        do {
            long handle = nextHandle++;
            if (nextHandle <= 0L) nextHandle = 1L;
            if (handle > 0L && !records.containsKey(handle)) return handle;
        } while (nextHandle != start);
        throw new IllegalStateException("Scene spatial handle space exhausted");
    }

    private void clearInternal() {
        records.clear();
        sections.clear();
        largeRecords.clear();
    }

    private static int sectionCoord(double coordinate) {
        return SectionPos.blockToSectionCoord(coordinate);
    }

    private static double maxMembershipCoordinate(double min, double max) {
        // AABBs are effectively half-open for spatial membership. Avoid registering the adjacent
        // section when max lies exactly on a section boundary, while preserving zero-size records.
        return max > min ? Math.nextDown(max) : max;
    }

    private static long saturatedMultiply(long a, long b) {
        if (a <= 0L || b <= 0L) return 0L;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }

    private static final class SectionBucket implements SceneSpatialSection {
        private final long key;
        private final int x;
        private final int y;
        private final int z;
        private final AABB bounds;
        private final ObjectArrayList<SceneSpatialRecord> records = new ObjectArrayList<>();

        private SectionBucket(long key, int x, int y, int z) {
            this.key = key;
            this.x = x;
            this.y = y;
            this.z = z;
            double minX = (double) x * 16.0;
            double minY = (double) y * 16.0;
            double minZ = (double) z * 16.0;
            this.bounds = new AABB(minX, minY, minZ, minX + 16.0, minY + 16.0, minZ + 16.0);
        }

        @Override public long key() { return key; }
        @Override public int sectionX() { return x; }
        @Override public int sectionY() { return y; }
        @Override public int sectionZ() { return z; }
        @Override public AABB bounds() { return bounds; }
        @Override public int recordCount() { return records.size(); }

        @Override
        public boolean visitRecords(SceneSpatialVisitor visitor) {
            if (visitor == null) return true;
            for (int index = 0; index < records.size(); index++) {
                if (!visitor.visit(records.get(index))) return false;
            }
            return true;
        }
    }
}
