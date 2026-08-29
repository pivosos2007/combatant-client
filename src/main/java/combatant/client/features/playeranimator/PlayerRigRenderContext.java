/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator;

import java.util.ArrayDeque;
import java.util.Deque;

/** Nested-submit-safe access to the player rig for armor, held-item and feature renderers. */
public enum PlayerRigRenderContext {
    ;

    private static final ThreadLocal<Deque<Object>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final Object NON_PLAYER = new Object();

    public static void push(Object renderState) {
        PlayerRigInstance rig = renderState instanceof PlayerRigRenderState state
                ? state.combatant$getPlayerRig()
                : null;
        // ArrayDeque cannot store null; a sentinel preserves nesting across non-player submits.
        STACK.get().push(rig != null ? rig : NON_PLAYER);
    }

    public static void pop() {
        Deque<Object> stack = STACK.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) STACK.remove();
    }

    public static PlayerRigInstance current() {
        Deque<Object> stack = STACK.get();
        if (stack.isEmpty()) return null;
        Object rig = stack.peek();
        return rig instanceof PlayerRigInstance instance ? instance : null;
    }
}
