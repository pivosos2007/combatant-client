/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.aiming;

import combatant.client.features.module.Module;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Holds one pending request per provider, returning the highest-priority
 * nonexpired request from an enabled provider. Durations are measured in
 * calls to {@link #tick()}; inactive module providers are skipped on lookup.
 */
public final class RequestHandler<T> {

    private final PriorityBlockingQueue<Request<T>> activeRequests =
            new PriorityBlockingQueue<>(11, Comparator.comparingInt((Request<T> req) -> req.priority).reversed());
    private int currentTick = 0;

    private static boolean isProviderRunning(Object provider) {
        return !(provider instanceof Module module) || module.isEnabled();
    }

    public void tick() {
        currentTick++;
    }

    /**
     * Replaces the current request from the same provider identity.
     * The request's remaining duration is converted to an absolute expiry
     * against the current tick, so do not resubmit the same Request instance.
     */
    public void request(Request<T> request) {
        if (request == null) return;

        activeRequests.removeIf(existing -> existing.provider == request.provider);
        request.expiresIn += currentTick;
        activeRequests.add(request);
    }

    /**
     * Returns the highest-priority eligible request, removing expired or disabled
     * providers from the queue head. Returns null when none remains eligible.
     */
    public Request<T> getActiveRequest() {
        Request<T> top = activeRequests.peek();
        while (top != null && (top.expiresIn <= currentTick || !isProviderRunning(top.provider))) {
            activeRequests.poll();
            top = activeRequests.peek();
        }
        return top;
    }

    public T getActiveRequestValue() {
        Request<T> top = getActiveRequest();
        return top != null ? top.value : null;
    }

    public Object getActiveRequestProvider() {
        Request<T> top = getActiveRequest();
        return top != null ? top.provider : null;
    }

    public void clear() {
        activeRequests.clear();
        currentTick = 0;
    }

    public boolean clear(Object provider) {
        if (provider == null) {
            return false;
        }
        return activeRequests.removeIf(existing -> existing.provider == provider);
    }

    public static final class Request<T> {
        private final int priority;
        private final Object provider;
        private final T value;
        private int expiresIn;

        public Request(int expiresIn, int priority, Object provider, T value) {
            this.expiresIn = expiresIn;
            this.priority = priority;
            this.provider = provider;
            this.value = value;
        }

        public Object provider() {
            return provider;
        }

        public T value() {
            return value;
        }
    }
}
