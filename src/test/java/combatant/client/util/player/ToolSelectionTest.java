/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSelectionTest {

    private final ToolSelection picker = new ToolSelection();

    @Test
    void picksTheFastestCandidate() {
        picker.reset(1.0f, 0, 0.0f, 0.0f);
        picker.offer(0, 2.0f, 1.0f, false);
        picker.offer(3, 8.0f, 1.0f, false);
        picker.offer(5, 4.0f, 1.0f, false);
        assertEquals(3, picker.result());
    }

    @Test
    void returnsNoneWhenNothingBeatsTheBaseline() {
        picker.reset(1.0f, 0, 0.0f, 0.0f);
        picker.offer(1, 1.0f, 1.0f, false);
        picker.offer(2, 0.5f, 1.0f, false);
        assertEquals(ToolSelection.NONE, picker.result());
    }

    @Test
    void emptyDecisionReturnsNone() {
        picker.reset(1.0f, 4, 0.0f, 0.0f);
        assertEquals(ToolSelection.NONE, picker.result());
    }

    @Test
    void tieGoesToTheLowestSlotWhenNothingIsHeld() {
        picker.reset(1.0f, 8, 0.0f, 0.0f);
        picker.offer(2, 6.0f, 1.0f, false);
        picker.offer(5, 6.0f, 1.0f, false);
        assertEquals(2, picker.result());
    }

    @Test
    void heldSlotWinsATieSoEqualToolsNeverSwap() {
        picker.reset(1.0f, 5, 0.0f, 0.0f);
        picker.offer(2, 6.0f, 1.0f, false);
        picker.offer(5, 6.0f, 1.0f, false);
        assertEquals(5, picker.result());
    }

    @Test
    void heldSlotLosesWhenAnotherToolIsClearlyBetter() {
        picker.reset(1.0f, 5, 0.0f, 0.0f);
        picker.offer(2, 9.0f, 1.0f, false);
        picker.offer(5, 6.0f, 1.0f, false);
        assertEquals(2, picker.result());
    }

    @Test
    void floatNoiseDoesNotCountAsBetter() {
        picker.reset(1.0f, 5, 0.0f, 0.0f);
        picker.offer(1, 6.00001f, 1.0f, false);
        picker.offer(5, 6.0f, 1.0f, false);
        assertEquals(5, picker.result());
    }

    @Test
    void durabilityGuardSkipsWornToolsAndFallsBackToTheNextBest() {
        picker.reset(1.0f, 0, 0.10f, 0.0f);
        picker.offer(1, 9.0f, 0.05f, false);
        picker.offer(2, 5.0f, 0.80f, false);
        assertEquals(2, picker.result());
    }

    @Test
    void durabilityGuardCanLeaveNoCandidate() {
        picker.reset(1.0f, 0, 0.50f, 0.0f);
        picker.offer(1, 9.0f, 0.20f, false);
        assertEquals(ToolSelection.NONE, picker.result());
    }

    @Test
    void zeroGuardKeepsEvenAlmostBrokenTools() {
        picker.reset(1.0f, 0, 0.0f, 0.0f);
        picker.offer(1, 9.0f, 0.001f, false);
        assertEquals(1, picker.result());
    }

    @Test
    void heldButWornSlotDoesNotWinTheTie() {
        picker.reset(1.0f, 1, 0.10f, 0.0f);
        picker.offer(1, 9.0f, 0.05f, false);
        picker.offer(2, 9.0f, 0.90f, false);
        assertEquals(2, picker.result());
    }

    @Test
    void preferredBonusFlipsACloseCall() {
        picker.reset(1.0f, 0, 0.0f, 0.5f);
        picker.offer(1, 10.0f, 1.0f, false);
        picker.offer(2, 8.0f, 1.0f, true);
        assertEquals(2, picker.result());
    }

    @Test
    void preferredBonusDoesNotOverrideAHugeGap() {
        picker.reset(1.0f, 0, 0.0f, 0.5f);
        picker.offer(1, 30.0f, 1.0f, false);
        picker.offer(2, 8.0f, 1.0f, true);
        assertEquals(1, picker.result());
    }

    @Test
    void resetClearsThePreviousDecision() {
        picker.reset(1.0f, 0, 0.0f, 0.0f);
        picker.offer(3, 9.0f, 1.0f, false);
        assertEquals(3, picker.result());

        picker.reset(1.0f, 0, 0.0f, 0.0f);
        assertEquals(ToolSelection.NONE, picker.result());
    }

    @Test
    void swordBeatsAxeAndBareHandOnDamagePerSecond() {
        float hand = ToolSelection.weaponScore(0.0, 0.0);
        float sword = ToolSelection.weaponScore(7.0, -2.4);
        float axe = ToolSelection.weaponScore(9.0, -3.0);
        assertEquals(4.0f, hand, 1.0e-4f);
        assertTrue(sword > axe, "sword " + sword + " should out-dps axe " + axe);
        assertTrue(axe > hand);
    }

    @Test
    void slowHeavyItemsDoNotBeatTheBareHand() {
        // Mace-like: +5 damage, -3.4 speed.
        assertTrue(ToolSelection.weaponScore(5.0, -3.4) < ToolSelection.weaponScore(0.0, 0.0));
    }

    @Test
    void attackSpeedNeverGoesNegative() {
        assertEquals(0.0f, ToolSelection.weaponScore(7.0, -10.0));
    }

    @Test
    void durabilityFractionHandlesUnbreakableAndBrokenItems() {
        assertEquals(1.0f, ToolSelection.durabilityLeft(0, 0));
        assertEquals(0.75f, ToolSelection.durabilityLeft(250, 1000), 1.0e-6f);
        assertEquals(0.0f, ToolSelection.durabilityLeft(1200, 1000));
    }
}
