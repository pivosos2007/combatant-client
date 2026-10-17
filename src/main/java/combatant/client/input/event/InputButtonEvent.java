/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input.event;

import combatant.client.events.Event;
import combatant.client.input.InputAction;
import combatant.client.input.InputFocus;
import combatant.client.input.InputKey;
import combatant.client.input.InputModifiers;

public abstract class InputButtonEvent extends Event {
    private final InputKey key;
    private final InputAction action;
    private final InputModifiers modifiers;
    private final InputFocus.Snapshot focus;

    protected InputButtonEvent(InputKey key, InputAction action, InputModifiers modifiers, InputFocus.Snapshot focus) {
        this.key = key;
        this.action = action;
        this.modifiers = modifiers;
        this.focus = focus;
    }

    public InputKey key() {
        return key;
    }

    public InputAction action() {
        return action;
    }

    public InputModifiers modifiers() {
        return modifiers;
    }

    public InputFocus.Snapshot focus() {
        return focus;
    }
}
