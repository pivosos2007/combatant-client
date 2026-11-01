package combatant.client.util.filedialog;

import org.lwjgl.sdl.*;
import org.lwjgl.system.MemoryUtil;
import combatant.client.util.logging.DebugLog;

import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class NativeFileDialogService {
    private static final AtomicReference<Request> PENDING = new AtomicReference<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Combatant-FileDialogIO");
        thread.setDaemon(true);
        return thread;
    });

    private NativeFileDialogService() { }

    public enum Status { SELECTED, CANCELLED, NATIVE_ERROR, INVALID_RESULT, BUSY }
    private enum Operation { OPEN, SAVE, FOLDER }

    public record Result(Status status, Path path, String detail) {
        public Result {
            Objects.requireNonNull(status);
            if ((status == Status.SELECTED) != (path != null)) throw new IllegalArgumentException("Inconsistent dialog result");
            if (path != null) path = path.toAbsolutePath().normalize();
            detail = detail == null ? "" : detail;
        }
    }

    public record Filter(String name, List<String> extensions) {
        public Filter {
            if (name == null || name.isBlank() || name.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid filter label");
            extensions = List.copyOf(extensions).stream().map(extension -> {
                String normalized = extension.toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
                if (!normalized.matches("[a-z0-9]+")) throw new IllegalArgumentException("Invalid extension: " + extension);
                return normalized;
            }).distinct().toList();
            if (extensions.isEmpty()) throw new IllegalArgumentException("Empty extension filter");
        }
    }

    public static boolean busy() { return PENDING.get() != null; }

    /** Supply Minecraft.getInstance()::schedule when dispatching results into the client. */
    public static CompletableFuture<Result> pickFolder(Path initial, Executor clientExecutor) {
        return submit(Operation.FOLDER, initial, null, List.of(), clientExecutor);
    }

    public static CompletableFuture<Result> openFile(Path initial, List<Filter> filters, Executor clientExecutor) {
        return submit(Operation.OPEN, initial, null, filters, clientExecutor);
    }

    public static CompletableFuture<Result> saveFile(Path initial, String filename, List<Filter> filters, Executor clientExecutor) {
        return submit(Operation.SAVE, initial, filename, filters, clientExecutor);
    }

    private static CompletableFuture<Result> submit(Operation operation, Path initial, String filename,
                                                     List<Filter> filters, Executor clientExecutor) {
        Request request = new Request(operation, initial, filename, List.copyOf(filters), Objects.requireNonNull(clientExecutor));
        if (!PENDING.compareAndSet(null, request)) {
            request.finish(new Result(Status.BUSY, null, "Another native file dialog is open"));
            return request.future;
        }
        IO.execute(() -> {
            try {
                if (initial != null && (!Files.isDirectory(initial) || !Files.isReadable(initial))) {
                    request.finish(new Result(Status.INVALID_RESULT, null, "Initial directory is inaccessible: " + initial));
                } else if (filename != null && (filename.isBlank() || filename.contains("/") || filename.contains("\\")
                        || filename.indexOf('\0') >= 0 || filename.equals(".") || filename.equals(".."))) {
                    request.finish(new Result(Status.INVALID_RESULT, null, "Invalid default filename"));
                } else {
                    clientExecutor.execute(() -> launch(request));
                }
            } catch (RuntimeException e) {
                request.finish(new Result(Status.NATIVE_ERROR, null, e.toString()));
            }
        });
        return request.future;
    }

    private static void launch(Request request) {
        try {
            if (SDLInit.SDL_WasInit(SDLInit.SDL_INIT_VIDEO) == 0) {
                request.finish(new Result(Status.NATIVE_ERROR, null, "Minecraft's SDL video subsystem is not initialized"));
            } else if (SDLInit.SDL_IsMainThread()) {
                show(request);
            } else if (!SDLInit.SDL_RunOnMainThread(Callbacks.LAUNCH, request.token, false)) {
                request.finish(nativeError());
            }
        } catch (RuntimeException | LinkageError e) {
            request.release();
            DebugLog.error("Native file dialog unavailable", e);
            request.finish(new Result(Status.NATIVE_ERROR, null, "SDL file dialog unavailable: " + e.getMessage()));
        }
    }

    private static void show(Request request) {
        synchronized (request) {
            if (!request.filters.isEmpty()) {
                request.nativeFilters = SDL_DialogFileFilter.calloc(request.filters.size());
                for (int i = 0; i < request.filters.size(); i++) {
                    Filter filter = request.filters.get(i);
                    ByteBuffer name = MemoryUtil.memUTF8(filter.name());
                    request.strings.add(name);
                    ByteBuffer pattern = MemoryUtil.memUTF8(String.join(";", filter.extensions()));
                    request.strings.add(pattern);
                    request.nativeFilters.get(i).name(name).pattern(pattern);
                }
            }
        }
        String location = request.initial == null ? null : request.initial.toAbsolutePath().normalize().toString();
        if (request.operation == Operation.SAVE && request.filename != null) {
            location = request.initial == null ? request.filename : request.initial.resolve(request.filename).toAbsolutePath().normalize().toString();
        }
        switch (request.operation) {
            case OPEN -> SDLDialog.SDL_ShowOpenFileDialog(Callbacks.DIALOG, request.token, 0L, request.nativeFilters, location, false);
            case SAVE -> SDLDialog.SDL_ShowSaveFileDialog(Callbacks.DIALOG, request.token, 0L, request.nativeFilters, location);
            case FOLDER -> SDLDialog.SDL_ShowOpenFolderDialog(Callbacks.DIALOG, request.token, 0L, location, false);
        }
    }

    private static final class Callbacks {
        // Process-lifetime trampolines cannot be freed while an SDL worker is returning through them.
        private static final SDL_MainThreadCallback LAUNCH = SDL_MainThreadCallback.create(token -> {
            Request request = PENDING.get();
            if (request != null && request.token == token) launch(request);
        });
        private static final SDL_DialogFileCallback DIALOG = SDL_DialogFileCallback.create((token, files, filter) -> {
            Request request = PENDING.get();
            if (request == null || request.token != token) return;
            try {
                if (files == 0L) {
                    request.finish(nativeError());
                } else {
                    long first = MemoryUtil.memGetAddress(files);
                    if (first == 0L) {
                        request.finish(new Result(Status.CANCELLED, null, ""));
                    } else {
                        // SDL owns the file list and frees it as soon as this callback returns.
                        String selected = MemoryUtil.memUTF8(first);
                        IO.execute(() -> request.finish(validateSelection(selected, request.operation)));
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                DebugLog.error("Native file dialog callback failed", e);
                request.finish(new Result(Status.NATIVE_ERROR, null, e.toString()));
            } finally {
                request.release();
            }
        });
    }

    private static Result nativeError() {
        String detail = SDLError.SDL_GetError();
        DebugLog.error("SDL file dialog failed: {}", detail);
        return new Result(Status.NATIVE_ERROR, null, detail);
    }

    private static Result validateSelection(String raw, Operation operation) {
        try {
            if (raw == null || raw.isBlank()) return new Result(Status.INVALID_RESULT, null, "Empty selected path");
            Path path = Path.of(raw).toAbsolutePath().normalize();
            boolean valid = switch (operation) {
                case FOLDER -> Files.isDirectory(path) && Files.isReadable(path);
                case OPEN -> Files.isRegularFile(path) && Files.isReadable(path);
                case SAVE -> path.getParent() != null && Files.isDirectory(path.getParent()) && Files.isWritable(path.getParent())
                        && (!Files.exists(path) || (Files.isRegularFile(path) && Files.isWritable(path)));
            };
            return valid ? new Result(Status.SELECTED, path, "")
                    : new Result(Status.INVALID_RESULT, null, "Selected path is inaccessible: " + path);
        } catch (InvalidPathException | SecurityException e) {
            return new Result(Status.INVALID_RESULT, null, e.getMessage());
        }
    }

    private static final class Request {
        final long token = SEQUENCE.incrementAndGet();
        final Operation operation;
        final Path initial;
        final String filename;
        final List<Filter> filters;
        final Executor clientExecutor;
        final CompletableFuture<Result> future = new CompletableFuture<>();
        final AtomicBoolean completed = new AtomicBoolean();
        final List<ByteBuffer> strings = new ArrayList<>();
        SDL_DialogFileFilter.Buffer nativeFilters;

        Request(Operation operation, Path initial, String filename, List<Filter> filters, Executor clientExecutor) {
            this.operation = operation;
            this.initial = initial;
            this.filename = filename;
            this.filters = filters;
            this.clientExecutor = clientExecutor;
        }

        void finish(Result result) {
            if (!completed.compareAndSet(false, true)) return;
            try {
                clientExecutor.execute(() -> {
                    PENDING.compareAndSet(this, null);
                    future.complete(result);
                });
            } catch (RuntimeException e) {
                PENDING.compareAndSet(this, null);
                future.completeExceptionally(e);
            }
        }

        synchronized void release() {
            if (nativeFilters != null) {
                nativeFilters.free();
                nativeFilters = null;
            }
            for (ByteBuffer string : strings) MemoryUtil.memFree(string);
            strings.clear();
        }
    }
}
