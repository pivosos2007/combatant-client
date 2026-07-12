/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp;

import combatant.client.util.logging.DebugLog;
import combatant.client.util.pvp.client.CooldownsState;

public enum PvpState {
    ;
    private static final Object LOCK = new Object();

    private static boolean active;
    private static String opponentName;
    private static Float secondsLeft;
    private static Float maxSeconds;
    private static Integer opponentHealth;
    private static PvpTagSource tagSource = PvpTagSource.UNKNOWN;
    private static long lastUpdateMs;

    public static boolean isActive() {
        return active;
    }

    public static String getOpponentName() {
        return opponentName;
    }

    public static Float getSecondsLeft() {
        return secondsLeft;
    }

    public static Float getMaxSeconds() {
        return maxSeconds;
    }

    public static Integer getOpponentHealth() {
        return opponentHealth;
    }

    public static PvpTagSource getTagSource() {
        return tagSource;
    }

    public static long getLastUpdateMs() {
        return lastUpdateMs;
    }

    public static long getPredictedRemainingMs() {
        synchronized (LOCK) {
            if (!active || secondsLeft == null) {
                return 0L;
            }
            return Math.max(0L, (long) Math.ceil(secondsLeft * 1000.0f));
        }
    }

    static void applyOverlay(PvpOverlayParser.Result result, long timeMs) {
        if (result == null) return;
        boolean changed;
        boolean prevActive;
        synchronized (LOCK) {
            prevActive = active;
            if (result.exit()) {
                active = false;
                opponentName = null;
                secondsLeft = null;
                maxSeconds = null;
                opponentHealth = null;
                tagSource = PvpTagSource.UNKNOWN;
            } else if (result.active()) {
                active = true;
                if (result.secondsLeft() != null) {
                    secondsLeft = result.secondsLeft();
                    if (maxSeconds == null || secondsLeft > maxSeconds) {
                        maxSeconds = secondsLeft;
                    }
                }
                if (result.opponentName() != null && !result.opponentName().isBlank()) {
                    opponentName = result.opponentName();
                }
                if (result.opponentHealth() != null) {
                    opponentHealth = result.opponentHealth();
                }
            }
            lastUpdateMs = timeMs;
            changed = prevActive != active;
        }

        if (changed) {
            if (active) {
                CooldownsState.MANAGER.enterPvp();
            } else {
                CooldownsState.MANAGER.exitPvp();
            }
        }

        if (changed && DebugLog.serverOnly()) {
            DebugLog.server("PVP state overlay: active=%s time=%.2f opponent=%s hp=%s source=%s",
                    active,
                    secondsLeft == null ? -1f : secondsLeft,
                    opponentName,
                    opponentHealth == null ? "?" : opponentHealth,
                    tagSource);
        }
    }

    static void applyChat(PvpChatParser.Result result, long timeMs) {
        if (result == null) return;
        boolean changed;
        boolean prevActive;
        synchronized (LOCK) {
            prevActive = active;
            if (result.exit()) {
                active = false;
                opponentName = null;
                secondsLeft = null;
                maxSeconds = null;
                opponentHealth = null;
                tagSource = PvpTagSource.UNKNOWN;
            } else if (result.active()) {
                active = true;
                if (result.source() != null && result.source() != PvpTagSource.UNKNOWN) {
                    tagSource = result.source();
                }
                if (result.opponentName() != null && !result.opponentName().isBlank()) {
                    opponentName = result.opponentName();
                }
                if (result.secondsLeft() != null) {
                    secondsLeft = result.secondsLeft();
                    if (maxSeconds == null || secondsLeft > maxSeconds) {
                        maxSeconds = secondsLeft;
                    }
                }
                if (result.opponentHealth() != null) {
                    opponentHealth = result.opponentHealth();
                }
            } else {
                if (result.source() != null && result.source() != PvpTagSource.UNKNOWN) {
                    tagSource = result.source();
                }
                if (result.opponentName() != null && !result.opponentName().isBlank()) {
                    opponentName = result.opponentName();
                }
            }
            lastUpdateMs = timeMs;
            changed = prevActive != active;
        }

        if (changed) {
            if (active) {
                CooldownsState.MANAGER.enterPvp();
            } else {
                CooldownsState.MANAGER.exitPvp();
            }
        }

        if (DebugLog.serverOnly()) {
            DebugLog.server("PVP state chat: active=%s opponent=%s source=%s msgTime=%d",
                    active,
                    opponentName,
                    tagSource,
                    timeMs);
        }
    }
}
