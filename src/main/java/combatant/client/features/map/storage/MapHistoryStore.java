/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.storage;

import combatant.client.config.ConfigPaths;
import combatant.client.config.subsystem.MapStorageConfig;
import combatant.client.util.logging.DebugLog;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded asynchronous map-history backend. Render/client threads only enqueue immutable frames.
 * Compression, disk writes, index maintenance, lazy reads and retention run on one storage worker.
 */
public final class MapHistoryStore {
    private static final MapHistoryStore INSTANCE = new MapHistoryStore();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final MapStorageConfig config = MapStorageConfig.get();
    private final Path root = FabricLoader.getInstance().getGameDir()
            .resolve(ConfigPaths.root()).resolve("map_history").toAbsolutePath().normalize();
    private final ArrayBlockingQueue<Command> queue = new ArrayBlockingQueue<>(8192);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong writtenRecords = new AtomicLong();
    private final AtomicLong writtenChunks = new AtomicLong();
    private final Thread worker;

    // Worker-thread only.
    private final Map<ChunkKey, Buffer> buffers = new HashMap<>();
    private final Map<RecordKey, MapHistoryRecord> lastMaterial = new HashMap<>();
    private MapHistoryCatalog catalog;
    private long lastRetentionAt;

    private MapHistoryStore() {
        worker = new Thread(this::loop, "Combatant-Map-History");
        worker.setDaemon(true);
        worker.start();
    }

    public static MapHistoryStore get() {
        return INSTANCE;
    }

    public Path root() {
        return root;
    }

    public long droppedRecords() {
        return dropped.get();
    }

    public long writtenRecords() {
        return writtenRecords.get();
    }

    public long writtenChunks() {
        return writtenChunks.get();
    }

    public boolean offer(String serverKey, MapHistoryRecord record) {
        if (!running.get() || !config.enabled() || record == null || serverKey == null || serverKey.isBlank()) {
            return false;
        }
        if (!sourceEnabled(record)) return false;
        if (record.type() == MapHistoryRecordType.BEARING
                && config.retentionMode() != MapHistoryRetentionMode.WITH_BEARINGS) {
            return false;
        }
        boolean accepted = queue.offer(new WriteCommand(serverKey.trim(), record));
        if (!accepted) dropped.incrementAndGet();
        return accepted;
    }

    public CompletableFuture<List<MapHistoryRecord>> queryAsync(MapHistoryQuery query) {
        CompletableFuture<List<MapHistoryRecord>> future = new CompletableFuture<>();
        if (!running.get()) {
            future.complete(List.of());
            return future;
        }
        if (!queue.offer(new QueryCommand(query, future))) {
            future.completeExceptionally(new IOException("Map history queue is full"));
        }
        return future;
    }

    public CompletableFuture<List<MapHistoryCatalogEntry>> searchAsync(String query, int limit) {
        CompletableFuture<List<MapHistoryCatalogEntry>> future = new CompletableFuture<>();
        if (!running.get()) {
            future.complete(List.of());
            return future;
        }
        if (!queue.offer(new SearchCommand(query, limit, future))) {
            future.completeExceptionally(new IOException("Map history queue is full"));
        }
        return future;
    }

