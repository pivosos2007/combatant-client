package combatant.client.features.module.modules.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CriticalsGrimModeTest {
    @Test
    void packetTricksBecomeJumpCrits() {
        assertEquals(Criticals.Mode.JUMP, Criticals.grimSafeMode(Criticals.Mode.PACKET));
        assertEquals(Criticals.Mode.JUMP, Criticals.grimSafeMode(Criticals.Mode.NO_GROUND));
        assertEquals(Criticals.Mode.JUMP, Criticals.grimSafeMode(Criticals.Mode.VULCAN_297));
    }

    @Test
    void realCritsAndOffStayAsChosen() {
        assertEquals(Criticals.Mode.JUMP, Criticals.grimSafeMode(Criticals.Mode.JUMP));
        assertEquals(Criticals.Mode.TIMER, Criticals.grimSafeMode(Criticals.Mode.TIMER));
        assertEquals(Criticals.Mode.OFF, Criticals.grimSafeMode(Criticals.Mode.OFF));
    }
}
