/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import lombok.Getter;
import net.minecraft.client.input.KeyEvent;
import combatant.client.events.Event;

@Getter
public final class KeyInputEvent extends Event {
    private final int action;
    private final KeyEvent input;

    public KeyInputEvent(int action, KeyEvent input) {
        this.action = action;
        this.input = input;
    }

}
