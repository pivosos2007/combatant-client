/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class PlayerRigScriptRuntimeTest {
    @Test
    void wrapsPackedContextAsOneVarargInsteadOfSpreadingIt() {
        Object[] packed = new Object[]{"player", 42};
        Object[] arguments = PlayerRigScriptRuntime.invocationArguments(packed);
        assertEquals(1, arguments.length);
        assertSame(packed, arguments[0]);
    }
}
