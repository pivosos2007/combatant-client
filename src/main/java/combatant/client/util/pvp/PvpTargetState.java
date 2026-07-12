/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp;

import net.minecraft.util.Util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public enum PvpTargetState {
    ;
    private static final Map<UUID, TargetInfo> TARGETS = new ConcurrentHashMap<>();
    private static volatile UUID currentTargetId;

    public static void setCurrentTarget(UUID targetId) {
        currentTargetId = targetId;
    }

    public static UUID getCurrentTargetId() {
        return currentTargetId;
    }

    public static void markTargetInPvp(UUID targetId, long durationMs) {
        if (targetId == null) return;
        long now = Util.getMillis();
        long until = now + Math.max(0L, durationMs);
        TARGETS.put(targetId, new TargetInfo(true, until, now));
    }

    public static void setTargetInPvp(UUID targetId, boolean inPvp, long untilMs) {
        if (targetId == null) return;
        long now = Util.getMillis();
        if (!inPvp) {
            TARGETS.remove(targetId);
            return;
        }
        TARGETS.put(targetId, new TargetInfo(true, untilMs, now));
    }

    public static boolean isTargetInPvp(UUID targetId) {
        if (targetId == null) return false;
        TargetInfo info = TARGETS.get(targetId);
        if (info == null) return false;
        if (info.untilMs <= Util.getMillis()) {
            TARGETS.remove(targetId);
            return false;
        }
        return info.inPvp;
    }

    public static long getTargetRemainingMs(UUID targetId) {
        if (targetId == null) return 0L;
        TargetInfo info = TARGETS.get(targetId);
        if (info == null) return 0L;
        long now = Util.getMillis();
        long remaining = info.untilMs - now;
        if (remaining <= 0L) {
            TARGETS.remove(targetId);
            return 0L;
        }
        return remaining;
    }

    public static boolean isCurrentTargetInPvp() {
        return isTargetInPvp(currentTargetId);
    }

    public static long getCurrentTargetRemainingMs() {
        return getTargetRemainingMs(currentTargetId);
    }

    private record TargetInfo(boolean inPvp, long untilMs, long lastUpdateMs) {
    }
}
