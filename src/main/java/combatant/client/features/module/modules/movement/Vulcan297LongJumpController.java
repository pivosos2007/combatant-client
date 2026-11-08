/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.movement;

public final class Vulcan297LongJumpController {
    private static final int MAX_FLIGHT_TICKS = 46;
    private static final int REARM_TICKS = 12;
    private static final int CORRECTION_COOLDOWN_TICKS = 60;

    private int launchTick = Integer.MIN_VALUE;
    private int rearmTick = Integer.MIN_VALUE;

    public void reset() {
        launchTick = Integer.MIN_VALUE;
        rearmTick = Integer.MIN_VALUE;
    }

    public void onServerCorrection(int tick) {
        reset();
        rearmTick = tick + CORRECTION_COOLDOWN_TICKS;
    }

    public void onTick(int tick, boolean onGround) {
        if (launchTick == Integer.MIN_VALUE) return;
        int elapsed = tick - launchTick;
        if (elapsed < 0 || elapsed > MAX_FLIGHT_TICKS || (elapsed > 2 && onGround)) {
            launchTick = Integer.MIN_VALUE;
            rearmTick = tick + REARM_TICKS;
        }
    }

    public boolean beginSlimeBounce(int tick) {
        if (tick < rearmTick || isManaging(tick)) return false;
        launchTick = tick;
        return true;
    }

    public boolean canBeginBoatLaunch(int tick) {
        return tick >= rearmTick && !isManaging(tick);
    }

    public boolean beginBoatLaunch(int tick) {
        if (!canBeginBoatLaunch(tick)) return false;
        launchTick = tick;
        return true;
    }

    public boolean beginShulkerLaunch(int tick) {
        if (!canBeginBoatLaunch(tick)) return false;
        launchTick = tick;
        return true;
    }

    public boolean isManaging(int tick) {
        return launchTick != Integer.MIN_VALUE
                && tick >= launchTick && tick - launchTick <= MAX_FLIGHT_TICKS;
    }
}
