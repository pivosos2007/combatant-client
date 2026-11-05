/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.seedcracker;

import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.finder.FinderQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SeedCrackerX normally sees only chunk-load packets. Starting it after the world has
 * loaded therefore leaves an already visible area unexamined. This main-thread,
 * bounded backfill submits only loaded chunks to the unchanged upstream FinderQueue.
 * One submission per tick avoids flooding its five-worker executor with finder jobs.
 */
final class SeedCrackerChunkBackfill {
    private static final Set<Long> SEEN = new HashSet<>();
    private static final List<Long> SCAN_ORDER = new ArrayList<>();
    private static String worldKey = "";
    private static int centerX, centerZ, radius, cursor;
    private static long nativeSubmissions, backfilled, inspected;
    private static boolean backfilling;

    private SeedCrackerChunkBackfill() { }

    static void clear() {
        SEEN.clear();
        SCAN_ORDER.clear();
        centerX = centerZ = 0;
        radius = cursor = 0;
        nativeSubmissions = backfilled = inspected = 0;
        worldKey = "";
    }

    /** Called by the optional FinderQueue mixin, for both packet and backfill submissions. */
    static void onChunkData(ChunkPos pos) {
        if (!Config.get().active || pos == null) return;
        SEEN.add(ChunkPos.pack(pos.x(), pos.z()));
        if (!backfilling) nativeSubmissions++;
    }

    static void tick(Minecraft mc, String sessionKey) {
        if (mc.level == null || mc.player == null || !Config.get().active) return;
        if (!sessionKey.equals(worldKey)) {
            clear();
            worldKey = sessionKey;
        }
        ChunkPos current = mc.player.chunkPosition();
        int targetRadius = Math.max(2, Math.min(12, mc.options.renderDistance().get()));
        if (radius != targetRadius || Math.abs(current.x() - centerX) > 5 || Math.abs(current.z() - centerZ) > 5) {
            centerX = current.x();
            centerZ = current.z();
            radius = targetRadius;
            cursor = 0;
            SCAN_ORDER.clear();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    SCAN_ORDER.add(ChunkPos.pack(centerX + dx, centerZ + dz));
                }
            }
            // Begin at the player, not an unloaded corner of the render square.
            SCAN_ORDER.sort(Comparator.comparingInt(key ->
                    Math.abs(ChunkPos.getX(key) - centerX) + Math.abs(ChunkPos.getZ(key) - centerZ)));
        }
        int checks = 0;
        while (cursor < SCAN_ORDER.size() && checks++ < 20) {
            long key = SCAN_ORDER.get(cursor++);
            int x = ChunkPos.getX(key);
            int z = ChunkPos.getZ(key);
            if (SEEN.contains(key)) continue;
            if (mc.level.getChunkSource().getChunk(x, z, false) == null) continue;
            inspected++;
            backfilling = true;
            try {
                FinderQueue.get().onChunkData(mc.level, new ChunkPos(x, z));
            } finally {
                backfilling = false;
            }
            backfilled++;
            // Keep original FinderQueue's asynchronous scanning under control.
            break;
        }
    }

    static String diagnostics() {
        return "chunkEvents=" + nativeSubmissions + ", backfilled=" + backfilled
                + ", submittedChunks=" + SEEN.size() + ", checkedLoaded=" + inspected
                + ", backfill=" + (radius == 0 ? "not started" : cursor + "/" + SCAN_ORDER.size());
    }
    static long submittedChunks() { return SEEN.size(); }
    static long backfilledCount() { return backfilled; }
}
