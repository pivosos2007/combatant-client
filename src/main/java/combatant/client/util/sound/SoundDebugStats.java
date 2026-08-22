/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public enum SoundDebugStats {
    ;
    private static final AtomicInteger BUFFERS_CREATED = new AtomicInteger();
    private static final AtomicInteger BUFFERS_ALIVE = new AtomicInteger();
    private static final AtomicInteger SOURCES_CREATED = new AtomicInteger();
    private static final AtomicInteger SOURCES_ALIVE = new AtomicInteger();
    private static final AtomicInteger BUFFER_UPLOADS = new AtomicInteger();
    private static final AtomicLong BUFFER_BYTES = new AtomicLong();

    static void onBufferCreated(int bytes) {
        BUFFERS_CREATED.incrementAndGet();
        BUFFERS_ALIVE.incrementAndGet();
        BUFFER_UPLOADS.incrementAndGet();
        if (bytes > 0) BUFFER_BYTES.addAndGet(bytes);
    }

    static void onBuffersCleared(int count) {
        if (count > 0) BUFFERS_ALIVE.addAndGet(-count);
    }

    static void onSourceCreated() {
        SOURCES_CREATED.incrementAndGet();
        SOURCES_ALIVE.incrementAndGet();
    }

    static void onSourcesCleared(int count) {
        if (count > 0) SOURCES_ALIVE.addAndGet(-count);
    }

    public static int getBuffersCreated() { return BUFFERS_CREATED.get(); }
    public static int getBuffersAlive() { return BUFFERS_ALIVE.get(); }
    public static int getSourcesCreated() { return SOURCES_CREATED.get(); }
    public static int getSourcesAlive() { return SOURCES_ALIVE.get(); }
    public static int getBufferUploads() { return BUFFER_UPLOADS.get(); }
    public static long getBufferBytes() { return BUFFER_BYTES.get(); }
}
