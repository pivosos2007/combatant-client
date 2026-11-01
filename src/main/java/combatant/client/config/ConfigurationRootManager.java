package combatant.client.config;

import combatant.client.config.storage.StorageFiles;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

public final class ConfigurationRootManager implements AutoCloseable {
    static final String LOCK_FILE = ".combatant-root.lock";
    private final Path defaultRoot;
    private final Path bootstrap;
    private final List<StorageFiles.Lease> leases = new ArrayList<>();
    private Properties metadata;
    private Path root;
    private boolean closed;

    private ConfigurationRootManager(Path gameDirectory) throws IOException {
        Path config = StorageFiles.checked(gameDirectory).resolve("config");
        StorageFiles.checked(config);
        Files.createDirectories(config);
        defaultRoot = config.resolve("combatant");
        bootstrap = config.resolve("combatant-storage.properties");
    }

    public static ConfigurationRootManager open(Path gameDirectory) throws IOException {
        ConfigurationRootManager manager = new ConfigurationRootManager(gameDirectory);
        try {
            manager.start();
            return manager;
        } catch (IOException | RuntimeException e) {
            try { manager.close(); } catch (IOException close) { e.addSuppressed(close); }
            throw e;
        }
    }

    private void start() throws IOException {
        leases.add(StorageFiles.lock(bootstrap.resolveSibling("combatant-storage.lock")));
        boolean existing = StorageFiles.exists(bootstrap);
        metadata = StorageFiles.read(bootstrap);
        if (existing && (!"1".equals(metadata.getProperty("schema")) || metadata.getProperty("active") == null)) {
            throw new IOException("Unsupported or corrupt bootstrap metadata: " + bootstrap);
        }
        root = existing ? absoluteMetadataPath("active") : defaultRoot;
        if (!existing) {
            StorageFiles.checked(root);
            Files.createDirectories(root);
            metadata.setProperty("schema", "1");
            metadata.setProperty("active", root.toString());
            metadata.setProperty("state", "IDLE");
        }
        requireRoot(root);
        leases.add(StorageFiles.lock(root.resolve(LOCK_FILE)));
        String state = metadata.getProperty("state", "IDLE");
        if (!state.equals("IDLE") && !state.equals("COMMITTED")) {
            try {
                new ConfigurationMigrationService().recover(this);
            } catch (IOException e) {
                metadata.setProperty("lastError", e.toString());
                try { StorageFiles.write(bootstrap, metadata); }
                catch (IOException journalError) { e.addSuppressed(journalError); }
                throw new IOException("Configuration migration stopped; source preserved. Recovery metadata: " + bootstrap, e);
            }
        }
    }

    public Path root() { return root; }
    public Path defaultRoot() { return defaultRoot; }
    public Path bootstrapFile() { return bootstrap; }
    public synchronized String migrationState() { return metadata.getProperty("state", "IDLE"); }
    public synchronized Path pendingDestination() {
        if (List.of("IDLE", "COMMITTED").contains(migrationState())) return null;
        String destination = metadata.getProperty("destination");
        return destination == null ? null : Path.of(destination);
    }

    public Path destinationUnder(Path selectedParent) throws IOException {
        return StorageFiles.checked(selectedParent).resolve("combatant");
    }

    /** Schedules the exact, previously reviewed destination; never changes this process's root. */
    public synchronized void schedule(Path destination) throws IOException {
        if (closed) throw new IOException("Storage manager is closed");
        if (!List.of("IDLE", "COMMITTED").contains(migrationState())) {
            throw new IOException("A configuration migration is already scheduled");
        }
        Path target = ConfigurationMigrationService.preflight(root, destination);
        Properties next = new Properties();
        next.setProperty("schema", "1");
        next.setProperty("active", root.toString());
        next.setProperty("source", root.toString());
        next.setProperty("destination", target.toString());
        next.setProperty("state", "PREPARING");
        StorageFiles.write(bootstrap, next);
        metadata = next;
    }

    public synchronized void restoreDefault() throws IOException { schedule(defaultRoot); }

    public synchronized void cancelScheduled() throws IOException {
        if (closed) throw new IOException("Storage manager is closed");
        if (!migrationState().equals("PREPARING")) throw new IOException("No cancellable scheduled migration");
        Properties next = new Properties();
        next.setProperty("schema", "1");
        next.setProperty("active", root.toString());
        next.setProperty("state", "IDLE");
        StorageFiles.write(bootstrap, next);
        metadata = next;
    }

    /** Offline recovery only; acquires the same locks as startup and preserves every transfer artifact. */
    public static void cancelPendingMigration(Path gameDirectory) throws IOException {
        try (var manager = new ConfigurationRootManager(gameDirectory)) {
            manager.leases.add(StorageFiles.lock(manager.bootstrap.resolveSibling("combatant-storage.lock")));
            manager.metadata = StorageFiles.read(manager.bootstrap);
            if (!"1".equals(manager.metadata.getProperty("schema"))) throw new IOException("Invalid bootstrap schema");
            String state = manager.migrationState();
            if (!List.of("PREPARING", "COPYING", "VERIFYING", "COMMITTING").contains(state)) {
                throw new IOException("No pending migration to cancel");
            }
            Path source = manager.absoluteMetadataPath("source");
            if (!source.equals(manager.absoluteMetadataPath("active"))) throw new IOException("Migration already activated");
            requireRoot(source);
            manager.leases.add(StorageFiles.lock(source.resolve(LOCK_FILE)));
            Properties idle = new Properties();
            idle.setProperty("schema", "1");
            idle.setProperty("active", source.toString());
            idle.setProperty("state", "IDLE");
            StorageFiles.write(manager.bootstrap, idle);
        }
    }

    Properties metadata() { return metadata; }
    void persist(String state) throws IOException {
        metadata.setProperty("state", state);
        StorageFiles.write(bootstrap, metadata);
    }

    Path absoluteMetadataPath(String key) throws IOException {
        try {
            Path path = Path.of(metadata.getProperty(key, ""));
            if (!path.isAbsolute()) throw new IOException("Missing/relative metadata path: " + key);
            return StorageFiles.checked(path);
        } catch (InvalidPathException e) {
            throw new IOException("Invalid metadata path: " + key, e);
        }
    }

    void lockDestination(Path destination) throws IOException {
        requireRoot(destination);
        leases.add(StorageFiles.lock(destination.resolve(LOCK_FILE)));
    }

    void activate(Path destination) throws IOException {
        metadata.remove("lastError");
        metadata.setProperty("active", destination.toString());
        persist("COMMITTED");
        root = destination;
    }

    private static void requireRoot(Path path) throws IOException {
        StorageFiles.checked(path);
        if (!Files.isDirectory(path, NOFOLLOW_LINKS) || !Files.isReadable(path) || !Files.isWritable(path)) {
            throw new IOException("Configured directory unavailable; no fallback was activated: " + path);
        }
    }

    @Override public synchronized void close() throws IOException {
        closed = true;
        IOException failure = null;
        for (StorageFiles.Lease lease : leases.reversed()) {
            try { lease.close(); } catch (IOException e) { failure = e; }
        }
        leases.clear();
        if (failure != null) throw failure;
    }
}
