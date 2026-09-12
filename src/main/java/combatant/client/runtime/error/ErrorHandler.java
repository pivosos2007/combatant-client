/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.runtime.error;

import combatant.client.runtime.error.FailureRegistry.Failure;
import combatant.client.runtime.error.FailureRegistry.Scope;
import combatant.client.runtime.error.FailureRegistry.State;
import java.util.List;
import java.util.Objects;

/**
 * Failure state machine and identity-keyed registry only.
 * No Minecraft, module lifecycle, renderer, chat, commands or presentation dependencies.
 * The caller must explicitly choose a boundary. ISOLATE is valid only when it owns the
 * component and can establish a valid state after failure. PROPAGATE never quarantines.
 */
public final class ErrorHandler {
    private static final FailureRegistry REGISTRY = new FailureRegistry();
    private static final Object LOCK = new Object();
    private static final ThreadLocal<Object> RECOVERING_OWNER = new ThreadLocal<>();
    private ErrorHandler() { }

    public static Failure failure(Object key) { return key == null ? null : REGISTRY.snapshot(key); }
    public static Failure byId(long id) { return REGISTRY.byId(id); }
    public static List<Failure> history() { return REGISTRY.history(); }
    public static boolean blocked(Object key) { return key != null && REGISTRY.blocked(key); }
    public static boolean isRecovering(Object key) { return RECOVERING_OWNER.get() == key; }
    public static boolean canRun(Object key) {
        Failure f = failure(key);
        return f == null || (f.state() == State.RECOVERING && isRecovering(key));
    }

    public static Failure report(Object key, Scope scope, String owner, String component,
                                 String phase, Throwable cause, FailureBoundary boundary) {
        return reportIncident(key, scope, owner, component, phase, cause, boundary).failure();
    }
    public static FailureRegistry.Report reportIncident(Object key, Scope scope, String owner, String component,
                                                        String phase, Throwable cause, FailureBoundary boundary) {
        Objects.requireNonNull(boundary, "boundary");
        FailureBoundary.requireRecoverable(cause);
        if (boundary == FailureBoundary.PROPAGATE) throw FailureBoundary.propagate(cause);
        FailureRegistry.Report result;
        synchronized (LOCK) {
            result = REGISTRY.reportDetailed(key, scope, owner, component, phase, FailureBoundary.unwrap(cause));
        }
        if (result.created()) FailureEvents.publish(new FailureEvents.Change(FailureEvents.Kind.REPORTED, result.failure(), FailureBoundary.unwrap(cause)));
        return result;
    }
    public static Failure quarantine(Object key) { return transition(key, State.FAILED, State.QUARANTINED); }
    public static Failure beginRecovery(Object key) { return transition(key, State.QUARANTINED, State.RECOVERING); }
    public static Failure recoveryFailed(Object key) { return transition(key, State.RECOVERING, State.QUARANTINED); }
    public static boolean recovered(Object key) {
        Failure previous;
        synchronized (LOCK) {
            previous = REGISTRY.snapshot(key);
            if (!REGISTRY.recovered(key)) return false;
        }
        FailureEvents.publish(new FailureEvents.Change(FailureEvents.Kind.UPDATED, previous.withState(State.HEALTHY)));
        return true;
    }
    private static Failure transition(Object key, State from, State to) {
        Failure result;
        synchronized (LOCK) {
            result = switch (to) {
                case QUARANTINED -> from == State.FAILED ? REGISTRY.quarantine(key) : REGISTRY.recoveryFailed(key);
                case RECOVERING -> REGISTRY.beginRecovery(key);
                default -> throw new IllegalArgumentException("Unsupported transition: " + from + " -> " + to);
            };
        }
        if (result != null) FailureEvents.publish(new FailureEvents.Change(FailureEvents.Kind.UPDATED, result));
        return result;
    }
    public static AutoCloseable recoveryScope(Object key) {
        Objects.requireNonNull(key, "key");
        Object previous = RECOVERING_OWNER.get();
        if (previous != null) throw new IllegalStateException("Nested recovery transaction");
        if (failure(key) == null || failure(key).state() != State.RECOVERING)
            throw new IllegalStateException("Recovery has not begun");
        RECOVERING_OWNER.set(key);
        return () -> RECOVERING_OWNER.remove();
    }
    /** Removal is reserved for an unregistered or explicitly reset, inactive owner. */
    public static Failure unregister(Object key) {
        if (key == null) return null;
        Failure previous;
        synchronized (LOCK) { previous = REGISTRY.forget(key); }
        if (previous != null) FailureEvents.publish(new FailureEvents.Change(FailureEvents.Kind.REMOVED, previous));
        return previous;
    }
    static void clearSession() { synchronized (LOCK) { REGISTRY.clear(); } RECOVERING_OWNER.remove(); }
}
