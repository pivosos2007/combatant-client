/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.antiafk;

import combatant.client.features.module.modules.misc.antiafk.AfkScheduler.Action;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkSchedulerTest {

    private static final EnumSet<Action> ALL = EnumSet.allOf(Action.class);

    /** Ticks until the next action fires, counting the firing tick. */
    private static int ticksUntilAction(AfkScheduler scheduler, EnumSet<Action> enabled) {
        for (int ticks = 1; ticks < 100_000; ticks++) {
            if (scheduler.tick(enabled) != null) return ticks;
        }
        throw new AssertionError("scheduler never fired");
    }

    @Test
    void waitsTheFullIntervalWithoutJitter() {
        AfkScheduler scheduler = new AfkScheduler(new Random(1));
        scheduler.configure(10, 0);
        for (int i = 0; i < 200; i++) {
            assertNull(scheduler.tick(ALL), "fired early at tick " + i);
        }
        assertNotNull(scheduler.tick(ALL));
    }

    @Test
    void everyDelayStaysInsideTheJitterBand() {
        AfkScheduler scheduler = new AfkScheduler(new Random(7));
        scheduler.configure(30, 25);
        int base = 30 * 20;
        int min = base - base * 25 / 100;
        int max = base + base * 25 / 100;
        ticksUntilAction(scheduler, ALL);
        for (int i = 0; i < 300; i++) {
            int delay = ticksUntilAction(scheduler, ALL);
            assertTrue(delay >= min && delay <= max + 1, "delay " + delay + " outside [" + min + ", " + max + "]");
        }
    }

    @Test
    void neverRepeatsTheSameActionWhileSeveralAreEnabled() {
        AfkScheduler scheduler = new AfkScheduler(new Random(11));
        scheduler.configure(5, 0);
        EnumSet<Action> two = EnumSet.of(Action.JUMP, Action.SWING);
        Action previous = null;
        for (int i = 0; i < 200; i++) {
            Action action = fire(scheduler, two);
            assertNotEquals(previous, action, "repeated " + action + " at " + i);
            previous = action;
        }
    }

    @Test
    void aSingleEnabledActionIsAllowedToRepeat() {
        AfkScheduler scheduler = new AfkScheduler(new Random(5));
        scheduler.configure(5, 0);
        EnumSet<Action> only = EnumSet.of(Action.LOOK);
        assertEquals(Action.LOOK, fire(scheduler, only));
        assertEquals(Action.LOOK, fire(scheduler, only));
    }

    @Test
    void onlyEnabledActionsAreChosen() {
        AfkScheduler scheduler = new AfkScheduler(new Random(9));
        scheduler.configure(5, 0);
        EnumSet<Action> enabled = EnumSet.of(Action.JUMP, Action.SNEAK, Action.STRAFE);
        for (int i = 0; i < 300; i++) {
            Action action = fire(scheduler, enabled);
            assertTrue(enabled.contains(action), "chose disabled action " + action);
        }
    }

    @Test
    void everyEnabledActionGetsUsedEventually() {
        AfkScheduler scheduler = new AfkScheduler(new Random(13));
        scheduler.configure(5, 0);
        EnumSet<Action> seen = EnumSet.noneOf(Action.class);
        for (int i = 0; i < 200; i++) seen.add(fire(scheduler, ALL));
        assertEquals(ALL, seen);
    }

    @Test
    void nothingEnabledMeansNoActionButTheTimerKeepsRunning() {
        AfkScheduler scheduler = new AfkScheduler(new Random(2));
        scheduler.configure(5, 0);
        // 100 waiting ticks plus the tick that finds nothing to do and re-arms the timer.
        for (int i = 0; i < 101; i++) {
            assertNull(scheduler.tick(EnumSet.noneOf(Action.class)));
        }
        // Enabling an action now still waits a fresh interval instead of firing instantly.
        assertEquals(101, ticksUntilAction(scheduler, ALL));
    }

    @Test
    void activityPushesTheNextActionAFullIntervalAway() {
        AfkScheduler scheduler = new AfkScheduler(new Random(4));
        scheduler.configure(10, 0);
        for (int i = 0; i < 150; i++) scheduler.tick(ALL);
        scheduler.noteActivity();
        for (int i = 0; i < 200; i++) {
            assertNull(scheduler.tick(ALL), "fired " + i + " ticks after activity");
        }
        assertNotNull(scheduler.tick(ALL));
    }

    @Test
    void theDelayNeverDropsBelowOneSecond() {
        AfkScheduler scheduler = new AfkScheduler(new Random(6));
        scheduler.configure(0, 90);
        for (int i = 0; i < 200; i++) {
            assertTrue(ticksUntilAction(scheduler, ALL) > AfkScheduler.MIN_DELAY_TICKS - 1);
        }
    }

    @Test
    void reconfiguringWithTheSameValuesKeepsTheCountdown() {
        AfkScheduler scheduler = new AfkScheduler(new Random(8));
        scheduler.configure(10, 0);
        for (int i = 0; i < 150; i++) {
            scheduler.configure(10, 0);
            assertNull(scheduler.tick(ALL));
        }
        // 50 ticks left, not another full 200.
        int remaining = ticksUntilAction(scheduler, ALL);
        assertEquals(51, remaining);
    }

    /** Runs the scheduler until it fires and returns the chosen action. */
    private static Action fire(AfkScheduler scheduler, EnumSet<Action> enabled) {
        for (int i = 0; i < 100_000; i++) {
            Action action = scheduler.tick(enabled);
            if (action != null) return action;
        }
        throw new AssertionError("scheduler never fired");
    }
}
