/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input.event;

import combatant.client.input.InputAction;
import combatant.client.input.InputFocus;
import combatant.client.input.InputKey;
import combatant.client.input.InputModifiers;

public final class KeyboardInputEvent extends InputButtonEvent {
    public KeyboardInputEvent(InputKey key, InputAction action, InputModifiers modifiers, InputFocus.Snapshot focus) {
        super(key, action, modifiers, focus);
    }
}
