/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/**
 * Walks the loaded chunks around the player and runs an analyzer on each, a few per tick.
 *
 * <p>Everything runs on the client thread. The reference finders read chunk sections from
 * background executors, which races with chunk unloads and section updates; the per-tick
 * budget here keeps the cost bounded instead.</p>
 *
 * <p>Chunks are scanned nearest-first. A chunk is rescanned when a block-change packet touches
 * it; the rescan waits one tick because packet events fire on the network thread before the
 * change is applied on the client thread.</p>
 *
 * @param <R> per-chunk result; {@code null} means "nothing found" and is not stored
 */
public final class LoadedChunkScanner<R> {

    @FunctionalInterface
    public interface ChunkAnalyzer<R> {
        R analyze(ClientLevel level, LevelChunk chunk);
    }

    @FunctionalInterface
    interface ChunkLoaded {
        boolean test(int chunkX, int chunkZ);
    }

    // When everything in range is scanned, look for newly loaded chunks this often.
    private static final int IDLE_REFILL_TICKS = 20;
    // Re-sort the queue once the player has moved this many chunks from its centre.
    private static final int RECENTER_DISTANCE = 2;
    private static final int PRUNE_INTERVAL_TICKS = 10;

    private final ChunkAnalyzer<R> analyzer;
    private final IntSupplier radiusChunks;
    private final int chunksPerTick;
    private final long nanosPerTick;
    private final int neighborRadius;

    private final Map<Long, R> results = new ConcurrentHashMap<>();
    // Chunk keys that were scanned (with or without a result) and are still valid.
    private final Map<Long, Boolean> scanned = new ConcurrentHashMap<>();
    // Chunk key -> tick it was marked dirty; written from the network thread.
    private final Map<Long, Long> dirty = new ConcurrentHashMap<>();
    private final Deque<Long> queue = new ArrayDeque<>();

    private ChunkPos queueCenter;
    // Chunk keys repeat across dimensions and servers; results from the old level must not
    // survive into a new one that happens to load the same coordinates.
    private ClientLevel scannedLevel;
    private long tick;
    private long nextIdleRefillTick;

    public LoadedChunkScanner(ChunkAnalyzer<R> analyzer, IntSupplier radiusChunks, int chunksPerTick, long microsPerTick) {
        this(analyzer, radiusChunks, chunksPerTick, microsPerTick, 0);
    }

    /**
     * @param neighborRadius chunks around the analyzed one that must be loaded first. Analyzers
     *                       that read blocks across a chunk border need this: an unloaded chunk
     *                       reads as air on the client, and a chunk is not rescanned when a
     *                       neighbour arrives later unless this is set.
     */
    public LoadedChunkScanner(ChunkAnalyzer<R> analyzer, IntSupplier radiusChunks, int chunksPerTick,
                              long microsPerTick, int neighborRadius) {
        this.analyzer = analyzer;
        this.radiusChunks = radiusChunks;
        this.chunksPerTick = chunksPerTick;
        this.nanosPerTick = microsPerTick * 1000L;
        this.neighborRadius = Math.max(0, neighborRadius);
    }

    /** Current results keyed by {@link ChunkPos#pack} value. Safe to read from the render thread. */
    public Map<Long, R> results() {
        return Collections.unmodifiableMap(results);
    }

    public void clear() {
        results.clear();
        scanned.clear();
        dirty.clear();
        queue.clear();
        queueCenter = null;
        scannedLevel = null;
        tick = 0L;
        nextIdleRefillTick = 0L;
    }

    /** Forces every chunk to be analyzed again, e.g. after a setting changed. */
    public void rescanAll() {
        scanned.clear();
        results.clear();
        queue.clear();
        queueCenter = null;
    }

    public void markDirty(int chunkX, int chunkZ) {
        dirty.put(ChunkPos.pack(chunkX, chunkZ), tick);
    }

