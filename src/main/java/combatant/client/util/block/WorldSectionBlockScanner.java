/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayDeque;
import java.util.function.Predicate;

/**
 * Main-thread, budgeted scanner for already loaded client world chunk sections.
 *
 * <p>This is intentionally not a worker thread and does not request chunk rebuilds.
 * Sodium/renderer observations can still feed caches opportunistically, while this
 * scanner provides deterministic catch-up for enable, setting changes and newly
 * reachable sections.</p>
 */
public final class WorldSectionBlockScanner {
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final LongOpenHashSet queuedSections = new LongOpenHashSet();
    private final LongOpenHashSet scannedSections = new LongOpenHashSet();

    private static boolean scanSection(
            ClientLevel world,
            long sectionKey,
            Predicate<BlockState> targetPredicate,
            ResultConsumer consumer
    ) {
        int sectionX = SectionPos.x(sectionKey);
        int sectionY = SectionPos.y(sectionKey);
        int sectionZ = SectionPos.z(sectionKey);

        ChunkAccess chunk = world.getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            return false;
        }

        int sectionIndex = world.getSectionIndexFromSectionY(sectionY);
        LevelChunkSection[] sections = chunk.getSections();
        if (sectionIndex < 0 || sectionIndex >= sections.length) {
            return true;
        }

        LevelChunkSection section = sections[sectionIndex];
        if (section == null || section.hasOnlyAir() || !section.maybeHas(targetPredicate)) {
            return true;
        }

        int baseX = SectionPos.sectionToBlockCoord(sectionX);
        int baseY = SectionPos.sectionToBlockCoord(sectionY);
        int baseZ = SectionPos.sectionToBlockCoord(sectionZ);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int localY = 0; localY < 16; localY++) {
            int y = baseY + localY;
            if (!world.isInsideBuildHeight(y)) {
                continue;
            }

            for (int localZ = 0; localZ < 16; localZ++) {
                int z = baseZ + localZ;
                for (int localX = 0; localX < 16; localX++) {
                    BlockState state = section.getBlockState(localX, localY, localZ);
                    if (!targetPredicate.test(state)) {
                        continue;
                    }

                    mutable.set(baseX + localX, y, z);
                    consumer.accept(mutable.immutable(), state);
                }
            }
        }
        return true;
    }

    public void reset() {
        queue.clear();
        queuedSections.clear();
        scannedSections.clear();
    }

    public void forget(BlockPos pos) {
        if (pos == null) {
            return;
        }
        scannedSections.remove(SectionPos.asLong(pos));
    }

    public void enqueueAround(Minecraft client, int horizontalBlockRadius, int minY, int maxY) {
        if (client == null || client.level == null || client.player == null) {
            return;
        }

        ClientLevel world = client.level;
        int radius = Math.max(1, horizontalBlockRadius);
        int chunkRadius = Math.max(1, (radius + 15) >> 4);
        int centerChunkX = SectionPos.blockToSectionCoord(client.player.getBlockX());
        int centerChunkZ = SectionPos.blockToSectionCoord(client.player.getBlockZ());

        int clampedMinY = Math.max(world.getMinY(), Math.min(minY, maxY));
        int clampedMaxY = Math.min(world.getMaxY(), Math.max(minY, maxY));
        if (clampedMinY > clampedMaxY) {
            return;
        }

        int minSectionY = SectionPos.blockToSectionCoord(clampedMinY);
        int maxSectionY = SectionPos.blockToSectionCoord(clampedMaxY);
        int centerSectionY = SectionPos.blockToSectionCoord(client.player.getBlockY());

        for (int ring = 0; ring <= chunkRadius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                enqueueColumnEdge(centerChunkX + dx, centerChunkZ - ring, minSectionY, maxSectionY, centerSectionY);
                if (ring != 0) {
                    enqueueColumnEdge(centerChunkX + dx, centerChunkZ + ring, minSectionY, maxSectionY, centerSectionY);
                }
            }

            for (int dz = -ring + 1; dz <= ring - 1; dz++) {
                enqueueColumnEdge(centerChunkX - ring, centerChunkZ + dz, minSectionY, maxSectionY, centerSectionY);
                if (ring != 0) {
                    enqueueColumnEdge(centerChunkX + ring, centerChunkZ + dz, minSectionY, maxSectionY, centerSectionY);
                }
            }
        }
    }

    public int drain(
            Minecraft client,
            int maxSections,
            Predicate<BlockState> targetPredicate,
            ResultConsumer consumer
    ) {
        if (client == null || client.level == null || maxSections <= 0 || targetPredicate == null || consumer == null) {
            return 0;
        }

        int processed = 0;
        int attempted = 0;
        int maxAttempts = Math.max(maxSections, maxSections * 8);
        while (processed < maxSections && attempted < maxAttempts && !queue.isEmpty()) {
            attempted++;
            long sectionKey = queue.removeFirst();
            queuedSections.remove(sectionKey);

            if (scanSection(client.level, sectionKey, targetPredicate, consumer)) {
                scannedSections.add(sectionKey);
                processed++;
            }
        }
        return processed;
    }

    private void enqueueColumnEdge(int chunkX, int chunkZ, int minSectionY, int maxSectionY, int centerSectionY) {
        int center = Math.max(minSectionY, Math.min(maxSectionY, centerSectionY));
        enqueueSection(chunkX, center, chunkZ);

        int maxOffset = Math.max(center - minSectionY, maxSectionY - center);
        for (int offset = 1; offset <= maxOffset; offset++) {
            int up = center + offset;
            if (up <= maxSectionY) {
                enqueueSection(chunkX, up, chunkZ);
            }

            int down = center - offset;
            if (down >= minSectionY) {
                enqueueSection(chunkX, down, chunkZ);
            }
        }
    }

    private void enqueueSection(int sectionX, int sectionY, int sectionZ) {
        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
        if (scannedSections.contains(key) || !queuedSections.add(key)) {
            return;
        }
        queue.addLast(key);
    }

    @FunctionalInterface
    public interface ResultConsumer {
        void accept(BlockPos pos, BlockState state);
    }
}
