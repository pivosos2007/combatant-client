/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer;

/**
 * Thread-local stack for screen-space UI warp while emitting 2D meshes.
 */
public enum RenderWarpStack {
    ;
    private static final int MAX_DEPTH = 16;
    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    public static RenderWarp current() {
        State state = STATE.get();
        return state.depth > 0 ? state.stack[state.depth - 1] : RenderWarp.IDENTITY;
    }

    public static boolean active() {
        return current().active();
    }

    public static Scope push(RenderWarp warp) {
        State state = STATE.get();
        if (state.depth >= MAX_DEPTH) {
            throw new IllegalStateException("Render warp stack overflow");
        }
        state.stack[state.depth++] = warp != null ? warp : RenderWarp.IDENTITY;
        return new Scope(state);
    }

    public static void pop() {
        State state = STATE.get();
        if (state.depth <= 0) {
            throw new IllegalStateException("Render warp stack underflow");
        }
        state.stack[--state.depth] = null;
    }

    private static final class State {
        private final RenderWarp[] stack = new RenderWarp[MAX_DEPTH];
        private int depth;
    }

    public static final class Scope implements AutoCloseable {
        private final State state;
        private boolean closed;

        private Scope(State state) {
            this.state = state;
        }

        @Override
        public void close() {
            if (closed) return;
            if (state.depth <= 0) {
                throw new IllegalStateException("Render warp scope closed after stack was emptied");
            }
            state.stack[--state.depth] = null;
            closed = true;
        }
    }
}
