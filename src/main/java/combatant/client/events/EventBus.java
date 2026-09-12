/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events;

import combatant.client.runtime.error.FailureBoundary;

import combatant.client.runtime.error.FailureIsolation;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import combatant.client.features.module.Module;
import combatant.client.runtime.RuntimeGate;
import combatant.client.runtime.error.ErrorHandler;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.util.logging.DebugLog;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class EventBus {
    private static final Subscriber[] EMPTY_SUBSCRIBERS = new Subscriber[0];
    private static final Comparator<Subscriber> PRIORITY =
            Comparator.comparingInt((Subscriber sub) -> sub.priority).reversed();

    private final Object lock = new Object();

    private final Reference2ObjectOpenHashMap<Class<?>, Subscriber[]> exactHandlers = new Reference2ObjectOpenHashMap<>();
    /*
     * Posting is overwhelmingly more frequent than registration. In particular collision
     * hooks can post hundreds of events per client tick. Keeping this cache behind the
     * registration lock made every already-resolved dispatch enter a monitor for no reason.
     * The exact handler table is still mutated under lock; published flattened arrays are
     * immutable snapshots and can therefore be read lock-free.
     */
    private final ConcurrentHashMap<Class<?>, Subscriber[]> dispatchCache = new ConcurrentHashMap<>();
    private final Reference2ObjectOpenHashMap<Object, Subscriber[]> ownerIndex = new Reference2ObjectOpenHashMap<>();

    private static Subscriber[] append(Subscriber[] old, Subscriber sub) {
        Subscriber[] next = Arrays.copyOf(old, old.length + 1);
        next[old.length] = sub;
        return next;
    }

    private static Subscriber[] remove(Subscriber[] old, Subscriber sub) {
        int index = -1;
        for (int i = 0; i < old.length; i++) {
            if (old[i] == sub) {
                index = i;
                break;
            }
        }

        if (index < 0) return old;
        if (old.length == 1) return EMPTY_SUBSCRIBERS;

        Subscriber[] next = new Subscriber[old.length - 1];
        System.arraycopy(old, 0, next, 0, index);
        System.arraycopy(old, index + 1, next, index, old.length - index - 1);
        return next;
    }

    private static void sortSubscribers(Subscriber[] subscribers) {
        Arrays.sort(subscribers, PRIORITY);
    }

    public void register(Object listener) {
        if (listener instanceof Module module) {
            registerOwned(module, listener);
        } else {
            registerOwned(null, listener);
        }
    }

    public void registerOwned(Module gateModule, Object listener) {
        if (listener == null) return;

        synchronized (lock) {
            if (ownerIndex.containsKey(listener)) return;

            Subscriber[] subs = scan(listener, gateModule);
            if (subs.length == 0) return;

            ownerIndex.put(listener, subs);
            for (Subscriber sub : subs) {
                Subscriber[] old = exactHandlers.get(sub.eventType);
                if (old == null) old = EMPTY_SUBSCRIBERS;
                Subscriber[] next = append(old, sub);
                sortSubscribers(next);
                exactHandlers.put(sub.eventType, next);
            }

            dispatchCache.clear();
        }
    }

    public void unregister(Object listener) {
        if (listener == null) return;

        synchronized (lock) {
            Subscriber[] subs = ownerIndex.remove(listener);
            if (subs == null || subs.length == 0) return;

            for (Subscriber sub : subs) {
                Subscriber[] old = exactHandlers.get(sub.eventType);
                if (old == null || old.length == 0) continue;

                Subscriber[] next = remove(old, sub);
                if (next.length == 0) {
                    exactHandlers.remove(sub.eventType);
                } else {
                    exactHandlers.put(sub.eventType, next);
                }
            }

            dispatchCache.clear();
        }
    }

    public boolean hasListeners(Class<? extends Event> eventType) {
        return subscribersFor(eventType).length != 0;
    }

    public <T extends Event> T post(T event) {
        if (event == null) return null;
        if (!RuntimeGate.canRunClientLogic()) return event;

        Subscriber[] subscribers = subscribersFor(event.getClass());
        if (!ProfilerPhase.isActive()) {
            dispatchUnprofiled(event, subscribers);
            return event;
        }

        try (ProfilerPhase.Scope eventScope = ProfilerPhase.scope("event:" + event.getClass().getSimpleName())) {
            for (Subscriber sub : subscribers) {
                Module gate = sub.gateModule;
                if (gate != null && !gate.isEnabled()) continue;

                try (ProfilerPhase.Scope handlerScope = ProfilerPhase.scope(sub.profileLabel)) {
                    sub.invoker.invoke(event);
                } catch (Throwable t) {
                    handleFailure(sub, t);
                }
            }
        }

        return event;
    }


    private static void dispatchUnprofiled(Event event, Subscriber[] subscribers) {
        for (Subscriber sub : subscribers) {
            Module gate = sub.gateModule;
            if (gate != null && !gate.isEnabled()) continue;

            try {
                sub.invoker.invoke(event);
            } catch (Throwable t) {
                handleFailure(sub, t);
            }
        }
    }

    private static void handleFailure(Subscriber sub, Throwable cause) {
        FailureBoundary.requireRecoverable(cause);
        if (sub.gateModule != null) {
            FailureIsolation.reportModule(sub.gateModule, "event " + sub.describe(), cause);
        } else {
            // Non-module listeners may not have reversible state. Do not quarantine
            // an entire shared service or claim that its state has been recovered.
            DebugLog.error("Event handler failed: %s", cause, sub.describe());
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Unisolated event handler failed: " + sub.describe(), cause);
        }
    }

    public void clear() {
        synchronized (lock) {
            exactHandlers.clear();
            dispatchCache.clear();
            ownerIndex.clear();
        }
    }

    public int listenerCount() {
        synchronized (lock) {
            return ownerIndex.size();
        }
    }

    private Subscriber[] scan(Object listener, Module gateModule) {
        List<Subscriber> out = new ArrayList<>();
        Class<?> cls = listener.getClass();

        for (Method method : cls.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(EventHandler.class)) continue;

            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1) continue;
            if (!Event.class.isAssignableFrom(params[0])) continue;

            EventHandler meta = method.getAnnotation(EventHandler.class);
            method.setAccessible(true);

            @SuppressWarnings("unchecked")
            Class<? extends Event> eventType = (Class<? extends Event>) params[0];

            EventInvoker invoker = createInvoker(listener, method);
            out.add(new Subscriber(listener, gateModule, method, invoker, eventType, meta.priority()));
        }

        return out.toArray(Subscriber[]::new);
    }

    private EventInvoker createInvoker(Object listener, Method method) {
        method.setAccessible(true);
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle handle = lookup.unreflect(method).bindTo(listener);
            return handle::invoke;
        } catch (Throwable t) {
            DebugLog.error("Failed to create MethodHandle event invoker, falling back to reflection: %s", t,
                    listener.getClass().getName() + "#" + method.getName());
            return event -> method.invoke(listener, event);
        }
    }

    private Subscriber[] subscribersFor(Class<?> eventType) {
        if (eventType == null) return EMPTY_SUBSCRIBERS;

        Subscriber[] cached = dispatchCache.get(eventType);
        if (cached != null) return cached;

        synchronized (lock) {
            cached = dispatchCache.get(eventType);
            if (cached != null) return cached;

            Subscriber[] built = buildFlattenedSubscribers(eventType);
            dispatchCache.put(eventType, built);
            return built;
        }
    }

    private Subscriber[] buildFlattenedSubscribers(Class<?> eventType) {
        int total = 0;

        Class<?> type = eventType;
        while (type != null && Event.class.isAssignableFrom(type)) {
            Subscriber[] exact = exactHandlers.get(type);
            if (exact != null) total += exact.length;
            type = type.getSuperclass();
        }

        if (total == 0) return EMPTY_SUBSCRIBERS;

        Subscriber[] out = new Subscriber[total];
        int offset = 0;

        type = eventType;
        while (type != null && Event.class.isAssignableFrom(type)) {
            Subscriber[] exact = exactHandlers.get(type);
            if (exact != null && exact.length > 0) {
                System.arraycopy(exact, 0, out, offset, exact.length);
                offset += exact.length;
            }
            type = type.getSuperclass();
        }

        return out;
    }

    @FunctionalInterface
    private interface EventInvoker {
        void invoke(Event event) throws Throwable;
    }

    private static final class Subscriber {
        final Object owner;
        final Module gateModule;
        final Method method;
        final EventInvoker invoker;
        final Class<? extends Event> eventType;
        final int priority;
        final String description;
        final String profileLabel;

        Subscriber(Object owner,
                   Module gateModule,
                   Method method,
                   EventInvoker invoker,
                   Class<? extends Event> eventType,
                   int priority) {
            this.owner = owner;
            this.gateModule = gateModule;
            this.method = method;
            this.invoker = invoker;
            this.eventType = eventType;
            this.priority = priority;
            this.description = owner.getClass().getName() + "#" + method.getName();
            this.profileLabel = "handler:" + owner.getClass().getSimpleName() + "#" + method.getName();
        }

        String describe() {
            return description;
        }
    }
}
