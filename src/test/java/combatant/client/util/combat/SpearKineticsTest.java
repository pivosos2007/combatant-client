/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

import combatant.client.util.combat.SpearKinetics.Condition;
import combatant.client.util.combat.SpearKinetics.ReachBand;
import combatant.client.util.combat.SpearKinetics.WindowState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpearKineticsTest {

    @Test
    void testSpeedAlong() {
        // Look along +Z, moving along +Z at 10 blocks/sec
        double speed = SpearKinetics.speedAlong(0, 0, 1, 0, 0, 10);
        assertEquals(10.0, speed, 1e-6);

        // Moving perpendicular to look
        double perp = SpearKinetics.speedAlong(1, 0, 0, 0, 0, 10);
        assertEquals(0.0, perp, 1e-6);

        // Moving backward
        double back = SpearKinetics.speedAlong(0, 1, 0, 0, -5, 0);
        assertEquals(-5.0, back, 1e-6);
    }

    @Test
    void testRelativeSpeed() {
        // Attacker moving 15, target moving 5 in same direction -> rel speed 10
        assertEquals(10.0, SpearKinetics.relativeSpeed(15, 5), 1e-6);

        // Target moving away faster than attacker -> rel speed clamped to 0
        assertEquals(0.0, SpearKinetics.relativeSpeed(5, 15), 1e-6);

        // Target moving toward attacker (negative speed along look) -> rel speed 15 - (-5) = 20
        assertEquals(20.0, SpearKinetics.relativeSpeed(15, -5), 1e-6);
    }

    @Test
    void testChargeProgress() {
        assertEquals(0.0f, SpearKinetics.chargeProgress(0, 20), 1e-6f);
        assertEquals(0.5f, SpearKinetics.chargeProgress(10, 20), 1e-6f);
        assertEquals(1.0f, SpearKinetics.chargeProgress(20, 20), 1e-6f);
        assertEquals(1.0f, SpearKinetics.chargeProgress(30, 20), 1e-6f);
        assertEquals(1.0f, SpearKinetics.chargeProgress(5, 0), 1e-6f);
    }

    @Test
    void testRemainingFraction() {
        Condition cond = new Condition(30, 5.0f, 0.0f);
        // Before delay (ticksUsingItem < delay)
        assertEquals(1.0f, SpearKinetics.remainingFraction(cond, 5, 20), 1e-6f);

        // Active: halfway through 30 ticks
        assertEquals(0.5f, SpearKinetics.remainingFraction(cond, 35, 20), 1e-6f);

        // Expired (active >= 30)
        assertEquals(0.0f, SpearKinetics.remainingFraction(cond, 50, 20), 1e-6f);
        assertEquals(0.0f, SpearKinetics.remainingFraction(cond, 60, 20), 1e-6f);

        // Null condition
        assertEquals(0.0f, SpearKinetics.remainingFraction(null, 25, 20), 1e-6f);
    }

    @Test
    void testWindowState() {
        Condition cond = new Condition(40, 5.0f, 8.0f);
        int delay = 20;

        // Pending during charge-up
        assertEquals(WindowState.PENDING, SpearKinetics.state(cond, 10, delay, 10, 10, true));

        // Active, but speeds unmet
        assertEquals(WindowState.UNMET, SpearKinetics.state(cond, 30, delay, 3, 2, true));

        // Active, attacker speed met, but relative speed unmet
        assertEquals(WindowState.UNMET, SpearKinetics.state(cond, 30, delay, 6, 2, true));

        // Active, speeds met with target
        assertEquals(WindowState.LIT, SpearKinetics.state(cond, 30, delay, 6, 10, true));

        // Active, speeds met without target (fails if relative speed > 0 required)
        assertEquals(WindowState.UNMET, SpearKinetics.state(cond, 30, delay, 6, 0, false));

        // Expired
        assertEquals(WindowState.EXPIRED, SpearKinetics.state(cond, 70, delay, 10, 10, true));

        // Null condition
        assertEquals(WindowState.EXPIRED, SpearKinetics.state(null, 30, delay, 10, 10, true));
    }

    @Test
    void testShouldRecharge() {
        Condition dismount = new Condition(20, 5.0f, 0.0f);
        Condition knockback = new Condition(25, 5.0f, 0.0f);
        Condition damage = new Condition(30, 5.0f, 5.0f);
        int delay = 10;

        // Disabled or keyUse not down
        assertFalse(SpearKinetics.shouldRecharge(false, true, true, 50, delay, dismount, knockback, damage));
        assertFalse(SpearKinetics.shouldRecharge(true, false, true, 50, delay, dismount, knockback, damage));

        // During charge-up
        assertFalse(SpearKinetics.shouldRecharge(true, true, true, 5, delay, dismount, knockback, damage));

        // While any condition is still active (active = 22: dismount expired, but knockback and damage active)
        assertFalse(SpearKinetics.shouldRecharge(true, true, true, 32, delay, dismount, knockback, damage));

        // When all conditions are expired (active = 35 > 30)
        assertTrue(SpearKinetics.shouldRecharge(true, true, true, 45, delay, dismount, knockback, damage));
    }

    @Test
    void testClassifyReach() {
        double min = 1.0;
        double max = 4.0;
        double margin = 0.5;

        assertEquals(ReachBand.TOO_CLOSE, SpearKinetics.classifyReach(0.5, min, max, margin));
        assertEquals(ReachBand.IN_RANGE, SpearKinetics.classifyReach(1.0, min, max, margin));
        assertEquals(ReachBand.IN_RANGE, SpearKinetics.classifyReach(3.0, min, max, margin));
        assertEquals(ReachBand.IN_RANGE, SpearKinetics.classifyReach(4.5, min, max, margin));
        assertEquals(ReachBand.TOO_FAR, SpearKinetics.classifyReach(4.6, min, max, margin));
    }

    @Test
    void testStabDamage() {
        // Base damage 5.0, rel speed 3.8, multiplier 2.0 -> 5.0 + floor(3.8 * 2.0) = 5.0 + floor(7.6) = 12.0
        float dmg = SpearKinetics.stabDamage(5.0, 3.8, 2.0f);
        assertEquals(12.0f, dmg, 1e-6f);

        // Zero relative speed
        assertEquals(5.0f, SpearKinetics.stabDamage(5.0, 0.0, 2.0f), 1e-6f);

        // Negative relative speed
        assertEquals(5.0f, SpearKinetics.stabDamage(5.0, -2.0, 2.0f), 1e-6f);
    }
}
