/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.anchormacro;

import combatant.client.features.module.modules.combat.anchormacro.AnchorMacroPlanner.AbortReason;
import combatant.client.features.module.modules.combat.anchormacro.AnchorMacroPlanner.Decision;
import combatant.client.features.module.modules.combat.anchormacro.AnchorMacroPlanner.Disposition;
import combatant.client.features.module.modules.combat.anchormacro.AnchorMacroPlanner.State;
import combatant.client.features.module.modules.combat.anchormacro.AnchorMacroPlanner.Step;
import combatant.client.features.module.modules.combat.anchormacro.AnchorSlotPicker.DetonatorPreference;
import combatant.client.features.module.modules.combat.anchormacro.AnchorSlotPicker.ItemKind;
import combatant.client.features.module.modules.combat.anchormacro.DoubleAnchorLayout.Layout;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnchorMacroPlannerTest {

    private static State state(boolean present, int charge, int target) {
        return new State(present, charge, target, true, true, true, false);
    }

    @Test
    void emptySpotNeedsPlacing() {
        assertEquals(Step.PLACE, AnchorMacroPlanner.next(state(false, 0, 1)).step());
    }

    @Test
    void unchargedAnchorNeedsCharging() {
        assertEquals(Step.CHARGE, AnchorMacroPlanner.next(state(true, 0, 1)).step());
    }

    @Test
    void chargesAreLoadedUntilTheTargetThenDetonated() {
        assertEquals(Step.CHARGE, AnchorMacroPlanner.next(state(true, 1, 3)).step());
        assertEquals(Step.CHARGE, AnchorMacroPlanner.next(state(true, 2, 3)).step());
        assertEquals(Step.DETONATE, AnchorMacroPlanner.next(state(true, 3, 3)).step());
        assertEquals(Step.DETONATE, AnchorMacroPlanner.next(state(true, 4, 3)).step());
    }

    @Test
    void targetIsClampedToWhatAnAnchorCanHold() {
        assertEquals(Step.DETONATE, AnchorMacroPlanner.next(state(true, 1, 0)).step());
        assertEquals(Step.CHARGE, AnchorMacroPlanner.next(state(true, 3, 9)).step());
        assertEquals(Step.DETONATE, AnchorMacroPlanner.next(state(true, 4, 9)).step());
    }

    @Test
    void missingItemsAbortWithTheirReason() {
        Decision noAnchor = AnchorMacroPlanner.next(new State(false, 0, 1, false, true, true, false));
        assertEquals(Step.ABORT, noAnchor.step());
        assertEquals(AbortReason.NO_ANCHOR_ITEM, noAnchor.reason());

        Decision noGlow = AnchorMacroPlanner.next(new State(true, 0, 1, true, false, true, false));
        assertEquals(AbortReason.NO_GLOWSTONE, noGlow.reason());

        Decision noDetonator = AnchorMacroPlanner.next(new State(true, 1, 1, true, true, false, false));
        assertEquals(AbortReason.NO_DETONATOR, noDetonator.reason());
    }

    @Test
    void anAlreadyPlacedAnchorDoesNotNeedAnAnchorItem() {
        Decision decision = AnchorMacroPlanner.next(new State(true, 1, 1, false, false, true, false));
        assertEquals(Step.DETONATE, decision.step());
    }

    @Test
    void offhandGlowstoneBlocksDetonationUntilTheAnchorIsFull() {
        Decision blocked = AnchorMacroPlanner.next(new State(true, 1, 1, true, true, true, true));
        assertEquals(AbortReason.OFFHAND_FUEL, blocked.reason());
        Decision full = AnchorMacroPlanner.next(new State(true, 4, 4, true, true, true, true));
        assertEquals(Step.DETONATE, full.step());
    }

    @Test
    void dispositionRunsMatchingStepSkipsFinishedOnesAndRejectsDrift() {
        Decision charge = AnchorMacroPlanner.next(state(true, 0, 1));
        assertEquals(Disposition.SKIP, AnchorMacroPlanner.disposition(Step.PLACE, charge));
        assertEquals(Disposition.RUN, AnchorMacroPlanner.disposition(Step.CHARGE, charge));
        assertEquals(Disposition.DRIFT, AnchorMacroPlanner.disposition(Step.DETONATE, charge));

        Decision place = AnchorMacroPlanner.next(state(false, 0, 1));
        assertEquals(Disposition.DRIFT, AnchorMacroPlanner.disposition(Step.CHARGE, place));

        Decision detonate = AnchorMacroPlanner.next(state(true, 2, 2));
        assertEquals(Disposition.SKIP, AnchorMacroPlanner.disposition(Step.CHARGE, detonate));
    }

    @Test
    void abortWinsOverEveryStep() {
        Decision abort = AnchorMacroPlanner.next(new State(true, 0, 1, true, false, true, false));
        for (Step step : new Step[]{Step.PLACE, Step.CHARGE, Step.DETONATE}) {
            assertEquals(Disposition.ABORT, AnchorMacroPlanner.disposition(step, abort));
        }
    }

    @Test
    void secondChargeStepOfATwoChargeSequenceSkipsOnceTargetIsMet() {
        // Sequence: place, charge, charge, detonate for target 1 would have a surplus charge step that skips.
        Decision afterFirstCharge = AnchorMacroPlanner.next(state(true, 1, 1));
        assertEquals(Disposition.SKIP, AnchorMacroPlanner.disposition(Step.CHARGE, afterFirstCharge));
        assertEquals(Disposition.RUN, AnchorMacroPlanner.disposition(Step.DETONATE, afterFirstCharge));
    }

    @Test
    void safetyRefusesDamageAboveTheLimit() {
        assertFalse(AnchorSafety.detonationAllowed(9.0f, 8.0f, 20.0f, false, false));
        assertTrue(AnchorSafety.detonationAllowed(8.0f, 8.0f, 20.0f, false, false));
    }

    @Test
    void safetyRefusesLethalDamageUnlessATotemPopIsAccepted() {
        assertFalse(AnchorSafety.detonationAllowed(7.8f, 20.0f, 8.0f, true, false));
        assertFalse(AnchorSafety.detonationAllowed(7.8f, 20.0f, 8.0f, false, true));
        assertTrue(AnchorSafety.detonationAllowed(7.8f, 20.0f, 8.0f, true, true));
        assertTrue(AnchorSafety.detonationAllowed(7.5f, 20.0f, 8.0f, false, false));
    }

    @Test
    void safetyNeverOverridesTheConfiguredLimit() {
        assertFalse(AnchorSafety.detonationAllowed(12.0f, 8.0f, 4.0f, true, true));
    }

    private static ItemKind[] hotbar(ItemKind... kinds) {
        ItemKind[] bar = new ItemKind[9];
        Arrays.fill(bar, ItemKind.EMPTY);
        System.arraycopy(kinds, 0, bar, 0, kinds.length);
        return bar;
    }

    @Test
    void pickPrefersSelectedSlotThenFirst() {
        ItemKind[] bar = hotbar(ItemKind.GLOWSTONE, ItemKind.OTHER, ItemKind.GLOWSTONE);
        assertEquals(2, AnchorSlotPicker.pick(bar, 2, ItemKind.GLOWSTONE));
        assertEquals(0, AnchorSlotPicker.pick(bar, 1, ItemKind.GLOWSTONE));
        assertEquals(-1, AnchorSlotPicker.pick(bar, 1, ItemKind.ANCHOR));
    }

    @Test
    void detonatorNeverPicksGlowstoneOrAnchors() {
        ItemKind[] bar = new ItemKind[]{ItemKind.GLOWSTONE, ItemKind.ANCHOR, ItemKind.GLOWSTONE, ItemKind.ANCHOR,
                ItemKind.GLOWSTONE, ItemKind.ANCHOR, ItemKind.GLOWSTONE, ItemKind.ANCHOR, ItemKind.GLOWSTONE};
        assertEquals(-1, AnchorSlotPicker.pickDetonator(bar, 0, DetonatorPreference.TOTEM));
    }

    @Test
    void detonatorFollowsThePreference() {
        ItemKind[] bar = hotbar(ItemKind.SWORD, ItemKind.GLOWSTONE, ItemKind.TOTEM, ItemKind.OTHER);
        assertEquals(2, AnchorSlotPicker.pickDetonator(bar, 1, DetonatorPreference.TOTEM));
        assertEquals(0, AnchorSlotPicker.pickDetonator(bar, 1, DetonatorPreference.SWORD));
        assertEquals(3, AnchorSlotPicker.pickDetonator(hotbar(ItemKind.GLOWSTONE, ItemKind.EMPTY,
                ItemKind.EMPTY, ItemKind.OTHER), 3, DetonatorPreference.CURRENT));
    }

    @Test
    void detonatorFallsBackToTheOtherPreferredKindThenAnythingThenAnEmptyHand() {
        assertEquals(1, AnchorSlotPicker.pickDetonator(
                hotbar(ItemKind.GLOWSTONE, ItemKind.SWORD, ItemKind.OTHER), 0, DetonatorPreference.TOTEM));
        assertEquals(2, AnchorSlotPicker.pickDetonator(
                hotbar(ItemKind.GLOWSTONE, ItemKind.EMPTY, ItemKind.OTHER), 0, DetonatorPreference.TOTEM));
        assertEquals(1, AnchorSlotPicker.pickDetonator(
                hotbar(ItemKind.GLOWSTONE, ItemKind.EMPTY), 0, DetonatorPreference.TOTEM));
    }

    @Test
    void detonatorKeepsTheSelectedSlotWhenKindsTie() {
        ItemKind[] bar = hotbar(ItemKind.TOTEM, ItemKind.GLOWSTONE, ItemKind.TOTEM);
        assertEquals(2, AnchorSlotPicker.pickDetonator(bar, 2, DetonatorPreference.TOTEM));
        assertEquals(0, AnchorSlotPicker.pickDetonator(bar, 1, DetonatorPreference.TOTEM));
    }

    @Test
    void stackedLayoutPrefersAboveThenSides() {
        BlockPos first = new BlockPos(10, 64, 10);
        assertEquals(first.above(), DoubleAnchorLayout.chooseSecond(first, Layout.STACKED, p -> true));
        assertEquals(first.north(),
                DoubleAnchorLayout.chooseSecond(first, Layout.STACKED, p -> !p.equals(first.above())));
    }

    @Test
    void sideLayoutPrefersHorizontalNeighboursAndSkipsUnusableCells() {
        BlockPos first = new BlockPos(0, 70, 0);
        assertEquals(first.north(), DoubleAnchorLayout.chooseSecond(first, Layout.SIDE, p -> true));
        assertEquals(first.east(),
                DoubleAnchorLayout.chooseSecond(first, Layout.SIDE, p -> !p.equals(first.north())));
        assertEquals(first.above(), DoubleAnchorLayout.chooseSecond(first, Layout.SIDE, p -> p.getY() > 70));
        assertNull(DoubleAnchorLayout.chooseSecond(first, Layout.SIDE, p -> false));
    }
}
