/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.sound;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Explicit ownership of Minecraft's audio executor and its Library lifecycle.
 * A missing context is never repaired by creating capabilities on another thread.
 * Tasks from an obsolete Library generation are discarded before execution.
 */
final class SoundThreadBridge {
    private volatile Executor executor;
    private volatile Thread audioThread;
    private volatile boolean ready;
    private final AtomicLong generation = new AtomicLong();

    void attach(Executor executor) {
        Objects.requireNonNull(executor, "executor");
        if (ready) throw new IllegalStateException("Cannot replace a live audio executor");
        this.executor = executor;
        generation.incrementAndGet();
    }

    void opened() {
        if (executor == null) throw new IllegalStateException("Minecraft audio executor is not attached");
        audioThread = Thread.currentThread();
        generation.incrementAndGet();
        ready = true;
    }

    void close(Runnable cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");
        if (audioThread != Thread.currentThread()) {
            throw new IllegalStateException("Audio Library cleanup must run on its owning thread");
        }
        ready = false;
        generation.incrementAndGet();
        try { cleanup.run(); }
        finally { audioThread = null; }
    }

    boolean isReady() { return ready && executor != null && audioThread != null; }
    boolean isAudioThread() { return Thread.currentThread() == audioThread; }

    <T> T call(Supplier<T> action, T fallback) {
        Objects.requireNonNull(action, "action");
        if (!isReady()) return fallback;
        long expected = generation.get();
        if (isAudioThread()) return valid(expected) ? action.get() : fallback;
        Executor target = executor;
        FutureTask<T> task = new FutureTask<>(() -> valid(expected) ? action.get() : fallback);
        target.execute(task);
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) throw error;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Audio task failed", cause);
        }
    }

    void execute(Runnable action, Consumer<RuntimeException> onError) {
        Objects.requireNonNull(action, "action");
        if (!isReady()) return;
        long expected = generation.get();
        Runnable guarded = () -> {
            if (!valid(expected)) return;
            try { action.run(); }
            catch (RuntimeException e) { onError.accept(e); }
        };
        if (isAudioThread()) guarded.run();
        else executor.execute(guarded);
    }

    private boolean valid(long expected) {
        return isReady() && generation.get() == expected && isAudioThread();
    }
}
