/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.persist;

import combatant.client.util.logging.DebugLog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Coalescing write scheduler: each path holds at most one pending document; a request restarts the
 * quiet-period timer, and once it elapses the latest document is written on a single background thread.
 * {@link #flushAll()} writes everything pending synchronously (client stop, panic JAR replacement).
 */
public final class ConfigWriteScheduler {
    /** Performs the actual disk write; invoked while holding the scheduler's write lock. */
    @FunctionalInterface
    public interface Sink {
        void write(Path path, ConfigDocument document) throws IOException;
    }

    private final long debounceMs;
    private final Sink sink;
    private final ScheduledExecutorService executor;
    private final Object lock = new Object();
    private final Object writeLock = new Object();
    private final Map<Path, Pending> pending = new HashMap<>();

    private record Pending(ConfigDocument document, ScheduledFuture<?> timer) {
    }

    public ConfigWriteScheduler(long debounceMs, Sink sink) {
        this.debounceMs = Math.max(0L, debounceMs);
        this.sink = sink;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Combatant-ConfigSaver");
            t.setDaemon(true);
            return t;
        });
    }

    /** Sink that persists through {@link AtomicFileStore} with unknown-field merge and backup rotation. */
    public static Sink fileSink() {
        return (path, document) -> {
            byte[] existing = AtomicFileStore.readIfExists(path);
            AtomicFileStore.write(path, document.render(existing), document::isValid);
            DebugLog.config("Config saved: %s", path.toAbsolutePath());
        };
    }

    /** Marks {@code path} dirty with the newest content and restarts its quiet period. */
    public void schedule(Path path, ConfigDocument document) {
        synchronized (lock) {
            Pending previous = pending.get(path);
            if (previous != null) previous.timer().cancel(false);
            ScheduledFuture<?> timer = executor.schedule(() -> flushPath(path), debounceMs, TimeUnit.MILLISECONDS);
            pending.put(path, new Pending(document, timer));
        }
    }

    /** Writes immediately on the calling thread, superseding any pending write for the path. */
    public void writeNow(Path path, ConfigDocument document) {
        synchronized (writeLock) {
            synchronized (lock) {
                Pending previous = pending.remove(path);
                if (previous != null) previous.timer().cancel(false);
            }
            write(path, document);
        }
    }

    /** Synchronously writes every pending document. */
    public void flushAll() {
        synchronized (writeLock) {
            Map<Path, ConfigDocument> batch = new HashMap<>();
            synchronized (lock) {
                for (Map.Entry<Path, Pending> e : pending.entrySet()) {
                    e.getValue().timer().cancel(false);
                    batch.put(e.getKey(), e.getValue().document());
                }
                pending.clear();
            }
            for (Map.Entry<Path, ConfigDocument> e : batch.entrySet()) {
                write(e.getKey(), e.getValue());
            }
        }
    }

    /** Drops a pending write for {@code path} and waits out an in-flight write (e.g. before deleting the file). */
    public void cancel(Path path) {
        synchronized (writeLock) {
            synchronized (lock) {
                Pending previous = pending.remove(path);
                if (previous != null) previous.timer().cancel(false);
            }
        }
    }

    public int pendingCount() {
        synchronized (lock) {
            return pending.size();
        }
    }

    private void flushPath(Path path) {
        synchronized (writeLock) {
            ConfigDocument document;
            synchronized (lock) {
                // A timer that raced a reschedule may flush the newest document early; the newer timer then finds nothing.
                Pending p = pending.remove(path);
                if (p == null) return;
                document = p.document();
            }
            write(path, document);
        }
    }

    private void write(Path path, ConfigDocument document) {
        try {
            sink.write(path, document);
        } catch (IOException | RuntimeException e) {
            DebugLog.error("Failed to save config %s", e, path.toAbsolutePath());
        }
    }
}
