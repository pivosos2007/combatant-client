/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttributeSwapCycleAttackWindowTest {
    @Test
    void unequippedElytraDoesNotSuppressReportedFall() {
        AttributeSwap.CycleAttackWindow window = new AttributeSwap.CycleAttackWindow();
        window.notePosition(6.0, true, false, false);
        assertTrue(window.ready(2.0));
    }

    @Test
    void activeElytraAndGroundedMovementCannotPrimeMace() {
        AttributeSwap.CycleAttackWindow window = new AttributeSwap.CycleAttackWindow();
        window.notePosition(6.0, true, false, true);
        assertFalse(window.ready(2.0));
        window.notePosition(6.0, false, true, false);
        assertFalse(window.ready(2.0));
    }

    @Test
    void realFallMustExceedConfiguredThreshold() {
        AttributeSwap.CycleAttackWindow window = new AttributeSwap.CycleAttackWindow();
        window.notePosition(1.75, false, false, false);
        assertFalse(window.ready(2.0));
        window.notePosition(2.2, false, false, false);
        assertTrue(window.ready(2.0));
    }
}
