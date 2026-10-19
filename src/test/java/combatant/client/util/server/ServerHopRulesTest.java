/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.server;

import combatant.client.util.server.CoordFormatter.Format;
import combatant.client.util.server.ServerHopRules.StopCondition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerHopRulesTest {

    @Test
    void stopConditions() {
        assertTrue(ServerHopRules.conditionMet(StopCondition.NO_PLAYERS_NEARBY, 0, 0, 1000));
        assertFalse(ServerHopRules.conditionMet(StopCondition.NO_PLAYERS_NEARBY, 2, 5000, 1000));
        assertTrue(ServerHopRules.conditionMet(StopCondition.FAR_FROM_SPAWN, 3, 1000, 1000));
        assertFalse(ServerHopRules.conditionMet(StopCondition.BOTH, 0, 10, 1000));
        assertTrue(ServerHopRules.conditionMet(StopCondition.BOTH, 0, 2000, 1000));
        assertTrue(ServerHopRules.conditionMet(StopCondition.EITHER, 4, 2000, 1000));
        assertFalse(ServerHopRules.conditionMet(StopCondition.NEVER, 0, 99999, 1000));
    }

    @Test
    void teleportDetection() {
        assertTrue(ServerHopRules.isTeleport(100, 0, 0, 12));
        assertFalse(ServerHopRules.isTeleport(0.3, -0.8, 0.3, 12));
        assertEquals(5.0, ServerHopRules.horizontalDistance(3, 4, 0, 0), 1e-9);
    }

    @Test
    void phrases() {
        List<String> phrases = ServerHopRules.parsePhrases(" Accepted, ,TELEPORTING ");
        assertEquals(List.of("accepted", "teleporting"), phrases);
        assertTrue(ServerHopRules.containsAny("Steve accepted your request", phrases));
        assertFalse(ServerHopRules.containsAny("Steve ignored you", phrases));
    }

    @Test
    void commandTemplates() {
        assertEquals("tpa Steve", ServerHopRules.fillCommand("tpa", "Steve"));
        assertEquals("tpa Steve now", ServerHopRules.fillCommand("tpa %s now", "Steve"));
        assertEquals("tpa", ServerHopRules.fillCommand(" tpa ", ""));
    }

    @Test
    void coordinateFormats() {
        assertEquals("12 64 -31", CoordFormatter.format(Format.SPACES, 12.9, 64.0, -30.1, 0, null));
        assertEquals("[1.50, 2.00, 3.25]", CoordFormatter.format(Format.BRACKETS, 1.5, 2, 3.25, 2, null));
        assertEquals("12 -31 (overworld)", CoordFormatter.format(Format.XZ, 12, 64, -30.1, 0, "overworld"));
        assertEquals("/tp 1 2 3", CoordFormatter.format(Format.TELEPORT_COMMAND, 1, 2, 3, 0, "the_nether"));
        assertEquals("/rtp", ServerChatCheck.normalized());
    }

    private static final class ServerChatCheck {
        static String normalized() {
            return "/" + combatant.client.util.text.ServerChat.normalizeCommand("//  rtp ");
        }
    }
}
