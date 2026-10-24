/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.serverutil;

import combatant.client.features.module.modules.misc.serverutil.AutoLeaveRules.Config;
import combatant.client.features.module.modules.misc.serverutil.AutoLeaveRules.Snapshot;
import combatant.client.features.module.modules.misc.serverutil.AutoLeaveRules.Trigger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AutoLeaveRulesTest {

    private static final Config ALL = new Config(true, 6.0f, true, 20.0, true, true);
    private static final Config NONE = new Config(false, 6.0f, false, 20.0, false, false);

    private static Snapshot calm() {
        return new Snapshot(20.0f, Double.NaN, false, null);
    }

    @Test
    void nothingFiresWhenCalm() {
        assertNull(AutoLeaveRules.evaluate(ALL, calm()));
    }

    @Test
    void healthTriggersAtOrBelowThreshold() {
        assertEquals(Trigger.HEALTH, AutoLeaveRules.evaluate(ALL, new Snapshot(6.0f, Double.NaN, false, null)));
        assertEquals(Trigger.HEALTH, AutoLeaveRules.evaluate(ALL, new Snapshot(2.5f, Double.NaN, false, null)));
        assertNull(AutoLeaveRules.evaluate(ALL, new Snapshot(6.5f, Double.NaN, false, null)));
    }

    @Test
    void deadPlayerNeverTriggersHealth() {
        assertNull(AutoLeaveRules.evaluate(ALL, new Snapshot(0.0f, Double.NaN, false, null)));
    }

    @Test
    void playerTriggersWithinDistanceInclusive() {
        assertEquals(Trigger.PLAYER, AutoLeaveRules.evaluate(ALL, new Snapshot(20f, 20.0, false, null)));
        assertEquals(Trigger.PLAYER, AutoLeaveRules.evaluate(ALL, new Snapshot(20f, 3.0, false, null)));
        assertNull(AutoLeaveRules.evaluate(ALL, new Snapshot(20f, 20.1, false, null)));
    }

    @Test
    void staffAndChatTrigger() {
        assertEquals(Trigger.STAFF, AutoLeaveRules.evaluate(ALL, new Snapshot(20f, Double.NaN, true, null)));
        assertEquals(Trigger.CHAT, AutoLeaveRules.evaluate(ALL, new Snapshot(20f, Double.NaN, false, "ban")));
    }

    @Test
    void priorityIsHealthThenStaffThenChatThenPlayer() {
        assertEquals(Trigger.HEALTH, AutoLeaveRules.evaluate(ALL, new Snapshot(3f, 5.0, true, "ban")));
        assertEquals(Trigger.STAFF, AutoLeaveRules.evaluate(ALL, new Snapshot(20f, 5.0, true, "ban")));
        assertEquals(Trigger.CHAT, AutoLeaveRules.evaluate(ALL, new Snapshot(20f, 5.0, false, "ban")));
        assertEquals(Trigger.PLAYER, AutoLeaveRules.evaluate(ALL, new Snapshot(20f, 5.0, false, null)));
    }

    @Test
    void disabledTriggersNeverFire() {
        assertNull(AutoLeaveRules.evaluate(NONE, new Snapshot(1f, 1.0, true, "ban")));
        Config healthOnly = new Config(true, 6.0f, false, 20.0, false, false);
        assertNull(AutoLeaveRules.evaluate(healthOnly, new Snapshot(20f, 1.0, true, "ban")));
        assertEquals(Trigger.HEALTH, AutoLeaveRules.evaluate(healthOnly, new Snapshot(4f, 1.0, true, "ban")));
    }
}
