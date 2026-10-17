package combatant.client.features.module.modules.combat;

import combatant.client.config.values.EnumValue;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class KillAuraRotationTimingTest {

    @Test
    void rotationTimingEnumContainsSnap() {
        assertNotNull(KillAura.RotationTiming.valueOf("SNAP"));
        assertNotNull(KillAura.RotationTiming.valueOf("NORMAL"));
        assertNotNull(KillAura.RotationTiming.valueOf("ON_TICK"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void snapTimingCanBeConfigured() throws Exception {
        KillAura aura = new KillAura();
        Field timingField = KillAura.class.getDeclaredField("rotationTiming");
        timingField.setAccessible(true);
        EnumValue<KillAura.RotationTiming> timing = (EnumValue<KillAura.RotationTiming>) timingField.get(aura);

        assertEquals(KillAura.RotationTiming.NORMAL, timing.get());
        timing.set(KillAura.RotationTiming.SNAP);
        assertEquals(KillAura.RotationTiming.SNAP, timing.get());

        // When disabled or without player, willAttackInTicks returns false safely
        assertFalse(aura.willAttackInTicks(0));
        assertFalse(aura.willAttackInTicks(null, 0));
        assertFalse(aura.willAttackInTicks(null, 5));
    }
}
