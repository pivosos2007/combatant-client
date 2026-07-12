/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.ItemIdSetValue;

import java.util.HashSet;
import java.util.Set;

public final class BlockScanContext {
    public static final Direction[] DIRECTIONS = new Direction[]{
            Direction.UP,
            Direction.DOWN,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST
    };

    private final Minecraft client;
    private final ItemIdSetValue targetBlocks;
    private final ItemIdSetValue transparentBlocks;

    private volatile Set<Identifier> targetIdCache = Set.of();
    private volatile Set<Block> targetBlockCache = Set.of();
    private volatile int targetIdCacheHash;
    private volatile Set<Identifier> transparentIdCache = Set.of();
    private volatile Set<Block> transparentBlockCache = Set.of();
    private volatile int transparentIdCacheHash;

    public BlockScanContext(Minecraft client, ItemIdSetValue targetBlocks, ItemIdSetValue transparentBlocks) {
        this.client = client;
        this.targetBlocks = targetBlocks;
        this.transparentBlocks = transparentBlocks;
    }

    private static Set<Identifier> parseIds(Set<String> rawIds) {
        HashSet<Identifier> ids = new HashSet<>(rawIds.size());
        for (String raw : rawIds) {
            Identifier id = Identifier.tryParse(raw);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static Set<Block> parseBlocks(Set<Identifier> ids) {
        HashSet<Block> blocks = new HashSet<>(ids.size());
        for (Identifier id : ids) {
            blocks.add(BuiltInRegistries.BLOCK.getValue(id));
        }
        return blocks;
    }

    public void refresh() {
        if (targetBlocks != null) {
            Set<String> rawTargets = targetBlocks.get();
            int targetHash = rawTargets.hashCode();
            if (targetHash != targetIdCacheHash) {
                targetIdCache = parseIds(rawTargets);
                targetBlockCache = parseBlocks(targetIdCache);
                targetIdCacheHash = targetHash;
            }
        }

        if (transparentBlocks != null) {
            Set<String> rawTransparent = transparentBlocks.get();
            int transparentHash = rawTransparent.hashCode();
            if (transparentHash != transparentIdCacheHash) {
                transparentIdCache = parseIds(rawTransparent);
                transparentBlockCache = parseBlocks(transparentIdCache);
                transparentIdCacheHash = transparentHash;
            }
        }
    }

    public boolean isConfiguredTarget(BlockState state) {
        if (state == null || state.isAir() || targetBlocks == null) {
            return false;
        }

        return targetBlockCache.contains(state.getBlock());
    }

    public Set<Block> targetBlocksSnapshot() {
        return targetBlockCache;
    }

    public boolean isTransparentState(BlockState state) {
        if (state == null || state.isAir()) {
            return true;
        }

        return transparentBlockCache.contains(state.getBlock());
    }

    public boolean isTransparent(BlockPos pos) {
        BlockState state = RenderSectionBlockScanner.getCachedState(pos);
        if (state == null && client.level != null) {
            state = client.level.getBlockState(pos);
        }
        return isTransparentState(state);
    }

    public boolean hasTransparentNeighbor(BlockPos pos) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (Direction direction : DIRECTIONS) {
            mutable.set(pos).move(direction);
            if (isTransparent(mutable)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPath(Vec3 eye, BlockPos target, boolean limitDistance, int maxDistance) {
        long maxDistanceSq = limitDistance ? (long) maxDistance * (long) maxDistance : Long.MAX_VALUE;

        BlockPos start = BlockPos.containing(eye);
        if (limitDistance && start.distSqr(target) > maxDistanceSq) {
            return false;
        }

        it.unimi.dsi.fastutil.longs.LongOpenHashSet goals = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(8);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (Direction direction : DIRECTIONS) {
            mutable.set(target).move(direction);
            if (isTransparent(mutable)) {
                goals.add(mutable.asLong());
            }
        }
        if (goals.isEmpty()) {
            return false;
        }

        it.unimi.dsi.fastutil.longs.LongOpenHashSet visited = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(16384);
        it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue queue = new it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue();

        if (isTransparent(start)) {
            long key = start.asLong();
            visited.add(key);
            queue.enqueue(key);
        }
        for (Direction direction : DIRECTIONS) {
            mutable.set(start).move(direction);
            if (!isTransparent(mutable)) {
                continue;
            }
            long key = mutable.asLong();
            if (visited.add(key)) {
                queue.enqueue(key);
            }
        }
        if (queue.isEmpty()) {
            return false;
        }

        int hardLimit = 16384;
        while (!queue.isEmpty() && visited.size() < hardLimit) {
            long currentLong = queue.dequeueLong();
            if (goals.contains(currentLong)) {
                return true;
            }

            BlockPos current = BlockPos.of(currentLong);
            for (Direction direction : DIRECTIONS) {
                mutable.set(current).move(direction);
                if (!isTransparent(mutable)) {
                    continue;
                }
                if (limitDistance && mutable.distSqr(start) > maxDistanceSq) {
                    continue;
                }

                long nextLong = mutable.asLong();
                if (visited.add(nextLong)) {
                    queue.enqueue(nextLong);
                }
            }
        }
        return false;
    }
}
