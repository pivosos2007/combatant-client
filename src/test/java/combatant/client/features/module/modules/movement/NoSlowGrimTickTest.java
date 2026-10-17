package combatant.client.features.module.modules.movement;

import combatant.client.config.values.EnumValue;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoSlowGrimTickTest {

    private static final float VANILLA_USE_SLOWDOWN = 0.2f;

    @SuppressWarnings("unchecked")
    private static NoSlow noSlow(NoSlow.Mode mode) throws Exception {
        NoSlow noSlow = new NoSlow();
        Field modeField = NoSlow.class.getDeclaredField("mode");
        modeField.setAccessible(true);
        ((EnumValue<NoSlow.Mode>) modeField.get(noSlow)).set(mode);
        return noSlow;
    }

    private static Field grimTicks() throws Exception {
        Field ticks = NoSlow.class.getDeclaredField("grimTicks");
        ticks.setAccessible(true);
        return ticks;
    }

    @Test
    void grimTickKeepsTheSlowdownOnOneTickAndLiftsItOnTheNext() throws Exception {
        NoSlow noSlow = noSlow(NoSlow.Mode.GRIM_TICK);
        Field ticks = grimTicks();

        ticks.setInt(noSlow, 1);
        assertEquals(VANILLA_USE_SLOWDOWN, noSlow.getItemUseSpeedMultiplier(VANILLA_USE_SLOWDOWN), 1e-6f);
        assertEquals(1, ticks.getInt(noSlow));

        ticks.setInt(noSlow, 2);
        assertEquals(1.0f, noSlow.getItemUseSpeedMultiplier(VANILLA_USE_SLOWDOWN), 1e-6f);
        assertEquals(0, ticks.getInt(noSlow));
    }

    @Test
    void legacyGrimModePlaysAsGrimTick() throws Exception {
        NoSlow noSlow = noSlow(NoSlow.Mode.GRIM);
        Field ticks = grimTicks();

        ticks.setInt(noSlow, 1);
        assertEquals(VANILLA_USE_SLOWDOWN, noSlow.getItemUseSpeedMultiplier(VANILLA_USE_SLOWDOWN), 1e-6f);
        ticks.setInt(noSlow, 2);
        assertEquals(1.0f, noSlow.getItemUseSpeedMultiplier(VANILLA_USE_SLOWDOWN), 1e-6f);
    }

    @Test
    void vanillaAndCustomLiftTheSlowdownEveryTick() throws Exception {
        for (NoSlow.Mode mode : new NoSlow.Mode[] {NoSlow.Mode.VANILLA, NoSlow.Mode.CUSTOM}) {
            NoSlow noSlow = noSlow(mode);
            grimTicks().setInt(noSlow, 1);
            assertEquals(1.0f, noSlow.getItemUseSpeedMultiplier(VANILLA_USE_SLOWDOWN), 1e-6f, mode.name());
        }
    }
}
