package combatant.client.features.module.modules.movement;

import combatant.client.config.values.EnumValue;
import net.minecraft.world.phys.Vec2;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoSlowGrimTickTest {

    @Test
    @SuppressWarnings("unchecked")
    void grimTickAlternatesEquallyOnAllAxes() throws Exception {
        NoSlow noSlow = new NoSlow();
        Field modeField = NoSlow.class.getDeclaredField("mode");
        modeField.setAccessible(true);
        ((EnumValue<NoSlow.Mode>) modeField.get(noSlow)).set(NoSlow.Mode.GRIM_TICK);

        Field ticksField = NoSlow.class.getDeclaredField("grimTicks");
        ticksField.setAccessible(true);

        Vec2 forward = new Vec2(0.0f, 1.0f);   // W
        Vec2 backward = new Vec2(0.0f, -1.0f); // S
        Vec2 left = new Vec2(1.0f, 0.0f);      // A
        Vec2 right = new Vec2(-1.0f, 0.0f);    // D
        Vec2 diagonal = new Vec2(0.707f, 0.707f); // W + A

        // Tick 1: slowed tick (grimTicks = 1, < 2) -> scales by 0.2f
        ticksField.setInt(noSlow, 1);
        assertEquals(0.2f, noSlow.getModifiedInput(forward, null).y, 1e-4f);
        assertEquals(-0.2f, noSlow.getModifiedInput(backward, null).y, 1e-4f);
        assertEquals(0.2f, noSlow.getModifiedInput(left, null).x, 1e-4f);
        assertEquals(-0.2f, noSlow.getModifiedInput(right, null).x, 1e-4f);
        assertEquals(0.707f * 0.2f, noSlow.getModifiedInput(diagonal, null).x, 1e-4f);
        assertEquals(0.707f * 0.2f, noSlow.getModifiedInput(diagonal, null).y, 1e-4f);

        // Tick 2: unslowed tick (grimTicks = 2, >= 2) -> full unscaled input and reset to 0
        ticksField.setInt(noSlow, 2);
        Vec2 fRes = noSlow.getModifiedInput(forward, null);
        assertEquals(1.0f, fRes.y, 1e-4f);
        assertEquals(0, ticksField.getInt(noSlow));

        ticksField.setInt(noSlow, 2);
        Vec2 bRes = noSlow.getModifiedInput(backward, null);
        assertEquals(-1.0f, bRes.y, 1e-4f);
        assertEquals(0, ticksField.getInt(noSlow));

        ticksField.setInt(noSlow, 2);
        Vec2 lRes = noSlow.getModifiedInput(left, null);
        assertEquals(1.0f, lRes.x, 1e-4f);
        assertEquals(0, ticksField.getInt(noSlow));

        ticksField.setInt(noSlow, 2);
        Vec2 rRes = noSlow.getModifiedInput(right, null);
        assertEquals(-1.0f, rRes.x, 1e-4f);
        assertEquals(0, ticksField.getInt(noSlow));
    }
}
