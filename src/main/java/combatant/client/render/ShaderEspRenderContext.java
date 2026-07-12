/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render;

public enum ShaderEspRenderContext {
    ;

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    public static Scope enter() {
        DEPTH.set(DEPTH.get() + 1);
        return new Scope();
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }

    public static final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() {
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            int next = Math.max(0, DEPTH.get() - 1);
            if (next == 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(next);
            }
        }
    }
}
