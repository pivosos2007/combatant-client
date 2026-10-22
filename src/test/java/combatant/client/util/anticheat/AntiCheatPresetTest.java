package combatant.client.util.anticheat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import org.junit.jupiter.api.Test;

class AntiCheatPresetTest {

    private final EnumValue<AntiCheatPreset> preset =
            new EnumValue<>("anticheat_mode", AntiCheatPreset.VANILLA, AntiCheatPreset.values());
    private final NumberValue<Double> reach = new NumberValue<>("range", 4.2, 1.0, 6.0);
    private final BooleanValue airPlace = new BooleanValue("air_place", true);

    AntiCheatPresetTest() {
        AntiCheatPresetDefaults.bind(preset)
                .cap(reach, AntiCheatPreset.GRIM_ENTITY_RANGE, AntiCheatPreset.VANILLA_ENTITY_RANGE)
                .set(airPlace, false, true);
    }

    @Test
    void pickingGrimWritesTheRecommendationOnce() {
        preset.select("grim");
        assertEquals(3.0, reach.get());
        assertEquals(false, airPlace.get());

        // The player raises reach past Grim's recommendation afterwards: nothing pulls it back down.
        reach.set(3.4);
        airPlace.set(true);
        assertEquals(3.4, reach.get());
        assertEquals(true, airPlace.get());
    }

    @Test
    void grimLeavesValuesBelowTheCapAlone() {
        reach.set(2.5);
        preset.select("grim");
        assertEquals(2.5, reach.get());
    }

    @Test
    void pickingVanillaWritesItsValues() {
        preset.select("grim");
        preset.select("vanilla");
        assertEquals(6.0, reach.get());
        assertEquals(true, airPlace.get());
    }

    @Test
    void reselectingTheCurrentPresetChangesNothing() {
        preset.select("vanilla");
        assertEquals(4.2, reach.get());
    }

    @Test
    void loadingASavedConfigDoesNotApplyDefaults() {
        preset.fromJson("grim");
        assertEquals(AntiCheatPreset.GRIM, preset.get());
        assertEquals(4.2, reach.get());
    }

    @Test
    void customFromOldConfigsLoadsAsVanilla() {
        preset.fromJson("grim");
        preset.fromJson("custom");
        assertEquals(AntiCheatPreset.VANILLA, preset.get());
        assertEquals(java.util.List.of("grim", "vanilla"), preset.getOptions());
    }
}
