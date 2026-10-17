/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
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
import java.util.stream.Stream;

/**
 * The {@code schematics/} folder in the game directory. Scanning and loading run on separate daemon workers so a
 * 50 MB schematic never stalls the render thread and a load never queues behind a folder scan; headers are cached
 * by path and modification time.
 */
public final class SchematicLibrary {

    public static final SchematicLibrary INSTANCE = new SchematicLibrary();
    private static final Logger LOGGER = LoggerFactory.getLogger("Combatant/Schematic");

    /** Folder scans: background work the user is not waiting on. */
    private final ExecutorService scanner = daemon("Combatant-Schematic-Scan", Thread.NORM_PRIORITY - 1);
    /** Loads and name lookups: the user just asked for these, so they get their own thread. */
    private final ExecutorService loader = daemon("Combatant-Schematic-Load", Thread.NORM_PRIORITY);
    private final Map<Path, SchematicHeader> headerCache = new ConcurrentHashMap<>();
    private volatile List<SchematicHeader> headers = List.of();
    private volatile boolean scanning;
    private volatile int scanGeneration;
    private CompletableFuture<Void> pendingScan;

    private SchematicLibrary() {
    }

    private static ExecutorService daemon(String name, int priority) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            thread.setPriority(priority);
            return thread;
        });
    }

    public Path directory() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("schematics");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.warn("Cannot create {}", dir, e);
        }
        return dir;
    }

    public List<SchematicHeader> headers() {
        return headers;
    }

    public boolean isScanning() {
        return scanning;
    }

    /** Bumped after every finished scan so views know to re-filter. */
    public int scanGeneration() {
        return scanGeneration;
    }

    public synchronized CompletableFuture<Void> refresh() {
        if (pendingScan != null && !pendingScan.isDone()) return pendingScan;
        scanning = true;
        pendingScan = CompletableFuture.runAsync(this::scan, scanner).whenComplete((ignored, error) -> {
            scanning = false;
            if (error != null) LOGGER.warn("Schematic scan failed", error);
        });
        return pendingScan;
    }

    public CompletableFuture<Loaded> load(Path path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Schematic schematic = SchematicFiles.read(path);
                return new Loaded(schematic, LayerStats.compute(schematic), SchematicDiff.Layout.of(schematic));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (OutOfMemoryError e) {
                // The dense arrays are dropped with this frame, so the client survives; report it as a load error.
                throw new UncheckedIOException(new IOException("Not enough memory to load " + path.getFileName(), e));
            }
        }, loader);
    }

    /** {@link #resolveIn} against the schematics folder, off the main thread. */
    public CompletableFuture<Resolution> resolve(String name) {
        return CompletableFuture.supplyAsync(() -> resolveIn(directory(), name), loader);
    }

    /**
     * Finds a file by exact name (with or without the extension, case-insensitive), then by unique prefix. Every
     * match must really live inside {@code dir}: {@code ../} names and symlinks pointing elsewhere are rejected.
     */
    static Resolution resolveIn(Path dir, String name) {
        if (name == null || name.isBlank()) return Resolution.notFound();
        String wanted = name.trim();
        String lower = wanted.toLowerCase(Locale.ROOT);
        if (SchematicFiles.extensionOf(lower) != null) lower = SchematicFiles.baseName(lower);
        Path root;
        try {
            root = dir.toRealPath();
        } catch (IOException e) {
            return Resolution.notFound();
        }
        try {
            if (SchematicFiles.extensionOf(wanted) != null) {
                Path typed = inside(root, root.resolve(wanted));
                if (typed != null) return Resolution.found(typed);
            }
            for (String extension : SchematicFiles.EXTENSIONS) {
                Path direct = inside(root, root.resolve(wanted + extension));
                if (direct != null) return Resolution.found(direct);
            }
        } catch (InvalidPathException ignored) {
            // Fall through to the listing; chat input can hold characters the file system rejects.
        }

        List<Path> prefixed = new ArrayList<>();
        for (Path candidate : listFiles(root)) {
            String base = SchematicFiles.baseName(candidate).toLowerCase(Locale.ROOT);
            if (!base.startsWith(lower)) continue;
            Path real = inside(root, candidate);
            if (real == null) continue;
            if (base.equals(lower)) return Resolution.found(real);
            prefixed.add(real);
        }
        if (prefixed.size() == 1) return Resolution.found(prefixed.getFirst());
        if (prefixed.isEmpty()) return Resolution.notFound();
        List<String> names = new ArrayList<>();
        for (Path path : prefixed) names.add(SchematicFiles.baseName(path));
        return new Resolution(null, List.copyOf(names));
    }

    /** The real path of {@code candidate} when it is a regular file under {@code root}, else null. */
    private static Path inside(Path root, Path candidate) {
        try {
            if (!Files.isRegularFile(candidate)) return null;
            Path real = candidate.toRealPath();
            return real.startsWith(root) && !real.equals(root) ? real : null;
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    public List<String> fileNames() {
        List<String> names = new ArrayList<>();
        for (Path path : listFiles(directory())) names.add(SchematicFiles.baseName(path));
        return names;
    }

    private void scan() {
        Path dir = directory();
        List<Path> files = listFiles(dir);
        List<SchematicHeader> next = new ArrayList<>(files.size());
        for (Path path : files) {
            next.add(header(path));
        }
        headerCache.keySet().retainAll(files);
        next.sort(Comparator.comparing(h -> h.fileName().toLowerCase(Locale.ROOT)));
        headers = List.copyOf(next);
        scanGeneration++;
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
            header = SchematicFiles.readHeader(path, modified);
        } catch (IOException | RuntimeException e) {
            header = SchematicHeader.failed(path, modified, e.getMessage());
        } catch (OutOfMemoryError e) {
            header = SchematicHeader.failed(path, modified, "out of memory");
        }
        headerCache.put(path, header);
        return header;
    }

    private static List<Path> listFiles(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(SchematicFiles::isSupported)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public record Loaded(Schematic schematic, LayerStats stats, SchematicDiff.Layout layout) {
    }

    /** A lookup result: {@code path} when exactly one file matched, else every ambiguous candidate (maybe none). */
    public record Resolution(Path path, List<String> candidates) {
        static Resolution found(Path path) {
            return new Resolution(path, List.of());
        }

        static Resolution notFound() {
            return new Resolution(null, List.of());
        }

        public boolean ambiguous() {
            return path == null && candidates.size() > 1;
        }
    }
}
