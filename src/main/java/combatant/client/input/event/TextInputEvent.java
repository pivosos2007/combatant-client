/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input.event;

import combatant.client.events.Event;
import combatant.client.input.InputFocus;

public final class TextInputEvent extends Event {
    private final String text;
    private final InputFocus.Snapshot focus;

    public TextInputEvent(String text, InputFocus.Snapshot focus) {
        this.text = text == null ? "" : text;
        this.focus = focus;
    }

    public String text() { return text; }
    public InputFocus.Snapshot focus() { return focus; }
}
