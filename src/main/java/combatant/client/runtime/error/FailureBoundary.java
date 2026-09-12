/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.runtime.error;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

/** An explicit execution contract. Never infer recoverability from stack traces or mod names. */
public enum FailureBoundary {
    /** The caller owns an independent component and supplies its cleanup/recovery contract. */
    ISOLATE,
    /** Shared or unknown state: propagate to the caller/crash reporter; no recovery is claimed. */
    PROPAGATE;

    public static Throwable unwrap(Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        while (cause instanceof InvocationTargetException invocation && invocation.getCause() != null)
            cause = invocation.getCause();
        return cause;
    }

    /** Fatal VM errors and interruption are never converted into recoverable component failures. */
    public static void requireRecoverable(Throwable cause) {
        for (Throwable current = unwrap(cause); current != null; current = current.getCause()) {
            if (current instanceof Error error) throw error;
            if (current instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Client execution interrupted", current);
            }
        }
    }

    public static RuntimeException propagate(Throwable cause) {
        requireRecoverable(cause);
        Throwable actual = unwrap(cause);
        if (actual instanceof RuntimeException runtime) return runtime;
        return new IllegalStateException("Unisolated execution failed", actual);
    }
}
