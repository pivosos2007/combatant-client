/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.runtime.error;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Identity-keyed, thread-safe failure state machine. History contains no component references. */
public final class FailureRegistry {
    public enum State { HEALTHY, FAILED, QUARANTINED, RECOVERING }
    public enum Scope { MODULE, SETTING, COMPONENT }

    public record Failure(long id, Scope scope, String owner, String component, String phase,
                          State state, Instant occurredAt, String summary, String stackTrace,
                          int occurrences) {
        public boolean blocked() { return state != State.HEALTHY; }
        public Failure withState(State next) {
            return new Failure(id, scope, owner, component, phase, next, occurredAt, summary, stackTrace, occurrences);
        }
        public Failure repeated() {
            return new Failure(id, scope, owner, component, phase, state, occurredAt, summary, stackTrace,
                    occurrences == Integer.MAX_VALUE ? occurrences : occurrences + 1);
        }
    }

    public record Report(Failure failure, boolean created) { }

    private static final int MAX_HISTORY = 128;
    private static final int MAX_TRACE_CHARS = 262_144;
    private final IdentityHashMap<Object, Failure> active = new IdentityHashMap<>();
    private final LinkedHashMap<Long, Failure> history = new LinkedHashMap<>();
    private long nextId;

    public synchronized Failure report(Object key, Scope scope, String owner, String component,
                                       String phase, Throwable cause) {
        return reportDetailed(key, scope, owner, component, phase, cause).failure();
    }
    public synchronized Report reportDetailed(Object key, Scope scope, String owner, String component,
                                              String phase, Throwable cause) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(cause, "cause");
        Failure previous = active.get(key);
        if (previous != null && previous.state() != State.RECOVERING) {
            Failure repeated = previous.repeated();
            publish(key, repeated);
            return new Report(repeated, false);
        }
        if (previous != null) history.put(previous.id(), previous.withState(State.QUARANTINED));
        StringWriter buffer = new StringWriter();
        cause.printStackTrace(new PrintWriter(buffer));
        String trace = buffer.toString();
        if (trace.length() > MAX_TRACE_CHARS) {
            trace = trace.substring(0, MAX_TRACE_CHARS) + "\n[Trace exceeds chat limit; see the full exception in the game log.]";
        }
        String summary = cause.getClass().getSimpleName()
                + (cause.getMessage() == null || cause.getMessage().isBlank() ? "" : ": " + cause.getMessage());
        if (summary.length() > 512) summary = summary.substring(0, 512) + "…";
        Failure failure = new Failure(++nextId, scope, owner, component, phase, State.FAILED,
                Instant.now(), summary, trace, 1);
        publish(key, failure);
        return new Report(failure, true);
    }

    public synchronized Failure snapshot(Object key) { return active.get(key); }
    public synchronized Failure byId(long id) {
        Failure historical = history.get(id);
        if (historical != null) return historical;
        // A still-active incident must remain addressable even when recent history rolls over.
        for (Failure failure : active.values()) if (failure.id() == id) return failure;
        return null;
    }
    public synchronized List<Failure> history() { return List.copyOf(new ArrayList<>(history.values())); }
    public synchronized boolean blocked(Object key) {
        Failure failure = active.get(key);
        return failure != null && failure.blocked();
    }
    public synchronized boolean healthy(Object key) { return !active.containsKey(key); }
    public synchronized boolean current(Object key, long id) {
        Failure failure = active.get(key);
        return failure != null && failure.id() == id;
    }
    public synchronized Failure quarantine(Object key) { return transition(key, State.FAILED, State.QUARANTINED); }
    public synchronized Failure beginRecovery(Object key) { return transition(key, State.QUARANTINED, State.RECOVERING); }
    public synchronized Failure recoveryFailed(Object key) { return transition(key, State.RECOVERING, State.QUARANTINED); }
    public synchronized boolean recovered(Object key) {
        Failure failure = active.get(key);
        if (failure == null || failure.state() != State.RECOVERING) return false;
        history.put(failure.id(), failure.withState(State.HEALTHY));
        active.remove(key);
        return true;
    }
    public synchronized Failure forget(Object key) {
        Failure failure = active.remove(key);
        if (failure != null) history.put(failure.id(), failure);
        return failure;
    }
    public synchronized void clear() { active.clear(); history.clear(); }

    private Failure transition(Object key, State from, State to) {
        Failure failure = active.get(key);
        if (failure == null || failure.state() != from) return null;
        Failure next = failure.withState(to);
        publish(key, next);
        return next;
    }
    private void publish(Object key, Failure failure) {
        active.put(key, failure);
        history.put(failure.id(), failure);
        while (history.size() > MAX_HISTORY) history.remove(history.keySet().iterator().next());
    }
}
