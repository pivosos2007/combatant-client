/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.chat.actions;

import combatant.client.runtime.error.ErrorHandler;
import combatant.client.runtime.error.FailureBoundary;
import combatant.client.runtime.error.FailureExecution;
import combatant.client.util.logging.DebugLog;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Objects;

/**
 * Owns the independent failure boundary of each registered provider. A broken provider
 * is detached from execution without disabling the chat broker or unrelated providers.
 * No diagnostic presentation is performed here: observers of the state machine own that.
 */
final class MessageActionProviderIsolation {
    private static final List<RegistrationHandle> REGISTRATIONS = new ArrayList<>();
    private MessageActionProviderIsolation() { }
    private static final class RegistrationHandle implements MessageActionRegistry.Registration {
        private final MessageActionRegistry.Registration delegate;
        private final Object failureKey;
        private final AtomicBoolean closed;
        private RegistrationHandle(MessageActionRegistry.Registration delegate, Object failureKey, AtomicBoolean closed) {
            this.delegate = delegate; this.failureKey = failureKey; this.closed = closed;
        }
        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            delegate.close();
            ErrorHandler.unregister(failureKey);
            synchronized (REGISTRATIONS) { REGISTRATIONS.remove(this); }
        }
    }
    static void shutdown() {
        List<RegistrationHandle> registrations;
        synchronized (REGISTRATIONS) { registrations = List.copyOf(REGISTRATIONS); }
        for (RegistrationHandle registration : registrations) registration.close();
    }
    private static final class ProviderFailure extends RuntimeException {
        private ProviderFailure(Throwable cause) { super(cause); }
    }
    static MessageActionRegistry.Registration register(MessageActionRegistry registry, String owner,
                                                       MessageActionRegistry.Provider provider) {
        Objects.requireNonNull(provider, "provider");
        Object failureKey = new Object();
        AtomicBoolean closed = new AtomicBoolean();
        MessageActionRegistry.Provider guarded = new MessageActionRegistry.Provider() {
            @Override public List<MessageActionRegistry.Action> actions(MessageActionRegistry.Context context) {
                if (closed.get()) return List.of();
                return FailureExecution.call(failureKey, "Message action provider " + owner, "actions",
                        FailureBoundary.ISOLATE, () -> provider.actions(context), List.of());
            }
            @Override public void execute(MessageActionRegistry.Context context, String actionId) {
                if (closed.get() || ErrorHandler.blocked(failureKey)) throw new ProviderFailure(new IllegalStateException("Provider quarantined"));
                try { provider.execute(context, actionId); }
                catch (RuntimeException e) {
                    FailureExecution.reportComponent(failureKey, "Message action provider " + owner,
                            "execute", e, FailureBoundary.ISOLATE);
                    throw new ProviderFailure(e);
                }
            }
        };
        MessageActionRegistry.Registration registration = registry.register(owner, guarded);
        RegistrationHandle handle = new RegistrationHandle(registration, failureKey, closed);
        synchronized (REGISTRATIONS) { REGISTRATIONS.add(handle); }
        return handle;
    }
    static List<MessageActionRegistry.OfferedAction> actions(MessageActionRegistry registry, String token) {
        try { return registry.actions(token); }
        catch (ProviderFailure e) { return List.of(); }
        catch (RuntimeException e) {
            // Broker defects have no independently recoverable owner. Propagate them.
            DebugLog.error("Message action broker discovery failed", e);
            throw e;
        }
    }
    static MessageActionRegistry.Result invoke(MessageActionRegistry registry, String token, String qualifiedId) {
        try { return registry.invoke(token, qualifiedId); }
        catch (ProviderFailure e) { return MessageActionRegistry.Result.UNAVAILABLE; }
        catch (RuntimeException e) {
            DebugLog.error("Message action broker execution failed", e);
            throw e;
        }
    }
}
