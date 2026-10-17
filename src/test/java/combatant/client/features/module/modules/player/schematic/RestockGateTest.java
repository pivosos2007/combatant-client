package combatant.client.features.module.modules.player.schematic;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RestockGateTest {
    @Test
    void notReadyBeforeAnyStillTick() {
        assertFalse(new RestockGate().ready());
    }

    @Test
    void readyOnlyAfterConsecutiveStillTicks() {
        RestockGate gate = new RestockGate();
        gate.observe(false);
        assertFalse(gate.ready());
        gate.observe(false);
        assertTrue(gate.ready());
    }

    @Test
    void movementResetsTheCount() {
        RestockGate gate = new RestockGate();
        gate.observe(false);
        gate.observe(false);
        gate.observe(true);
        assertFalse(gate.ready());
        gate.observe(false);
        assertFalse(gate.ready());
        gate.observe(false);
        assertTrue(gate.ready());
    }

    @Test
    void resetForgetsStillness() {
        RestockGate gate = new RestockGate();
        gate.observe(false);
        gate.observe(false);
        gate.reset();
        assertFalse(gate.ready());
    }
}
