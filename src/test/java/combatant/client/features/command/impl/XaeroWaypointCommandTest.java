/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class XaeroWaypointCommandTest {
    @Test
    void parsesAbsoluteAndCurrentCoordinates() {
        assertEquals(120, XaeroWaypointCommand.parseCoordinate("120", 7));
        assertEquals(-300, XaeroWaypointCommand.parseCoordinate("-300", 7));
        assertEquals(7, XaeroWaypointCommand.parseCoordinate("~", 7));
    }

    @Test
    void rejectsInvalidCoordinates() {
        assertNull(XaeroWaypointCommand.parseCoordinate(null, 7));
        assertNull(XaeroWaypointCommand.parseCoordinate("", 7));
        assertNull(XaeroWaypointCommand.parseCoordinate("abc", 7));
        assertNull(XaeroWaypointCommand.parseCoordinate("~2", 7));
    }
}
