/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class BlockDeobfuscationScanner {
    private final ArrayList<BlockPos> queue = new ArrayList<>();
    private final ArrayList<CheckedBlock> checked = new ArrayList<>();
    private final Set<Long> discovered = new HashSet<>();
    private final Map<Long, BlockState> positiveStates = new HashMap<>();
    private AABB area;
    private int delayTicks;
    private int done;
    private int all;
    private BlockPos displayBlock;

    private static AABB areaOf(Minecraft client, int radius, int up, int down) {
        int r = Math.max(1, radius);
        int u = Math.max(1, up);
        int d = Math.max(1, down);
        return new AABB(
                client.player.getX() - r,
                client.player.getY() - d,
                client.player.getZ() - r,
                client.player.getX() + r,
                client.player.getY() + u,
                client.player.getZ() + r
        );
    }

    public void reset() {
        queue.clear();
        checked.clear();
        discovered.clear();
        positiveStates.clear();
        area = null;
        delayTicks = 0;
        done = 0;
        all = 0;
        displayBlock = null;
    }

    public void tick(
            Minecraft client,
            int delay,
            int radius,
            int up,
            int down,
            boolean fast
    ) {
        if (client == null || client.level == null || client.player == null || client.gameMode == null) {
            reset();
            return;
        }

        AABB currentArea = areaOf(client, radius, up, down);
        if (area == null || !area.intersects(currentArea)) {
            rebuildQueue(client, currentArea, radius, up, down, fast);
        }

        checked.removeIf(block -> {
            if (block.tickAndIsReady()) {
                discovered.add(block.pos().asLong());
                return true;
            }
            return false;
        });

        if (queue.isEmpty()) {
            return;
        }

        if (++delayTicks < Math.max(1, delay)) {
            return;
        }
        delayTicks = 0;

        int index = queue.size() <= 1 ? 0 : ThreadLocalRandom.current().nextInt(queue.size());
        BlockPos pos = queue.remove(index);
        displayBlock = pos;

        client.gameMode.startDestroyBlock(pos, client.player.getDirection());
        client.gameMode.stopDestroyBlock();
        if (client.getConnection() != null) {
            client.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }

        checked.add(new CheckedBlock(pos));
        done++;
    }

    public void acceptBlockUpdate(BlockPos pos, BlockState state, Predicate<BlockState> targetPredicate) {
        if (pos == null || state == null || targetPredicate == null) {
            return;
        }

        if (targetPredicate.test(state)) {
            long key = pos.asLong();
            discovered.add(key);
            positiveStates.put(key, state);
            RenderSectionBlockScanner.recordBlock(pos, state);
        }
    }

    public boolean hasPositiveState(BlockPos pos) {
        return pos != null && positiveStates.containsKey(pos.asLong());
    }

    public BlockState getPositiveState(BlockPos pos) {
        return pos == null ? null : positiveStates.get(pos.asLong());
    }

    public List<RenderSectionBlockScanner.ScanResult> snapshot(
            Minecraft client,
            Predicate<BlockState> targetPredicate,
            Predicate<BlockPos> visibilityPredicate,
            int maxTargets
    ) {
        if (client == null || client.level == null || maxTargets <= 0 || targetPredicate == null) {
            return List.of();
        }

        List<RenderSectionBlockScanner.ScanResult> results = new ArrayList<>(Math.min(maxTargets, discovered.size()));
        for (long key : discovered) {
            if (results.size() >= maxTargets) {
                break;
            }

            BlockState state = positiveStates.get(key);
            BlockPos pos = BlockPos.of(key);
            if (state == null) {
                state = RenderSectionBlockScanner.getCachedState(pos);
            }
            if (state == null) {
                state = client.level.getBlockState(pos);
            }
            if (!targetPredicate.test(state)) {
                continue;
            }

            boolean visible = visibilityPredicate != null && visibilityPredicate.test(pos);
            results.add(new RenderSectionBlockScanner.ScanResult(pos, state, visible));
        }
        return results;
    }

    public int done() {
        return done;
    }

    public int all() {
        return all;
    }

    public BlockPos displayBlock() {
        return displayBlock;
    }

    private void rebuildQueue(Minecraft client, AABB newArea, int radius, int up, int down, boolean fast) {
        queue.clear();
        checked.clear();
        area = newArea;
        done = 0;
        displayBlock = null;

        int clampedRadius = Math.max(1, radius);
        int clampedUp = Math.max(1, up);
        int clampedDown = Math.max(1, down);

        int px = client.player.getBlockX();
        int py = client.player.getBlockY();
        int pz = client.player.getBlockZ();
        int minY = client.level.getMinY();
        int maxY = client.level.getMinY() + client.level.getHeight() - 1;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = px - clampedRadius; x <= px + clampedRadius; x++) {
            for (int y = Math.max(minY, py - clampedDown); y <= Math.min(maxY, py + clampedUp); y++) {
                for (int z = pz - clampedRadius; z <= pz + clampedRadius; z++) {
                    if (fast && (x % 2 == 0 || y % 2 == 0 || z % 2 == 0)) {
                        continue;
                    }

                    mutable.set(x, y, z);
                    BlockState state = client.level.getBlockState(mutable);
                    if (!state.isAir()) {
                        queue.add(mutable.immutable());
                    }
                }
            }
        }

        all = queue.size();
    }

    private static final class CheckedBlock {
        private final BlockPos pos;
        private int age;

        private CheckedBlock(BlockPos pos) {
            this.pos = pos;
        }

        private BlockPos pos() {
            return pos;
        }

        private boolean tickAndIsReady() {
            age++;
            return age > 10;
        }
    }
}
