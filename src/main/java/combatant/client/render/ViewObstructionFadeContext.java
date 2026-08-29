/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render;

import java.util.ArrayDeque;

public enum ViewObstructionFadeContext {
    ;
    private static final ThreadLocal<ArrayDeque<State>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    public static void push(ViewObstructionFadeState state) {
        if (state == null) {
            STACK.get().addLast(State.INACTIVE);
            return;
        }

        STACK.get().addLast(new State(
                state.combatant$isViewObstructionFadeActive(),
                state.combatant$getViewObstructionFadeAlpha()
        ));
    }

    public static void pop() {
        ArrayDeque<State> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.removeLast();
        }
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    public static boolean isActive() {
        return current().active;
    }

    public static float alpha() {
        return current().alpha;
    }

    /**
     * Encodes the current fade in the same low-bit marker consumed by Combatant's entity shader.
     * Custom rig geometry bypasses SubmitNodeCollection.submitModel(), so it must opt into the
     * marker explicitly at submit time.
     */
    public static int applyToArgb(int argb, boolean msaaActive) {
        State state = current();
        if (!state.active || state.alpha >= 0.99f) return argb;
        int baseColor = argb == -1 ? 0xFFFFFFFF : ((argb & 0x00FFFFFF) | 0xFF000000);
        int marker = msaaActive ? 0x00010100 : 0x00010001;
        int rgb = (baseColor & 0x00FEFEFE) | marker;
        int alphaByte = Math.round(255.0f * Math.max(0.0f, Math.min(1.0f, state.alpha)));
        return rgb | ((alphaByte & 0xFF) << 24);
    }

    private static State current() {
        ArrayDeque<State> stack = STACK.get();
        return stack.isEmpty() ? State.INACTIVE : stack.peekLast();
    }

    private record State(boolean active, float alpha) {
        private static final State INACTIVE = new State(false, 1.0f);
    }
}
