/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input;

public final class InputFocus {
    private volatile Object screenOwner;
    private volatile Object textOwner;

    public void updateScreen(Object owner) {
        screenOwner = owner;
    }

    public void claimText(Object owner) {
        if (owner != null) textOwner = owner;
    }

    public void releaseText(Object owner) {
        if (textOwner == owner) textOwner = null;
    }

    public void clear() {
        screenOwner = null;
        textOwner = null;
    }

    public Snapshot snapshot() {
        return new Snapshot(screenOwner != null, textOwner != null);
    }

    public record Snapshot(boolean screenFocused, boolean textFocused) {
        public boolean allowsGameBindings() {
            return !screenFocused && !textFocused;
        }
    }
}
