/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.click;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClickSchedulerTest {

    private static int clicksOver(ClickScheduler scheduler, int ticks, int minCps, int maxCps) {
        int clicks = 0;
        for (int tick = 0; tick < ticks; tick++) {
            scheduler.setCps(minCps, maxCps);
            scheduler.tick();
            clicks += scheduler.getClicksAt(0);
        }
        return clicks;
    }

    /** KillAura, AutoAttack and ProjectilePuncher set the range every tick; the rate must survive that. */
    @Test
    void settingTheSameRangeEveryTickKeepsTheRate() {
        assertEquals(100, clicksOver(new ClickScheduler(10, 10), 200, 10, 10));
        assertEquals(60, clicksOver(new ClickScheduler(6, 6), 200, 6, 6));
    }

    @Test
    void aChangedRangeTakesEffect() {
        ClickScheduler scheduler = new ClickScheduler(5, 5);
        assertEquals(40, clicksOver(scheduler, 40, 20, 20));
    }
}
