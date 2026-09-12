/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.runtime.error;

import combatant.client.runtime.error.FailureRegistry.Failure;
import combatant.client.runtime.error.FailureRegistry.Scope;
import java.util.Objects;
import java.util.function.Supplier;

/** Execution adapters for explicitly owned, independently isolatable components. */
public final class FailureExecution {
    private FailureExecution() { }
    public static Failure reportComponent(Object key, String owner, String phase, Throwable cause,
                                          FailureBoundary boundary) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(boundary, "boundary");
        Failure result = ErrorHandler.report(key, Scope.COMPONENT, owner, owner, phase, cause, boundary);
        ErrorHandler.quarantine(key);
        return result;
    }
    public static boolean run(Object key, String owner, String phase, FailureBoundary boundary, Runnable action) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(boundary, "boundary");
        if (boundary == FailureBoundary.PROPAGATE) { action.run(); return true; }
        if (!ErrorHandler.canRun(key)) return false;
        try { action.run(); return true; }
        catch (RuntimeException e) { reportComponent(key, owner, phase, e, boundary); return false; }
    }
    public static <T> T call(Object key, String owner, String phase, FailureBoundary boundary,
                             Supplier<T> action, T fallback) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(boundary, "boundary");
        if (boundary == FailureBoundary.PROPAGATE) return action.get();
        if (!ErrorHandler.canRun(key)) return fallback;
        try { return action.get(); }
        catch (RuntimeException e) { reportComponent(key, owner, phase, e, boundary); return fallback; }
    }
}
