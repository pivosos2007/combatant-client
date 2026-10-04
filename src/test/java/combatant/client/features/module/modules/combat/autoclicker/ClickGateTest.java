/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autoclicker;

import combatant.client.features.module.modules.combat.autoclicker.ClickGate.Verdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClickGateTest {

    /** Defaults of the module: hold to click, respect cooldown, friends spared, swings in the air allowed. */
    private static Verdict decide(boolean held, boolean block, boolean entity, boolean friend, float strength) {
        return ClickGate.decide(true, held, block, entity, friend, false, false, false, true, strength);
    }

    @Test
    void holdingTheButtonOverAnEntityAttacks() {
        assertEquals(Verdict.ATTACK, decide(true, false, true, false, 1.0f));
    }

    @Test
    void holdingTheButtonOverNothingSwings() {
        assertEquals(Verdict.SWING, decide(true, false, false, false, 1.0f));
    }

    @Test
    void notHoldingTheButtonDoesNothingWhenHoldIsRequired() {
        assertEquals(Verdict.SKIP, decide(false, false, true, false, 1.0f));
    }

    @Test
    void withoutHoldTheModuleClicksOnItsOwn() {
        assertEquals(Verdict.ATTACK, ClickGate.decide(false, false, false, true, false, false, false, false, true, 1.0f));
    }

    @Test
    void neverClicksWhileLookingAtABlock() {
        assertEquals(Verdict.SKIP, decide(true, true, false, false, 1.0f));
        assertEquals(Verdict.SKIP, ClickGate.decide(false, false, true, false, false, false, false, false, false, 1.0f));
    }

    @Test
    void waitsForTheCooldownWhenAskedTo() {
        assertEquals(Verdict.SKIP, decide(true, false, true, false, 0.5f));
        assertEquals(Verdict.SKIP, decide(true, false, false, false, 0.89f));
        assertEquals(Verdict.ATTACK, decide(true, false, true, false, 0.9f));
    }

    @Test
    void ignoresTheCooldownWhenAskedTo() {
        assertEquals(Verdict.ATTACK, ClickGate.decide(true, true, false, true, false, false, false, false, false, 0.1f));
    }

    @Test
    void sparesFriends() {
        assertEquals(Verdict.SKIP, decide(true, false, true, true, 1.0f));
    }

    @Test
    void entitiesOnlySkipsAirSwings() {
        assertEquals(Verdict.SKIP, ClickGate.decide(true, true, false, false, false, true, false, false, true, 1.0f));
        assertEquals(Verdict.ATTACK, ClickGate.decide(true, true, false, true, false, true, false, false, true, 1.0f));
    }

    @Test
    void weaponOnlyNeedsAWeaponInHand() {
        assertEquals(Verdict.SKIP, ClickGate.decide(true, true, false, true, false, false, true, false, true, 1.0f));
        assertEquals(Verdict.ATTACK, ClickGate.decide(true, true, false, true, false, false, true, true, true, 1.0f));
    }
}
