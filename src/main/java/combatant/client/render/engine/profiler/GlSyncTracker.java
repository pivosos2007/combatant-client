/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

import java.util.List;

/**
 * GL synchronization timing for Tracy. Only wait duration is recorded; fence
 * counts and other vanity counters are intentionally omitted.
 */
public enum GlSyncTracker {
    ;

    private static final ThreadLocal<ProfilerPhase.Scope> ACTIVE_WAIT = new ThreadLocal<>();

    public static void onFence(long sync) {
        // Fence creation itself is covered at CommandEncoder#createFence.
    }

    public static void onWaitStart(long sync) {
        if (!ProfilerPhase.isActive()) return;
        ProfilerPhase.Scope previous = ACTIVE_WAIT.get();
        if (previous != null) previous.close();
        ACTIVE_WAIT.set(ProfilerPhase.scope("gl:client_wait_sync"));
    }

    public static void onWaitEnd() {
        ProfilerPhase.Scope scope = ACTIVE_WAIT.get();
        if (scope == null) return;
        ACTIVE_WAIT.remove();
        scope.close();
    }

    public static void emitTracyFrame() {
        // Mojang owns Tracy frame boundaries.
    }

    public static List<String> drainLines() {
        return List.of();
    }

    public static void reset() {
        ProfilerPhase.Scope scope = ACTIVE_WAIT.get();
        ACTIVE_WAIT.remove();
        if (scope != null) scope.close();
    }
}
