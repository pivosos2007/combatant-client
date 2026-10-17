/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input;

public final class CombatantInput {
    private static final InputState STATE = new InputState();
    private static final InputFocus FOCUS = new InputFocus();
    private static final InputBindingRegistry BINDINGS = new InputBindingRegistry(STATE);
    private static final InputRouter ROUTER = new InputRouter(STATE, BINDINGS);

    private CombatantInput() {
    }

    public static InputState state() { return STATE; }
    public static InputFocus focus() { return FOCUS; }
    public static InputBindingRegistry bindings() { return BINDINGS; }
    public static InputRouter router() { return ROUTER; }

    public static void clearState() {
        STATE.clear();
        FOCUS.clear();
        BINDINGS.clearState();
    }
}
