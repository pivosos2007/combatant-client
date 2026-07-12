/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.wav;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public enum WavDebugStats {
    ;
    private static final AtomicInteger BUFFERS_CREATED = new AtomicInteger();
    private static final AtomicInteger BUFFERS_ALIVE = new AtomicInteger();
    private static final AtomicInteger SOURCES_CREATED = new AtomicInteger();
    private static final AtomicInteger SOURCES_ALIVE = new AtomicInteger();
    private static final AtomicInteger BUFFER_UPLOADS = new AtomicInteger();
    private static final AtomicLong BUFFER_BYTES = new AtomicLong();

    public static void onBufferCreated(int bytes) {
        BUFFERS_CREATED.incrementAndGet();
        BUFFERS_ALIVE.incrementAndGet();
        BUFFER_UPLOADS.incrementAndGet();
        if (bytes > 0) BUFFER_BYTES.addAndGet(bytes);
    }

    public static void onBuffersCleared(int count) {
        if (count <= 0) return;
        BUFFERS_ALIVE.addAndGet(-count);
    }

    public static void onSourceCreated() {
        SOURCES_CREATED.incrementAndGet();
        SOURCES_ALIVE.incrementAndGet();
    }

    public static void onSourcesCleared(int count) {
        if (count <= 0) return;
        SOURCES_ALIVE.addAndGet(-count);
    }

    public static int getBuffersCreated() {
        return BUFFERS_CREATED.get();
    }

    public static int getBuffersAlive() {
        return BUFFERS_ALIVE.get();
    }

    public static int getSourcesCreated() {
        return SOURCES_CREATED.get();
    }

    public static int getSourcesAlive() {
        return SOURCES_ALIVE.get();
    }

    public static int getBufferUploads() {
        return BUFFER_UPLOADS.get();
    }

    public static long getBufferBytes() {
        return BUFFER_BYTES.get();
    }
}
