/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

/**
 * Explicit accessors for vanilla RenderTickCounter timing values.
 * <p>
 * Naming rule:
 * - tickProgress: fractional progress inside current game tick, used for world/entity interpolation.
 * - frameDeltaTicks: time elapsed since previous rendered frame in tick units, used by per-frame updates.
 * - frameDeltaSeconds: same frame delta in seconds.
 */
public enum TickDelta {
    ;
    private static final float TICKS_PER_SECOND = 20.0f;

    /**
     * Legacy alias. Keep this as vanilla-style interpolation progress for old call sites.
     * Prefer tickProgress(false) or tickProgress(counter, false) in new code.
     */
    public static float get() {
        return tickProgress(false);
    }

    public static float tickProgress(boolean ignoreFreeze) {
        DeltaTracker counter = currentCounter();
        return tickProgress(counter, ignoreFreeze);
    }

    public static float tickProgress(DeltaTracker counter, boolean ignoreFreeze) {
        return counter != null ? counter.getGameTimeDeltaPartialTick(ignoreFreeze) : 0.0f;
    }

    public static float frameDeltaTicks() {
        return frameDeltaTicks(currentCounter());
    }

    public static float frameDeltaTicks(DeltaTracker counter) {
        return counter != null ? Math.max(0.0f, counter.getGameTimeDeltaTicks()) : 0.0f;
    }

    public static float frameDeltaSeconds() {
        return frameDeltaSeconds(currentCounter());
    }

    public static float frameDeltaSeconds(DeltaTracker counter) {
        return frameDeltaTicks(counter) / TICKS_PER_SECOND;
    }

    public static float fixedDeltaTicks() {
        return fixedDeltaTicks(currentCounter());
    }

    public static float fixedDeltaTicks(DeltaTracker counter) {
        return counter != null ? Math.max(0.0f, counter.getRealtimeDeltaTicks()) : 0.0f;
    }

    private static DeltaTracker currentCounter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return DeltaTracker.ZERO;
        DeltaTracker counter = mc.getDeltaTracker();
        return counter != null ? counter : DeltaTracker.ZERO;
    }
}
