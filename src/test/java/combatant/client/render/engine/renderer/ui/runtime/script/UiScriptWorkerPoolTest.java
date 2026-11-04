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
