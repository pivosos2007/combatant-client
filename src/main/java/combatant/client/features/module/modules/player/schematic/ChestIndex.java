/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.schematic;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Remembers what nearby storage containers hold in the current dimension and picks the best container to restock
 * from when {@link SchematicBuilder} runs out of a material.
 */
public final class ChestIndex {

    private static final int PLAYER_INVENTORY_SLOTS = 36;
    private static final int RETRY_COOLDOWN_TICKS = 240;

    private final Long2ObjectOpenHashMap<Map<Item, Integer>> contents = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet indexed = new LongOpenHashSet();
    private final Long2LongOpenHashMap cooldownUntil = new Long2LongOpenHashMap();
    private ResourceKey<Level> dimension;

    public void clear() {
        contents.clear();
        indexed.clear();
        cooldownUntil.clear();
        dimension = null;
    }

    /** Canonical position keys ({@link BlockPos#asLong}) of every container indexed in {@code level}'s dimension. */
    public long[] indexedKeys(ClientLevel level) {
        if (level == null) return new long[0];
        ensureDimension(level);
        return indexed.toLongArray();
    }

    private void ensureDimension(ClientLevel level) {
        if (level == null) return;
        if (!level.dimension().equals(dimension)) {
            clear();
            dimension = level.dimension();
        }
    }

    /** Canonical position so both halves of a double chest share one index entry. */
    public static BlockPos canonical(ClientLevel level, BlockPos pos) {
        if (level == null || pos == null) return pos;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type != ChestType.SINGLE) {
                Direction otherDir = ChestBlock.getConnectedDirection(state);
                BlockPos other = pos.relative(otherDir);
                if (other.asLong() < pos.asLong()) return other.immutable();
            }
        }
        return pos.immutable();
    }

    /** Records the items currently visible in the open container menu for {@code openedPos}. */
    public void recordOpenContainer(ClientLevel level, LocalPlayer player, BlockPos openedPos) {
        if (level == null || player == null || openedPos == null) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu == player.inventoryMenu) return;
        int containerSlots = menu.slots.size() - PLAYER_INVENTORY_SLOTS;
        if (containerSlots <= 0) return;

        ensureDimension(level);
        long key = canonical(level, openedPos).asLong();
        Map<Item, Integer> map = new IdentityHashMap<>();
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                map.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        contents.put(key, map);
        indexed.add(key);
    }

    public void markCooldown(ClientLevel level, BlockPos pos, long untilTick) {
        if (level == null || pos == null) return;
        ensureDimension(level);
        cooldownUntil.put(canonical(level, pos).asLong(), untilTick);
    }

    public boolean hasNeeded(ClientLevel level, BlockPos pos, Set<Item> needed) {
        if (level == null || pos == null || needed.isEmpty()) return false;
        ensureDimension(level);
        Map<Item, Integer> map = contents.get(canonical(level, pos).asLong());
        if (map == null) return false;
        for (Item item : needed) {
            if (map.getOrDefault(item, 0) > 0) return true;
        }
        return false;
    }

    public boolean isIndexed(ClientLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        ensureDimension(level);
        return indexed.contains(canonical(level, pos).asLong());
    }

    /**
     * Picks a container to walk to and open:
     * <ol>
     *   <li>The marked {@link SupplyPoint} if it is unindexed or holds a needed material.</li>
     *   <li>The nearest indexed container within {@code scanRange} that holds a needed material.</li>
     *   <li>When {@code scanNearby} is on, the nearest unindexed container within {@code scanRange} so the builder
     *       indexes nearby chests and uses what is inside.</li>
     * </ol>
     */
    public BlockPos findTarget(ClientLevel level, LocalPlayer player, Set<Item> needed, boolean scanNearby,
                               int scanRange, long tick) {
        if (level == null || player == null || needed.isEmpty()) return null;
        ensureDimension(level);

        BlockPos marked = SupplyPoint.INSTANCE.pos();
        if (marked != null && SupplyPoint.isContainer(level, marked)) {
            long key = canonical(level, marked).asLong();
            if (cooldownUntil.getOrDefault(key, 0L) <= tick
                    && (!indexed.contains(key) || hasNeeded(level, marked, needed))) {
                return marked;
            }
        }
        if (!scanNearby) return null;

        BlockPos origin = player.blockPosition();
        double maxDistSq = (double) scanRange * scanRange;
        BlockPos bestKnown = null;
        double bestKnownSq = Double.MAX_VALUE;
        BlockPos bestUnindexed = null;
        double bestUnindexedSq = Double.MAX_VALUE;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -Math.min(8, scanRange); dy <= Math.min(8, scanRange); dy++) {
            for (int dz = -scanRange; dz <= scanRange; dz++) {
                for (int dx = -scanRange; dx <= scanRange; dx++) {
                    double distSq = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                    if (distSq > maxDistSq) continue;
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.hasChunkAt(cursor) || !SupplyPoint.isContainer(level, cursor)) continue;
                    BlockPos canon = canonical(level, cursor);
                    long key = canon.asLong();
                    if (cooldownUntil.getOrDefault(key, 0L) > tick) continue;

                    if (indexed.contains(key)) {
                        if (distSq < bestKnownSq && hasNeeded(level, canon, needed)) {
                            bestKnownSq = distSq;
                            bestKnown = cursor.immutable();
                        }
                    } else if (distSq < bestUnindexedSq) {
                        bestUnindexedSq = distSq;
                        bestUnindexed = cursor.immutable();
                    }
                }
            }
        }
        return bestKnown != null ? bestKnown : bestUnindexed;
    }

    public static int retryCooldownTicks() {
        return RETRY_COOLDOWN_TICKS;
    }
}
