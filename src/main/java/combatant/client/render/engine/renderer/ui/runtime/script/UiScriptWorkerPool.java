/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.script;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A small set of persistent, single-threaded lanes for UI V8 isolates.
 *
 * <p>An isolate is always accessed from the same lane. Each runtime uses a
 * mailbox and therefore has at most one queued/in-flight task, rather than
 * accumulating one JS evaluation for every rendered frame.</p>
 */
final class UiScriptWorkerPool {
    private static final int LANES = Math.min(2, Math.max(1, Runtime.getRuntime().availableProcessors() / 4));
    private static final int QUEUE_CAPACITY = 128;
    private static final AtomicInteger NEXT_LANE = new AtomicInteger();
    private static final ThreadPoolExecutor[] EXECUTORS = createExecutors();
    private static final ArrayList<Set<UiScriptEngine>> OWNED_ENGINES = createEngineSets();

    private UiScriptWorkerPool() {
    }

    static int selectLane() {
        return Math.floorMod(NEXT_LANE.getAndIncrement(), LANES);
    }

    static boolean submit(int lane, Runnable task) {
        try {
            EXECUTORS[lane].execute(task);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // The render thread never waits for capacity. A subsequent frame retries.
            return false;
        }
    }

    // These sets are accessed only from their owning executor's thread.
    static void trackEngine(int lane, UiScriptEngine engine) {
        OWNED_ENGINES.get(lane).add(engine);
    }

    static void untrackEngine(int lane, UiScriptEngine engine) {
        OWNED_ENGINES.get(lane).remove(engine);
    }

    /**
     * Drain then close V8 isolates on their owning lanes before native unload.
     * Called exclusively at process shutdown, never during a render frame.
     */
    static boolean shutdown() {
        boolean scheduled = true;
        for (int lane = 0; lane < LANES; lane++) {
            int index = lane;
            boolean accepted = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            do {
                accepted = submit(lane, () -> {
                    for (UiScriptEngine engine : OWNED_ENGINES.get(index)) {
                        try { engine.close(); } catch (Throwable ignored) {}
                    }
                    OWNED_ENGINES.get(index).clear();
                });
                if (accepted || System.nanoTime() >= deadline) break;
                try {
                    Thread.sleep(1);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } while (true);
            scheduled &= accepted;
        }
        for (ThreadPoolExecutor executor : EXECUTORS) executor.shutdown();
        boolean drained = true;
        for (ThreadPoolExecutor executor : EXECUTORS) {
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) drained = false;
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                drained = false;
                break;
            }
        }
        return scheduled && drained;
    }

    private static ArrayList<Set<UiScriptEngine>> createEngineSets() {
        ArrayList<Set<UiScriptEngine>> result = new ArrayList<>(LANES);
        for (int i = 0; i < LANES; i++) result.add(new HashSet<>());
        return result;
    }

    private static ThreadPoolExecutor[] createExecutors() {
        ThreadPoolExecutor[] executors = new ThreadPoolExecutor[LANES];
        for (int i = 0; i < LANES; i++) {
            int index = i;
            executors[i] = new ThreadPoolExecutor(
                    1, 1, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                    runnable -> {
                        Thread thread = new Thread(runnable, "Combatant-UI-V8-" + index);
                        thread.setDaemon(true);
                        return thread;
                    },
                    new ThreadPoolExecutor.AbortPolicy()
            );
        }
        return executors;
    }
}
