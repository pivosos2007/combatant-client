/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input;

import java.util.List;

public record InputChord(List<InputKey> keys) {
    public InputChord {
        keys = keys == null ? List.of() : List.copyOf(keys);
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public boolean isHeld(InputState state) {
        if (keys.isEmpty()) return false;
        for (InputKey key : keys) {
            if (!state.isPressed(key)) return false;
        }
        return true;
    }
}
