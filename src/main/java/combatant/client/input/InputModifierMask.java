/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input;

public final class InputModifierMask {
    public static final int SHIFT = 1;
    public static final int CONTROL = 1 << 1;
    public static final int ALT = 1 << 2;
    public static final int SUPER = 1 << 3;
    public static final int CAPS_LOCK = 1 << 4;
    public static final int NUM_LOCK = 1 << 5;
    public static final int SHORTCUT = 1 << 6;

    private InputModifierMask() {
    }

    public static boolean has(int mask, int modifier) {
        return (mask & modifier) != 0;
    }
}
