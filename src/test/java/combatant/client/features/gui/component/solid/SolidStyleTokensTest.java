package combatant.client.features.gui.component.solid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SolidStyleTokensTest {
    @Test
    void baselinePaletteMatchesSourceAndAccentShiftsTintedSurfaces() {
        SolidStyleTokens baseline = SolidStyleTokens.defaults();
        SolidStyleTokens green = new SolidStyleTokens(0xFF40E080);
        assertEquals(0xFF906BFF, baseline.accent());
        assertEquals(0xE618151D, baseline.surface());
        assertEquals(0x593D3647, baseline.separator());
        assertNotEquals(baseline.surface(), green.surface());
        assertEquals(0xFFFFFFFF, green.foreground());
    }

    @Test
    void rowSignalsRemainIndependent() {
        SolidStyleTokens tokens = SolidStyleTokens.defaults();
        int base = tokens.rowBackground(0, 0, 0);
        assertNotEquals(base, tokens.rowBackground(1, 0, 0));
        assertNotEquals(base, tokens.rowBackground(0, 1, 0));
        assertNotEquals(base, tokens.rowBackground(0, 0, 1));
    }
}
