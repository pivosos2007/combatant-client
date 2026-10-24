/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputBindingRegistryTest {
    private static final InputFocus.Snapshot GAME = new InputFocus.Snapshot(false, false);
    private static final InputFocus.Snapshot SCREEN = new InputFocus.Snapshot(true, false);
    private InputState state;
    private InputBindingRegistry registry;

    @BeforeEach
    void setUp() {
        CombatantInput.focus().clear();
        state = new InputState();
        registry = new InputBindingRegistry(state);
    }

    @AfterEach
    void tearDown() {
        CombatantInput.focus().clear();
    }

    private void key(String name, InputAction action, InputFocus.Snapshot focus) {
        InputKey key = InputKeyCatalog.fromName(name);
        if (state.apply(key, action)) registry.onStateChanged(key, focus);
        else registry.onFocusChanged(focus);
    }

    @Test
    void unrelatedKeysAndRepeatsNeverQueueChordPresses() {
        registry.bind("action", "LEFT_CTRL+A");
        key("B", InputAction.PRESS, GAME);
        key("A", InputAction.PRESS, GAME);
        key("A", InputAction.REPEAT, GAME);
        assertFalse(registry.hasPendingGamePress());
        key("LEFT_CTRL", InputAction.PRESS, GAME);
        assertTrue(registry.hasPendingGamePress());
        assertTrue(registry.consumePress("action", InputScope.GAME));
        assertFalse(registry.hasPendingGamePress());
        assertFalse(registry.consumePress("action", InputScope.GAME));
        key("A", InputAction.REPEAT, GAME);
        assertFalse(registry.hasPendingGamePress());
    }

    @Test
    void overlappingChordsDoNotProduceDuplicateEdges() {
        registry.bind("action", "A+B");
        registry.bind("action", "B+C");
        key("A", InputAction.PRESS, GAME);
        key("B", InputAction.PRESS, GAME);
        assertTrue(registry.consumePress("action", InputScope.GAME));
        key("C", InputAction.PRESS, GAME);
        key("A", InputAction.RELEASE, GAME);
        assertFalse(registry.hasPendingGamePress());
        key("B", InputAction.RELEASE, GAME);
        key("B", InputAction.PRESS, GAME);
        assertTrue(registry.consumePress("action", InputScope.GAME));
    }

    @Test
    void screenFocusDropsGameQueueButPreservesGlobalQueue() {
        registry.bind("action", "A");
        key("A", InputAction.PRESS, GAME);
        assertTrue(registry.hasPendingGamePress());
        registry.onFocusChanged(SCREEN);
        assertFalse(registry.hasPendingGamePress());
        assertFalse(registry.consumePress("action", InputScope.GAME));
        assertTrue(registry.consumePress("action", InputScope.GLOBAL));
        registry.onFocusChanged(GAME);
        assertFalse(registry.hasPendingGamePress());
        key("A", InputAction.RELEASE, GAME);
        key("A", InputAction.PRESS, GAME);
        assertTrue(registry.consumePress("action", InputScope.GAME));
    }

    @Test
    void unbindRemovesOnlyThatBindingAndItsPendingCount() {
        registry.bind("first", "A");
        registry.bind("second", "A");
        key("A", InputAction.PRESS, GAME);
        registry.unbind("first");
        assertTrue(registry.hasPendingGamePress());
        assertTrue(registry.consumePress("second", InputScope.GAME));
        assertFalse(registry.hasPendingGamePress());
        assertFalse(registry.consumePress("first", InputScope.GLOBAL));
        key("A", InputAction.RELEASE, GAME);
        key("A", InputAction.PRESS, GAME);
        assertTrue(registry.consumePress("second", InputScope.GAME));
    }

    @Test
    void rebindingWhileHeldDoesNotSynthesizeAnInputEdge() {
        key("A", InputAction.PRESS, GAME);
        registry.bind("action", "A");
        assertFalse(registry.hasPendingGamePress());
        key("A", InputAction.RELEASE, GAME);
        key("A", InputAction.PRESS, GAME);
        assertTrue(registry.consumePress("action", InputScope.GAME));
        registry.clearState();
        assertFalse(registry.hasPendingGamePress());
    }

    @Test
    void mouseAndKeyboardKeysNeverAlias() {
        registry.bind("mouse", "MOUSE_LEFT");
        registry.bind("key", "A");
        key("MOUSE_LEFT", InputAction.PRESS, GAME);
        assertTrue(registry.consumePress("mouse", InputScope.GAME));
        assertFalse(registry.consumePress("key", InputScope.GAME));
    }
}
