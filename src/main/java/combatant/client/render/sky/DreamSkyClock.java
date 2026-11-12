/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sky;

/**
 * Monotonic animation clock shared by sky and clouds. Time is measured in seconds
 * since client class initialization, independently of world time, ticks and weather.
 */
public enum DreamSkyClock {
    ;

    private static final long EPOCH_NANOS = System.nanoTime();

    public static double seconds() {
        return (System.nanoTime() - EPOCH_NANOS) * 1.0e-9;
    }
}
