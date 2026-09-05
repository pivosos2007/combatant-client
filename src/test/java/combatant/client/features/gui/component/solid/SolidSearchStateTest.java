package combatant.client.features.gui.component.solid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolidSearchStateTest {
    @Test
    void editsAtCaretAndReplacesSelection() {
        SolidSearchState state = new SolidSearchState();
        state.setText("players");
        state.placeCursor(7, false);
        assertTrue(state.keyPressed(263, true));
        assertTrue(state.keyPressed(263, true));
        assertEquals(5, state.selectionStart());
        assertEquals(7, state.selectionEnd());
        assertTrue(state.type('r'));
        assertEquals("player", state.text());
        assertEquals(6, state.cursor());
    }
}
