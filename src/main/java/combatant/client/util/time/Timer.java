/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Portions derived from ThunderHack Recode, copyright (c) 2023-2024 Pan4ur & 06ED.
 * Upstream: https://github.com/Pan4ur/ThunderHack-Recode
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.time;

public class Timer {
    private long time;

    public Timer() {
        reset();
    }

    public boolean passedS(double s) {
        return getMs(System.nanoTime() - time) >= (long) (s * 1000.0);
    }

    public boolean passedMs(long ms) {
        return getMs(System.nanoTime() - time) >= ms;
    }

    public boolean every(long ms) {
        boolean passed = getMs(System.nanoTime() - time) >= ms;
        if (passed) reset();
        return passed;
    }

    public void setMs(long ms) {
        this.time = System.nanoTime() - ms * 1000000L;
    }

    public long getPassedTimeMs() {
        return getMs(System.nanoTime() - time);
    }

    public void reset() {
        this.time = System.nanoTime();
    }

    public long getMs(long time) {
        return time / 1000000L;
    }

    public long getTimeMs() {
        return getMs(System.nanoTime() - time);
    }
}