    /** Feed incoming packets here (from a {@code PacketEvent.Receive} handler). */
    public void onPacket(Packet<?> packet) {
        if (packet instanceof ClientboundBlockUpdatePacket update) {
            BlockPos pos = update.getPos();
            markDirty(pos.getX() >> 4, pos.getZ() >> 4);
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
            sectionUpdate.runUpdates((pos, state) -> markDirty(pos.getX() >> 4, pos.getZ() >> 4));
        } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            // The new chunk may be the last missing neighbour of chunks that were waiting on it.
            for (int dx = -neighborRadius; dx <= neighborRadius; dx++) {
                for (int dz = -neighborRadius; dz <= neighborRadius; dz++) {
                    markDirty(chunk.getX() + dx, chunk.getZ() + dz);
                }
            }
        }
    }

    public void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        if (level != scannedLevel) {
            clear();
            scannedLevel = level;
        }
        tick++;

        ChunkPos center = mc.player.chunkPosition();
        int radius = Math.max(1, radiusChunks.getAsInt());
        // Pruning touches every remembered chunk; unloads are not urgent, so do it periodically.
        if (tick % PRUNE_INTERVAL_TICKS == 0) prune(level, center, radius);

        long deadline = System.nanoTime() + nanosPerTick;
        int budget = chunksPerTick;
        budget -= drainDirty(level, budget, deadline);
        if (budget <= 0) return;

        refillQueueIfNeeded(center, radius);
        drainQueue(level, budget, deadline);
    }

    private int drainDirty(ClientLevel level, int budget, long deadline) {
        if (dirty.isEmpty()) return 0;
        int done = 0;
        List<Long> ready = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : dirty.entrySet()) {
            if (entry.getValue() < tick) ready.add(entry.getKey());
        }
        for (long key : ready) {
            if (done >= budget || System.nanoTime() > deadline) break;
            dirty.remove(key);
            if (analyze(level, key)) done++;
        }
        return done;
    }

    private void drainQueue(ClientLevel level, int budget, long deadline) {
        int done = 0;
        while (done < budget && !queue.isEmpty() && System.nanoTime() <= deadline) {
            long key = queue.pollFirst();
            if (scanned.containsKey(key)) continue;
            if (analyze(level, key)) done++;
        }
    }

    private boolean analyze(ClientLevel level, long key) {
        LevelChunk chunk = level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), false);
        if (chunk == null || chunk.isEmpty()) {
            scanned.remove(key);
            results.remove(key);
            return false;
        }
        // Keep any earlier result while a neighbour is missing; it is better than nothing.
        if (!neighborsLoaded(level, key)) return false;
        R result = analyzer.analyze(level, chunk);
        scanned.put(key, Boolean.TRUE);
        if (result != null) {
            results.put(key, result);
        } else {
            results.remove(key);
        }
        return true;
    }

    private boolean neighborsLoaded(ClientLevel level, long key) {
        if (neighborRadius == 0) return true;
        return allLoaded(ChunkPos.getX(key), ChunkPos.getZ(key), neighborRadius, (x, z) -> {
            LevelChunk neighbor = level.getChunkSource().getChunk(x, z, false);
            return neighbor != null && !neighbor.isEmpty();
        });
    }

    static boolean allLoaded(int chunkX, int chunkZ, int radius, ChunkLoaded loaded) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!loaded.test(chunkX + dx, chunkZ + dz)) return false;
            }
        }
        return true;
    }

    private void refillQueueIfNeeded(ChunkPos center, int radius) {
        boolean moved = queueCenter == null
                || Math.max(Math.abs(center.x() - queueCenter.x()), Math.abs(center.z() - queueCenter.z())) >= RECENTER_DISTANCE;
        boolean idleRefill = queue.isEmpty() && tick >= nextIdleRefillTick;
        if (!moved && !idleRefill) return;

        queueCenter = center;
        nextIdleRefillTick = tick + IDLE_REFILL_TICKS;
        queue.clear();

        List<Long> order = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                long key = ChunkPos.pack(center.x() + dx, center.z() + dz);
                if (!scanned.containsKey(key)) order.add(key);
            }
        }
        order.sort(Comparator.comparingInt(key -> {
            int dx = ChunkPos.getX(key) - center.x();
            int dz = ChunkPos.getZ(key) - center.z();
            return dx * dx + dz * dz;
        }));
        queue.addAll(order);
    }

    private void prune(ClientLevel level, ChunkPos center, int radius) {
        int keep = radius + 2;
        scanned.keySet().removeIf(key -> outOfRange(key, center, keep)
                || level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), false) == null);
        results.keySet().removeIf(key -> !scanned.containsKey(key));
        dirty.keySet().removeIf(key -> outOfRange(key, center, keep));
    }

    private static boolean outOfRange(long key, ChunkPos center, int range) {
        return Math.abs(ChunkPos.getX(key) - center.x()) > range || Math.abs(ChunkPos.getZ(key) - center.z()) > range;
    }
}
