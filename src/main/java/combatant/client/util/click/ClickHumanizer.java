/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.click;

import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.NumberValue;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Human-like click behavior helper (misses / pauses).
 */
public final class ClickHumanizer {

    private final BooleanMapValue toggles;
    private final NumberValue<Integer> missRate;
    private final NumberValue<Integer> swingRate;
    private final NumberValue<Integer> pauseRate;
    private final NumberValue<Integer> pauseMin;
    private final NumberValue<Integer> pauseMax;
    private final String toggleMiss;
    private final String toggleMissSwing;
    private final String togglePause;
    private int pauseTicks = 0;

    public ClickHumanizer(BooleanMapValue toggles,
                          NumberValue<Integer> missRate,
                          NumberValue<Integer> swingRate,
                          NumberValue<Integer> pauseRate,
                          NumberValue<Integer> pauseMin,
                          NumberValue<Integer> pauseMax,
                          String toggleMiss,
                          String toggleMissSwing,
                          String togglePause) {
        this.toggles = toggles;
        this.missRate = missRate;
        this.swingRate = swingRate;
        this.pauseRate = pauseRate;
        this.pauseMin = pauseMin;
        this.pauseMax = pauseMax;
        this.toggleMiss = toggleMiss;
        this.toggleMissSwing = toggleMissSwing;
        this.togglePause = togglePause;
    }

    private static boolean roll(int rate) {
        if (rate <= 0) return false;
        if (rate >= 100) return true;
        return ThreadLocalRandom.current().nextInt(1, 101) <= rate;
    }

    private static int rand(int min, int max) {
        if (max < min) max = min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public void reset() {
        pauseTicks = 0;
    }

    public Decision decide() {
        if (pauseTicks > 0) {
            pauseTicks--;
            return Decision.PAUSE;
        }

        if (toggles.get(togglePause) && roll(pauseRate.get())) {
            int min = pauseMin.get();
            int max = pauseMax.get();
            if (max < min) max = min;
            pauseTicks = rand(min, max);
            return Decision.PAUSE;
        }

        if (toggles.get(toggleMissSwing) && roll(swingRate.get())) {
            return Decision.MISS_SWING;
        }

        if (toggles.get(toggleMiss) && roll(missRate.get())) {
            return Decision.MISS;
        }

        return Decision.CLICK;
    }

    public enum Decision {
        CLICK,
        MISS,
        MISS_SWING,
        PAUSE
    }
}
