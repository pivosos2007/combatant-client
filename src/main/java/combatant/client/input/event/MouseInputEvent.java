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

public final class MouseInputEvent extends InputButtonEvent {
    private final double x;
    private final double y;

    public MouseInputEvent(InputKey key, InputAction action, InputModifiers modifiers,
                           InputFocus.Snapshot focus, double x, double y) {
        super(key, action, modifiers, focus);
        this.x = x;
        this.y = y;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }
}
