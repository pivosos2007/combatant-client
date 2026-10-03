/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.antiafk;

import java.util.EnumSet;
import java.util.Random;

/**
 * Decides when AntiAFK acts and with what. Free of Minecraft types so the timing rules can be unit
 * tested: every delay stays inside the configured jitter band, no action repeats back to back while
 * several are enabled, and real input pushes the next action a full interval away.
 */
public final class AfkScheduler {

    public enum Action {
        JUMP,
        SWING,
        LOOK,
        STRAFE,
        SNEAK
    }

    /** Below one second the pattern looks scripted and servers that count packets notice it. */
    public static final int MIN_DELAY_TICKS = 20;

    private final Random random;
    private int baseTicks = 30 * 20;
    private int jitterPercent = 25;
    private int ticksLeft;
    private Action last;

    public AfkScheduler(Random random) {
        this.random = random;
        rollDelay();
    }

    /** Applies the settings; an unchanged call keeps the running countdown. */
    public void configure(int intervalSeconds, int jitterPercent) {
        int ticks = Math.max(MIN_DELAY_TICKS, intervalSeconds * 20);
        int jitter = Math.max(0, Math.min(90, jitterPercent));
        if (ticks == baseTicks && jitter == this.jitterPercent) return;
        baseTicks = ticks;
        this.jitterPercent = jitter;
        rollDelay();
    }

    /** The player moved or clicked on their own: the next idle action is a full interval away. */
    public void noteActivity() {
        rollDelay();
    }

    /** Advances one tick. Returns the action to perform now, or null. */
    public Action tick(EnumSet<Action> enabled) {
        if (ticksLeft > 0) {
            ticksLeft--;
            return null;
        }
        rollDelay();
        return pick(enabled);
    }

    private void rollDelay() {
        int spread = baseTicks * jitterPercent / 100;
        int offset = spread == 0 ? 0 : random.nextInt(spread * 2 + 1) - spread;
        ticksLeft = Math.max(MIN_DELAY_TICKS, baseTicks + offset);
    }

    private Action pick(EnumSet<Action> enabled) {
        if (enabled == null || enabled.isEmpty()) return null;

        int candidates = 0;
        for (Action action : enabled) {
            if (action != last || enabled.size() == 1) candidates++;
        }
        int choice = random.nextInt(candidates);
        for (Action action : enabled) {
            if (action == last && enabled.size() > 1) continue;
            if (choice-- == 0) {
                last = action;
                return action;
            }
        }
        return null;
    }
}
