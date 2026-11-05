/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.filedialog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NativeFileDialogServiceTest {
    @TempDir Path temporary;

    @Test void overlappingRequestDoesNotReleaseTheFirstRequestsBusyState() throws Exception {
        BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        var first = NativeFileDialogService.pickFolder(temporary.resolve("missing"), callbacks::add);
        Runnable firstCompletion = callbacks.poll(10, TimeUnit.SECONDS);
        assertNotNull(firstCompletion);
        assertTrue(NativeFileDialogService.busy());
        try {
            var second = NativeFileDialogService.pickFolder(temporary.resolve("also-missing"), Runnable::run);
            assertEquals(NativeFileDialogService.Status.BUSY, second.get(10, TimeUnit.SECONDS).status());
            assertTrue(NativeFileDialogService.busy());
            first.cancel(false);
            assertTrue(NativeFileDialogService.busy());
        } finally {
            firstCompletion.run();
        }
        assertFalse(NativeFileDialogService.busy());
    }

    @Test void rejectedClientDispatchDoesNotLeaveServiceBusy() {
        var future = NativeFileDialogService.pickFolder(temporary.resolve("missing"), action -> {
            throw new RejectedExecutionException("client stopped");
        });
        ExecutionException error = assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
        assertInstanceOf(RejectedExecutionException.class, error.getCause());
        assertFalse(NativeFileDialogService.busy());
    }

    @Test void invalidInitialDirectoryDispatchesAndReleasesBusyWithoutOpeningDialog() throws Exception {
        try (ExecutorService client = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-client"))) {
            AtomicReference<String> completionThread = new AtomicReference<>();
            Executor dispatch = action -> client.execute(() -> {
                completionThread.set(Thread.currentThread().getName());
                action.run();
            });
            var result = NativeFileDialogService.pickFolder(temporary.resolve("missing"), dispatch).get(10, TimeUnit.SECONDS);
            assertEquals(NativeFileDialogService.Status.INVALID_RESULT, result.status());
            assertNull(result.path());
            assertEquals("test-client", completionThread.get());
            assertFalse(NativeFileDialogService.busy());
        }
    }
}
