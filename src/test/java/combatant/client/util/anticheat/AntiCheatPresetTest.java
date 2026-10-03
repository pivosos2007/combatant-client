package combatant.client.util.anticheat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AntiCheatPresetTest {

    @Test
    void customKeepsTheUsersValue() {
        assertEquals(5.5, AntiCheatPreset.CUSTOM.limit(5.5, 3.0, 6.0));
        assertEquals(1.0f, AntiCheatPreset.CUSTOM.limit(1.0f, 3.0f, 6.0f));
    }

    @Test
    void grimCapsButNeverRaises() {
        assertEquals(3.0, AntiCheatPreset.GRIM.limit(6.0, 3.0, 6.0));
        assertEquals(2.0, AntiCheatPreset.GRIM.limit(2.0, 3.0, 6.0));
        assertEquals(4.5f, AntiCheatPreset.GRIM.limit(5.0f, 4.5f, 6.0f));
    }

    @Test
    void vanillaIgnoresTheUsersValue() {
        assertEquals(6.0, AntiCheatPreset.VANILLA.limit(1.0, 3.0, 6.0));
    }

    @Test
    void pickChoosesPerPreset() {
        assertEquals("user", AntiCheatPreset.CUSTOM.pick("user", "grim", "vanilla"));
        assertEquals("grim", AntiCheatPreset.GRIM.pick("user", "grim", "vanilla"));
        assertEquals("vanilla", AntiCheatPreset.VANILLA.pick("user", "grim", "vanilla"));
    }

    @Test
    void idsAreStableConfigValues() {
        assertEquals("custom", AntiCheatPreset.CUSTOM.getId());
        assertEquals("grim", AntiCheatPreset.GRIM.getId());
        assertEquals("vanilla", AntiCheatPreset.VANILLA.getId());
    }
}
