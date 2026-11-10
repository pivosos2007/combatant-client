/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.KeyMapping;
import combatant.client.events.Event;

/**
 * Allows overriding the value returned by a key-down query without modifying
 * the stored {@link KeyMapping} state.
 */
@Getter
public final class KeybindIsPressedEvent extends Event {

    private final KeyMapping keyBinding;
    @Setter
    private boolean pressed;

    public KeybindIsPressedEvent(KeyMapping keyBinding, boolean pressed) {
        this.keyBinding = keyBinding;
        this.pressed = pressed;
    }

}
