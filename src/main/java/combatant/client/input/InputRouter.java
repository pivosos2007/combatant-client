/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input;

import combatant.client.events.Event;
import combatant.client.events.Events;
import combatant.client.input.event.InputButtonEvent;
import combatant.client.input.event.MouseInputEvent;

public final class InputRouter {
    private final InputState state;
    private final InputBindingRegistry bindings;

    InputRouter(InputState state, InputBindingRegistry bindings) {
        this.state = state;
        this.bindings = bindings;
    }

    public <T extends InputButtonEvent> T route(T event) {
        state.apply(event.key(), event.action());
        if (event instanceof MouseInputEvent mouse) state.updateMousePosition(mouse.x(), mouse.y());
        bindings.onStateChanged(event.focus());
        return Events.BUS.post(event);
    }

    public <T extends Event> T route(T event) {
        return Events.BUS.post(event);
    }
}
