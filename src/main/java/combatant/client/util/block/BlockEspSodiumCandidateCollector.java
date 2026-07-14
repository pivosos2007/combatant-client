/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hot-path Sodium bridge for BlockESP.
 *
 * <p>This class is intentionally primitive-only on the meshing path: no module lookup,
 * no setting reads, no BlockPos allocation unless a configured block actually matched.</p>
 */
public enum BlockEspSodiumCandidateCollector {
    ;
    private static final int MAX_QUEUED_CANDIDATES = 32768;

    private static final Snapshot DISABLED = new Snapshot(false, Set.of(), false, 0, 0, 0, 0, 0);
    private static final ConcurrentLinkedQueue<Long> CANDIDATE_KEYS = new ConcurrentLinkedQueue<>();
    private static final Map<Long, Candidate> PENDING_CANDIDATES = new ConcurrentHashMap<>();
    private static final AtomicInteger QUEUED_COUNT = new AtomicInteger();
    private static volatile Snapshot snapshot = DISABLED;

    public static void publishSnapshot(
            boolean enabled,
            Set<Block> targetBlocks,
            boolean limitDistance,
            int maxDistance,
            int centerX,
            int centerY,
            int centerZ,
            int generation
    ) {
        if (!enabled || targetBlocks == null || targetBlocks.isEmpty()) {
            snapshot = DISABLED;
            clear();
            return;
        }

        snapshot = new Snapshot(
                true,
                Set.copyOf(targetBlocks),
                limitDistance,
                Math.max(1, maxDistance),
                centerX,
                centerY,
                centerZ,
                generation
        );
    }

    public static void disable() {
        snapshot = DISABLED;
        clear();
    }

    public static void clear() {
        CANDIDATE_KEYS.clear();
        PENDING_CANDIDATES.clear();
        QUEUED_COUNT.set(0);
    }

    public static void observeSodiumBlock(int x, int y, int z, BlockState state) {
        offer(x, y, z, state, CandidateSource.SODIUM_SECTION_SCAN);
    }

    public static void observeSodiumRenderedBlock(int x, int y, int z, BlockState state) {
        offer(x, y, z, state, CandidateSource.SODIUM_BUFFERED_QUAD);
    }

    private static void offer(int x, int y, int z, BlockState state, CandidateSource source) {
        Snapshot current = snapshot;
        if (!current.enabled || state == null || state.isAir()) {
            return;
        }

        if (!current.targets.contains(state.getBlock())) {
            return;
        }

        if (current.limitDistance && !current.withinDistance(x, y, z)) {
            return;
        }

        long packedPos = BlockPos.asLong(x, y, z);
        Candidate candidate = new Candidate(packedPos, state, source, current.generation);
        while (true) {
            Candidate pending = PENDING_CANDIDATES.putIfAbsent(packedPos, candidate);
            if (pending == null) {
                break;
            }
            if (source != CandidateSource.SODIUM_BUFFERED_QUAD
                    || pending.source == CandidateSource.SODIUM_BUFFERED_QUAD) {
                return;
            }
            if (PENDING_CANDIDATES.replace(packedPos, pending, candidate)) {
                return;
            }
        }

        int queued = QUEUED_COUNT.incrementAndGet();
        if (queued > MAX_QUEUED_CANDIDATES) {
            QUEUED_COUNT.decrementAndGet();
            PENDING_CANDIDATES.remove(packedPos, candidate);
            return;
        }

        CANDIDATE_KEYS.add(packedPos);
    }

    public static int drain(int maxCandidates, CandidateConsumer consumer) {
        if (maxCandidates <= 0 || consumer == null) {
            return 0;
        }

        int drained = 0;
        while (drained < maxCandidates) {
            Long packedPos = CANDIDATE_KEYS.poll();
            if (packedPos == null) {
                break;
            }

            Candidate candidate = PENDING_CANDIDATES.remove(packedPos);
            if (candidate == null) {
                continue;
            }
            QUEUED_COUNT.decrementAndGet();
            consumer.accept(candidate);
            drained++;
        }
        return drained;
    }

    public enum CandidateSource {
        SODIUM_SECTION_SCAN,
        SODIUM_BUFFERED_QUAD
    }

    @FunctionalInterface
    public interface CandidateConsumer {
        void accept(Candidate candidate);
    }

    private record Snapshot(
            boolean enabled,
            Set<Block> targets,
            boolean limitDistance,
            int maxDistance,
            int centerX,
            int centerY,
            int centerZ,
            int generation
    ) {
        boolean withinDistance(int x, int y, int z) {
            long dx = (long) x - centerX;
            long dy = (long) y - centerY;
            long dz = (long) z - centerZ;
            long max = maxDistance;
            return dx * dx + dy * dy + dz * dz <= max * max;
        }
    }

    public record Candidate(long packedPos, BlockState state, CandidateSource source, int generation) {
    }
}
