/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Shared helpers for network stats (ping/TPS).
 * Priority:
 * - TPS: TAB -> client estimation -> 20
 * - Ping: TAB -> player list latency
 */
public enum NetworkStatsUtil {
    ;

    /* ================= CLIENT TPS ESTIMATION ================= */

    private static final long SAMPLE_INTERVAL_MS = 500; // 500–1000 оптимально
    private static final int MAX_TPS = 20;
    private static final long TAB_TIMEOUT_MS = 3000;
    private static long lastWorldTick = -1L;
    private static long lastSampleTime = 0L;
    private static long tickAccumulator = 0L;

    /* ================= TAB OVERRIDES ================= */
    private static float averagedTps = 20.0f;
    private static volatile float tabTps = -1f;
    private static volatile long lastTabTpsUpdate = 0L;

    private static volatile int tabPing = -1;
    private static volatile long lastTabPingUpdate = 0L;

    /* ================= PUBLIC API ================= */

    /**
     * Returns player ping in ms; -1 if unavailable.
     */
    public static int getPing(Minecraft mc) {
        // 1) TAB ping
        if (tabPing >= 0 && isFresh(lastTabPingUpdate)) {
            return tabPing;
        }

        // 2) Player list latency
        if (mc == null) return -1;
        if (mc.hasSingleplayerServer()) return 0;
        if (mc.player == null || mc.player.connection == null) return -1;

        PlayerInfo entry =
                mc.player.connection.getPlayerInfo(mc.player.getUUID());

        return entry != null ? entry.getLatency() : -1;
    }

    /**
     * Returns server TPS.
     * Priority: TAB -> client estimate -> 20
     */
    public static float getTps(Minecraft mc) {
        float clientTps = updateClientEstimator(mc);
        // 1) TAB TPS
        if (tabTps > 0 && isFresh(lastTabTpsUpdate)) {
            return tabTps;
        }

        // 2) Client estimation
        return clientTps;
    }

    /**
     * Returns TPS calculated only from client estimation (ignores TAB).
     */
    public static float getClientTps(Minecraft mc) {
        return updateClientEstimator(mc);
    }

    private static float updateClientEstimator(Minecraft mc) {
        if (mc == null || mc.level == null) {
            return averagedTps;
        }

        if (mc.hasSingleplayerServer()) {
            averagedTps = 20.0f;
            return averagedTps;
        }

        long now = System.currentTimeMillis();
        long worldTick = mc.level.getGameTime();

        if (lastWorldTick >= 0 && worldTick > lastWorldTick) {
            tickAccumulator += (worldTick - lastWorldTick);
        }

        if (lastSampleTime == 0L) {
            lastSampleTime = now;
            lastWorldTick = worldTick;
            return averagedTps;
        }

        long elapsed = now - lastSampleTime;
        if (elapsed >= SAMPLE_INTERVAL_MS) {
            float tps = (tickAccumulator * 1000.0f) / elapsed;
            averagedTps = Math.min(MAX_TPS, Math.max(0f, tps));

            tickAccumulator = 0L;
            lastSampleTime = now;
        }

        lastWorldTick = worldTick;
        return averagedTps;
    }

    /* ================= TAB UPDATE API ================= */

    /**
     * Called from TAB mixin
     */
    public static void updateTabTps(float tps) {
        tabTps = Math.min(MAX_TPS, Math.max(0f, tps));
        lastTabTpsUpdate = System.currentTimeMillis();
    }

    /**
     * Called from TAB mixin
     */
    public static void updateTabPing(int ping) {
        tabPing = Math.max(0, ping);
        lastTabPingUpdate = System.currentTimeMillis();
    }

    /* ================= UTIL ================= */

    private static boolean isFresh(long time) {
        return (System.currentTimeMillis() - time) <= TAB_TIMEOUT_MS;
    }
}
