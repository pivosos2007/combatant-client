/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.resource;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Budgeted retirement queue. Never turn flipFrame into an unbounded cleanup sweep.
 */
public final class FrameResourceRetirementQueue {
    private final Queue<AutoCloseable> queue = new ArrayDeque<>();
    private long queued;
    private long closed;

    public void retire(AutoCloseable resource) {
        if (resource == null) return;
        queue.add(resource);
        queued++;
    }

    public void drain(int budget) {
        while (budget-- > 0 && !queue.isEmpty()) {
            AutoCloseable closeable = queue.poll();
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
            closed++;
        }
    }

    public void drainAll() {
        drain(Integer.MAX_VALUE);
    }

    public long queued() {
        return queued;
    }

    public long closed() {
        return closed;
    }

    public int backlog() {
        return queue.size();
    }
}
