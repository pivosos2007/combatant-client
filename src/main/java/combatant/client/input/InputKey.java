/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input;

import java.util.Locale;
import java.util.Objects;

public record InputKey(int physicalKey, int shortcutKey, InputDevice device, String canonicalName) {
    public InputKey {
        Objects.requireNonNull(device, "device");
        canonicalName = canonicalName == null || canonicalName.isBlank()
                ? "UNKNOWN"
                : canonicalName.trim().toUpperCase(Locale.ROOT);
    }

    public boolean samePhysicalKey(InputKey other) {
        return other != null && device == other.device && physicalKey == other.physicalKey;
    }
}
