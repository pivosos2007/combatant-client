/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input;

public record InputModifiers(
        boolean shift,
        boolean control,
        boolean alt,
        boolean superKey,
        boolean capsLock,
        boolean numLock,
        boolean shortcut
) {
    public static final InputModifiers NONE = new InputModifiers(false, false, false, false, false, false, false);

    public int mask() {
        int mask = 0;
        if (shift) mask |= InputModifierMask.SHIFT;
        if (control) mask |= InputModifierMask.CONTROL;
        if (alt) mask |= InputModifierMask.ALT;
        if (superKey) mask |= InputModifierMask.SUPER;
        if (capsLock) mask |= InputModifierMask.CAPS_LOCK;
        if (numLock) mask |= InputModifierMask.NUM_LOCK;
        if (shortcut) mask |= InputModifierMask.SHORTCUT;
        return mask;
    }
}
