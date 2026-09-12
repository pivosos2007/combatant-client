/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.runtime.error;

import combatant.client.runtime.error.FailureRegistry.Failure;
import combatant.client.util.logging.DebugLog;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Independent observer bus. A broken presenter is detached, never allowed to corrupt the registry. */
public final class FailureEvents {
    public enum Kind { REPORTED, UPDATED, REMOVED }
    public record Change(Kind kind, Failure failure, Throwable cause) {
        public Change(Kind kind, Failure failure) { this(kind, failure, null); }
    }
    private static final CopyOnWriteArrayList<Consumer<Change>> LISTENERS = new CopyOnWriteArrayList<>();
    private FailureEvents() { }
    public static AutoCloseable subscribe(Consumer<Change> listener) {
        Objects.requireNonNull(listener, "listener");
        LISTENERS.add(listener);
        return () -> LISTENERS.remove(listener);
    }
    static void publish(Change change) {
        for (Consumer<Change> listener : LISTENERS) {
            try { listener.accept(change); }
            catch (RuntimeException e) {
                LISTENERS.remove(listener);
                DebugLog.error("Failure observer detached after exception", e);
            }
        }
    }
}
