/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.script;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class UiScriptWorkerPoolTest {
    @Test
    void interactiveWorkDoesNotWaitForBackgroundLanes() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(UiScriptWorkerPool.interactiveLane());
        try {
            for (int lane = 0; lane < UiScriptWorkerPool.interactiveLane(); lane++) {
                assertTrue(UiScriptWorkerPool.submit(lane, () -> {
                    started.countDown();
                    try { release.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                }));
            }
            assertTrue(started.await(2, TimeUnit.SECONDS));
            CountDownLatch interactive = new CountDownLatch(1);
            assertTrue(UiScriptWorkerPool.submit(UiScriptWorkerPool.interactiveLane(), interactive::countDown));
            assertTrue(interactive.await(1, TimeUnit.SECONDS));
            assertEquals(1, release.getCount());
        } finally {
            release.countDown();
        }
    }

    @Test
    void serializesWorkOnTheSamePersistentLane() throws Exception {
        int lane = UiScriptWorkerPool.selectLane();
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Thread> first = new AtomicReference<>();
        AtomicReference<Thread> second = new AtomicReference<>();
        assertTrue(UiScriptWorkerPool.submit(lane, () -> {
            first.set(Thread.currentThread());
            done.countDown();
        }));
        assertTrue(UiScriptWorkerPool.submit(lane, () -> {
            second.set(Thread.currentThread());
            done.countDown();
        }));
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertSame(first.get(), second.get());
        assertTrue(first.get().isDaemon());
    }
}
