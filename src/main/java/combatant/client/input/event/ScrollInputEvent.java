/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input.event;

import combatant.client.events.Event;
import combatant.client.input.InputFocus;

public final class ScrollInputEvent extends Event {
    private final double x;
    private final double y;
    private final double horizontal;
    private final double vertical;
    private final InputFocus.Snapshot focus;

    public ScrollInputEvent(double x, double y, double horizontal, double vertical, InputFocus.Snapshot focus) {
        this.x = x;
        this.y = y;
        this.horizontal = horizontal;
        this.vertical = vertical;
        this.focus = focus;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double horizontal() { return horizontal; }
    public double vertical() { return vertical; }
    public InputFocus.Snapshot focus() { return focus; }
}