    public CompletableFuture<Void> rebuildIndexAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (!running.get()) {
            future.complete(null);
            return future;
        }
        if (!queue.offer(new RebuildCommand(future))) {
            future.completeExceptionally(new IOException("Map history queue is full"));
        }
        return future;
    }

    public CompletableFuture<Void> flushAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (!running.get()) {
            future.complete(null);
            return future;
        }
        if (!queue.offer(new FlushCommand(future))) {
            future.completeExceptionally(new IOException("Map history queue is full"));
        }
        return future;
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) return;
        CountDownLatch latch = new CountDownLatch(1);
        // A shutdown command must not be silently dropped. Client shutdown may wait for queue space.
        try {
            queue.put(new ShutdownCommand(latch));
            if (!latch.await(15000, TimeUnit.MILLISECONDS)) {
                DebugLog.warn("Timed out draining map history during shutdown; {} commands remain", queue.size());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            worker.interrupt();
        }
    }

    private void loop() {
        try {
            Files.createDirectories(root);
            catalog = new MapHistoryCatalog(root);
            catalog.loadOrRebuild();
            enforceRetention();
        } catch (IOException error) {
            DebugLog.error("Failed to initialize map history storage", error);
            catalog = new MapHistoryCatalog(root);
        }

        while (running.get() || !queue.isEmpty()) {
            try {
                Command command = queue.poll(250, TimeUnit.MILLISECONDS);
                if (command != null) handle(command);
                flushDue(false);
                maybeRetention();
            } catch (InterruptedException interrupted) {
                if (!running.get()) break;
            } catch (Throwable error) {
                DebugLog.error("Map history worker failure", error);
            }
        }

        try {
            flushDue(true);
        } catch (Throwable error) {
            DebugLog.error("Failed to flush map history on shutdown", error);
        }
    }

    private void handle(Command command) {
        try {
            switch (command) {
                case WriteCommand write -> accept(write.serverKey, write.record);
                case QueryCommand query -> query.future.complete(read(query.query));
                case SearchCommand search -> search.future.complete(catalog.search(search.query, search.limit));
                case RebuildCommand rebuild -> {
                    flushDue(true);
                    catalog.rebuild();
                    rebuild.future.complete(null);
                }
                case FlushCommand flush -> {
                    flushDue(true);
                    flush.future.complete(null);
                }
                case ShutdownCommand shutdown -> {
                    flushDue(true);
                    shutdown.latch.countDown();
                }
            }
        } catch (Throwable error) {
            switch (command) {
                case QueryCommand query -> query.future.completeExceptionally(error);
                case SearchCommand search -> search.future.completeExceptionally(error);
                case RebuildCommand rebuild -> rebuild.future.completeExceptionally(error);
                case FlushCommand flush -> flush.future.completeExceptionally(error);
                case ShutdownCommand shutdown -> shutdown.latch.countDown();
                default -> DebugLog.error("Map history command failed", error);
            }
        }
    }

    private void accept(String serverKey, MapHistoryRecord record) {
        if (!materialChange(serverKey, record)) return;
        ChunkKey key = new ChunkKey(serverKey, record.targetUuid(), record.worldKey(), record.source(),
                record.sourceKey(), record.type());
        Buffer buffer = buffers.computeIfAbsent(key, ignored -> new Buffer(System.currentTimeMillis()));
        buffer.records.add(record);
        if (buffer.records.size() >= config.chunkRecords()) {
            try {
                flushKey(key, buffer, true);
            } catch (IOException error) {
                DebugLog.error("Failed to flush map history chunk", error);
            }
        }
    }

    private boolean materialChange(String serverKey, MapHistoryRecord record) {
        RecordKey key = new RecordKey(serverKey, record.targetUuid(), record.worldKey(), record.source(),
                record.sourceKey(), record.type());
        MapHistoryRecord previous = lastMaterial.get(key);
        if (previous == null) {
            lastMaterial.put(key, record);
            return true;
        }

        boolean persist = switch (record) {
            case MapEstimateFrame next when previous instanceof MapEstimateFrame old -> estimateChanged(old, next);
            case MapExactPointFrame next when previous instanceof MapExactPointFrame old -> exactChanged(old, next);
            case MapBearingFrame next when previous instanceof MapBearingFrame old ->
                    next.sourceRevision() != old.sourceRevision();
            case MapHistoryEventFrame next when previous instanceof MapHistoryEventFrame old ->
                    next.eventKind() != old.eventKind() || next.sourceRevision() != old.sourceRevision()
                            || next.generation() != old.generation() || next.segmentId() != old.segmentId();
            default -> true;
        };
        if (persist) lastMaterial.put(key, record);
        return persist;
    }

    private boolean estimateChanged(MapEstimateFrame old, MapEstimateFrame next) {
        if (next.segmentId() != old.segmentId()) return true;
        if (next.observedAtMs() - old.observedAtMs() >= config.estimateMaxIntervalMs()) return true;
        if (Math.hypot(next.x() - old.x(), next.z() - old.z()) >= config.estimateCenterChange()) return true;
        double baseUncertainty = Math.max(0.001, old.uncertaintyMajor());
        double uncertaintyDelta = Math.abs(next.uncertaintyMajor() - old.uncertaintyMajor()) / baseUncertainty;
        if (uncertaintyDelta >= config.estimateUncertaintyImprovePct()) return true;
        return Math.abs(next.confidence() - old.confidence()) >= config.estimateConfidenceChange();
    }

    private boolean exactChanged(MapExactPointFrame old, MapExactPointFrame next) {
        if (next.observedAtMs() - old.observedAtMs() >= config.exactMaxIntervalMs()) return true;
        double dx = next.x() - old.x();
        double dy = next.y() - old.y();
        double dz = next.z() - old.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz) >= config.exactPositionChange();
    }

    private void flushDue(boolean force) throws IOException {
        if (buffers.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<Map.Entry<ChunkKey, Buffer>> entries = new ArrayList<>(buffers.entrySet());
        for (Map.Entry<ChunkKey, Buffer> entry : entries) {
            Buffer buffer = entry.getValue();
            if (buffer.records.isEmpty()) {
                buffers.remove(entry.getKey());
                continue;
            }
            if (!force && buffer.records.size() < config.chunkRecords()
                    && now - buffer.firstBufferedAt < config.flushIntervalMs()) {
                continue;
            }
            flushKey(entry.getKey(), buffer, force);
        }
    }

    private void flushKey(ChunkKey key, Buffer buffer, boolean forceAll) throws IOException {
        int chunkSize = Math.max(1, config.chunkRecords());
        while (!buffer.records.isEmpty() && (forceAll || buffer.records.size() >= chunkSize)) {
            int count = forceAll ? Math.min(chunkSize, buffer.records.size()) : chunkSize;
            List<MapHistoryRecord> chunk = List.copyOf(buffer.records.subList(0, count));
            writeChunk(key.serverKey, chunk);
            buffer.records.subList(0, count).clear();
            buffer.firstBufferedAt = System.currentTimeMillis();
        }
        if (buffer.records.isEmpty()) buffers.remove(key);
    }

    private void writeChunk(String serverKey, List<MapHistoryRecord> chunk) throws IOException {
        if (chunk.isEmpty()) return;
        long timestamp = chunk.getFirst().observedAtMs();
        Path file = selectFile(serverKey, timestamp);
        List<MapHistoryChunkMeta> appended;
        try (CbpDatFile data = CbpDatFile.open(file, serverKey)) {
            appended = data.appendChunks(serverKey, List.of(chunk));
        }
        try {
            catalog.add(file, appended);
        } catch (IOException catalogError) {
            DebugLog.error("Failed to update map history catalog; rebuilding", catalogError);
            try {
                catalog.rebuild();
            } catch (IOException rebuildError) {
                DebugLog.error("Failed to rebuild map history catalog after committed chunk", rebuildError);
            }
        }
        writtenChunks.addAndGet(appended.size());
        writtenRecords.addAndGet(chunk.size());
    }

    private List<MapHistoryRecord> read(MapHistoryQuery query) throws IOException {
        if (query == null) return List.of();
        // Make records already accepted by the client visible to history reads.
        flushDue(true);
        List<Path> files = catalog.filesFor(query);
        if (files.isEmpty()) return List.of();

        List<MapHistoryRecord> out = new ArrayList<>();
        for (Path file : files) {
            if (!Files.isRegularFile(file)) continue;
            List<MapHistoryChunkMeta> metas;
            try {
                metas = CbpDatFile.inspect(file);
            } catch (IOException ignored) {
                continue;
            }
            String server = metas.stream().map(MapHistoryChunkMeta::serverKey).filter(s -> !s.isBlank()).findFirst()
                    .orElse(query.serverKey());
            try (CbpDatFile data = CbpDatFile.open(file, server)) {
                out.addAll(data.read(query));
            } catch (IOException ignored) {
            }
        }
        out.sort(Comparator.comparingLong(MapHistoryRecord::observedAtMs));
        return List.copyOf(out);
    }

    private Path selectFile(String serverKey, long observedAtMs) throws IOException {
        LocalDate date = Instant.ofEpochMilli(Math.max(0L, observedAtMs)).atZone(ZoneOffset.UTC).toLocalDate();
        Path dir = root.resolve("data").resolve(CbpDatFile.serverRef(serverKey).toString());
        Files.createDirectories(dir);
        String prefix = DAY.format(date) + "-";
        int highest = -1;
        Path latest = null;
        try (var stream = Files.list(dir)) {
            for (Path candidate : stream.filter(Files::isRegularFile).toList()) {
                String name = candidate.getFileName().toString();
                if (!name.startsWith(prefix) || !name.endsWith(".cbpdat")) continue;
                int dash = prefix.length();
                int dot = name.length() - ".cbpdat".length();
                try {
                    int seq = Integer.parseInt(name.substring(dash, dot));
                    if (seq > highest) {
                        highest = seq;
                        latest = candidate;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (latest != null && Files.size(latest) < config.maxFileSizeBytes()) return latest;
        int next = Math.max(0, highest + 1);
        return dir.resolve(prefix + String.format(java.util.Locale.ROOT, "%04d.cbpdat", next));
    }

    private void maybeRetention() {
        long now = System.currentTimeMillis();
        if (now - lastRetentionAt < 10L * 60_000L) return;
        try {
            enforceRetention();
        } catch (IOException error) {
            DebugLog.error("Map history retention failed", error);
        }
    }

    private void enforceRetention() throws IOException {
        lastRetentionAt = System.currentTimeMillis();
        Path dataRoot = root.resolve("data");
        if (!Files.isDirectory(dataRoot)) return;

        List<FileState> files = new ArrayList<>();
        try (var stream = Files.walk(dataRoot)) {
            for (Path path : stream.filter(Files::isRegularFile)
                    .filter(candidate -> candidate.getFileName().toString().endsWith(".cbpdat")).toList()) {
                FileTime modified = Files.getLastModifiedTime(path);
                files.add(new FileState(path, Files.size(path), modified.toMillis()));
            }
        }

        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(config.maxHistoryDays());
        boolean deleted = false;
        for (FileState state : files) {
            if (state.modifiedAt < cutoff) {
                Files.deleteIfExists(state.path);
                deleted = true;
            }
        }

        files.removeIf(state -> !Files.isRegularFile(state.path));
        long total = files.stream().mapToLong(FileState::size).sum();
        long limit = config.maxTotalStorageBytes();
        if (total > limit) {
            files.sort(Comparator.comparingLong(FileState::modifiedAt));
            for (FileState state : files) {
                if (total <= limit) break;
                if (Files.deleteIfExists(state.path)) {
                    total -= state.size;
                    deleted = true;
                }
            }
        }
        if (deleted) catalog.removeMissingFiles();
    }

    private boolean sourceEnabled(MapHistoryRecord record) {
        return switch (record.source()) {
            case HEURISTIC_TRIANGULATED -> config.storeHeuristic();
            case DUPLEX_TRIANGULATED -> config.storeDuplex();
            case MAPLINK_EXACT -> config.storeMapLinkExact();
            case LOCATOR_EXACT, LOCATOR_APPROXIMATE -> config.storeLocatorExact();
            case LOCAL_ENTITY_EXACT -> config.storeLocalExact();
            case LOCATOR_BEARING -> true;
        };
    }

    private sealed interface Command permits WriteCommand, QueryCommand, SearchCommand,
            RebuildCommand, FlushCommand, ShutdownCommand {
    }

    private record WriteCommand(String serverKey, MapHistoryRecord record) implements Command {
    }

    private record QueryCommand(MapHistoryQuery query,
                                CompletableFuture<List<MapHistoryRecord>> future) implements Command {
    }

    private record SearchCommand(String query, int limit,
                                 CompletableFuture<List<MapHistoryCatalogEntry>> future) implements Command {
    }

    private record RebuildCommand(CompletableFuture<Void> future) implements Command {
    }

    private record FlushCommand(CompletableFuture<Void> future) implements Command {
    }

    private record ShutdownCommand(CountDownLatch latch) implements Command {
    }

    private record ChunkKey(String serverKey, UUID targetUuid, String worldKey, MapHistorySource source,
                            String sourceKey, MapHistoryRecordType type) {
    }

    private record RecordKey(String serverKey, UUID targetUuid, String worldKey, MapHistorySource source,
                             String sourceKey, MapHistoryRecordType type) {
    }

    private static final class Buffer {
        private final List<MapHistoryRecord> records = new ArrayList<>();
        private long firstBufferedAt;

        private Buffer(long firstBufferedAt) {
            this.firstBufferedAt = firstBufferedAt;
        }
    }

    private record FileState(Path path, long size, long modifiedAt) {
    }
}
