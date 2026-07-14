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

    private volatile Set<Identifier> targetIdCache = Set.of();
    private volatile Set<Block> targetBlockCache = Set.of();
    private volatile int targetIdCacheHash;

    public BlockScanContext(Minecraft client, ItemIdSetValue targetBlocks) {
        this.client = client;
        this.targetBlocks = targetBlocks;
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

    public boolean isPassable(BlockPos pos) {
        if (pos == null || client.level == null) {
            return false;
        }

        BlockState state = client.level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }

        return state.getCollisionShape(client.level, pos).isEmpty();
    }

}
