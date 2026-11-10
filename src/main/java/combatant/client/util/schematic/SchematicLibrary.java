/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.fabricmc.loader.api.FabricLoader;
import combatant.client.util.logging.DebugLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Maintains the selected schematic directory and a cached snapshot of parsed headers.
 * File operations and rescans share a single background worker; readers receive the
 * last published header list while a scan is running.
 */
public final class SchematicLibrary {

    public static final SchematicLibrary INSTANCE = new SchematicLibrary();

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Combatant-Schematic");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final Map<Path, SchematicHeader> headerCache = new ConcurrentHashMap<>();
    private volatile List<SchematicHeader> headers = List.of();
    private volatile boolean scanning;
    private final AtomicInteger scanGeneration = new AtomicInteger();
    private CompletableFuture<Void> pendingScan;

    private static final class Storage {
        private static final SchematicStorage INSTANCE = new SchematicStorage(FabricLoader.getInstance().getGameDir());
    }

    private SchematicLibrary() {
    }

    public Path directory() {
        try {
            return Storage.INSTANCE.configuredDirectory();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private Path readableDirectory() {
        try {
            return Storage.INSTANCE.directory();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public CompletableFuture<Void> changeDirectory(Path directory) {
        return updateDirectory(() -> Storage.INSTANCE.select(directory));
    }

    public CompletableFuture<Void> restoreDefaultDirectory() {
        return updateDirectory(() -> Storage.INSTANCE.restoreDefault());
    }

    private CompletableFuture<Void> updateDirectory(IoOperation operation) {
        return CompletableFuture.runAsync(() -> {
            try {
                operation.run();
                headerCache.clear();
                headers = List.of();
                scan();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, worker);
    }

    /**
     * Imports and validates a schematic on the library worker, then schedules
     * a rescan. I/O failures complete the returned future exceptionally.
     */
    public CompletableFuture<Path> importFile(Path source) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path imported = Storage.INSTANCE.importFile(source, path -> SchematicFormats.read(path));
                scan();
                return imported;
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, worker);
    }

    @FunctionalInterface private interface IoOperation {
        void run() throws IOException;
    }

    /**
     * Returns the most recently published header snapshot. A scan in progress
     * does not modify the previously returned list.
     */
    public List<SchematicHeader> headers() {
        return headers;
    }

    public boolean isScanning() {
        return scanning;
    }

    /** Bumped after every finished scan so views know to re-filter. */
    public int scanGeneration() {
        return scanGeneration.get();
    }

    public synchronized CompletableFuture<Void> refresh() {
        if (pendingScan != null && !pendingScan.isDone()) return pendingScan;
        scanning = true;
        pendingScan = CompletableFuture.runAsync(this::scan, worker).whenComplete((ignored, error) -> {
            scanning = false;
            if (error != null) {
                headerCache.clear();
                headers = List.of();
                scanGeneration.incrementAndGet();
                DebugLog.error("Schematic scan failed", error);
            }
        });
        return pendingScan;
    }

    public CompletableFuture<Loaded> load(Path path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Schematic schematic = SchematicFormats.read(path);
                return new Loaded(schematic, LayerStats.compute(schematic));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, worker);
    }

    /** Finds a file by name with or without the extension; falls back to a prefix match. */
    public Path resolve(String name) {
        try {
            return resolveReadable(name);
        } catch (java.io.UncheckedIOException e) {
            DebugLog.error("Cannot resolve schematic in the selected library", e);
            return null;
        }
    }

    private Path resolveReadable(String name) {
        if (name == null || name.isBlank()) return null;
        String wanted = name.trim();
        Path dir = readableDirectory();
        String lower = wanted.toLowerCase(Locale.ROOT);
        for (String extension : SchematicFormats.EXTENSIONS) {
            if (lower.endsWith(extension)) {
                lower = lower.substring(0, lower.length() - extension.length());
                break;
            }
        }
        try {
            Path exact = dir.resolve(wanted);
            if (SchematicFormats.isSchematic(exact) && Files.isRegularFile(exact) && exact.normalize().startsWith(dir.normalize())) {
                return exact;
            }
            for (String extension : SchematicFormats.EXTENSIONS) {
                Path direct = dir.resolve(wanted + extension);
                if (Files.isRegularFile(direct) && direct.normalize().startsWith(dir.normalize())) return direct;
            }
        } catch (java.nio.file.InvalidPathException ignored) {
            // Fall through to the prefix match; chat input can hold characters the file system rejects.
        }

        for (Path candidate : listFiles(dir)) {
            if (SchematicFormats.baseName(candidate).toLowerCase(Locale.ROOT).startsWith(lower)) return candidate;
        }
        return null;
    }

    public List<String> fileNames() {
        try {
            List<String> names = new ArrayList<>();
            for (Path path : listFiles(readableDirectory())) names.add(SchematicFormats.baseName(path));
            return names;
        } catch (java.io.UncheckedIOException e) {
            DebugLog.error("Cannot list schematic names in the selected library", e);
            return List.of();
        }
    }

    private void scan() {
        Path dir = readableDirectory();
        List<Path> files = listFiles(dir);
        List<SchematicHeader> next = new ArrayList<>(files.size());
        for (Path path : files) {
            next.add(header(path));
        }
        headerCache.keySet().retainAll(files);
        next.sort(Comparator.comparing(h -> h.fileName().toLowerCase(Locale.ROOT)));
        headers = List.copyOf(next);
        scanGeneration.incrementAndGet();
    }

    private SchematicHeader header(Path path) {
        long modified;
        try {
            modified = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            modified = 0L;
        }
        SchematicHeader cached = headerCache.get(path);
        if (cached != null && cached.lastModified() == modified) return cached;
        SchematicHeader header;
        try {
            header = SchematicFormats.readHeader(path, modified);
        } catch (IOException | RuntimeException e) {
            header = SchematicHeader.failed(path, modified, e.getMessage());
        }
        headerCache.put(path, header);
        return header;
    }

    private static List<Path> listFiles(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(SchematicFormats::isSchematic)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Cannot scan schematic directory: " + dir, e);
        }
    }

    public record Loaded(Schematic schematic, LayerStats stats) {
    }
}
