package combatant.client.config;

import combatant.client.config.storage.StorageFiles;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

public final class ConfigurationMigrationService {
    public static Path preflight(Path source, Path destination) throws IOException {
        Path from = StorageFiles.checked(source).toRealPath();
        Path target = StorageFiles.checked(destination);
        if (target.getParent() == null || !Files.isDirectory(target.getParent(), NOFOLLOW_LINKS)) {
            throw new IOException("Destination parent must already exist: " + target);
        }
        target = target.getParent().toRealPath().resolve(target.getFileName());
        if (target.startsWith(from) || from.startsWith(target)) throw new IOException("Configuration roots overlap");
        if (StorageFiles.exists(target)) throw new FileAlreadyExistsException(target.toString());
        if (!Files.isWritable(target.getParent())) throw new AccessDeniedException(target.getParent().toString());
        return target;
    }

    void recover(ConfigurationRootManager manager) throws IOException {
        Properties metadata = manager.metadata();
        Path source = manager.absoluteMetadataPath("source");
        Path destination = manager.absoluteMetadataPath("destination");
        if (!source.equals(manager.root())) throw new IOException("Migration source does not match active root");
        String state = manager.migrationState();
        if (state.equals("COMMITTING")) {
            finishCommit(manager, source, destination);
            return;
        }
        if (!Set.of("PREPARING", "COPYING", "VERIFYING").contains(state)) {
            throw new IOException("Configuration migration requires recovery: " + manager.bootstrapFile());
        }
        preflight(source, destination);
        Properties manifest = snapshot(source);
        long size = manifest.values().stream().map(Object::toString).filter(s -> !s.equals("D"))
                .mapToLong(s -> Long.parseLong(s.substring(0, s.indexOf(':')))).sum();
        long usable = Files.getFileStore(destination.getParent()).getUsableSpace();
        if (usable > 0 && usable < size) throw new IOException("Insufficient storage for configuration snapshot");

        // An interrupted copy is abandoned, never mistaken for a verified snapshot or deleted recursively.
        String id = UUID.randomUUID().toString();
        Path staging = destination.resolveSibling(".combatant-transfer-" + id);
        Path manifestFile = manager.bootstrapFile().resolveSibling("combatant-transfer-" + id + ".properties");
        metadata.setProperty("staging", staging.toString());
        metadata.setProperty("manifest", manifestFile.toString());
        StorageFiles.write(manifestFile, manifest);
        manager.persist("COPYING");
        Files.createDirectory(staging);
        for (String relative : new TreeSet<>(manifest.stringPropertyNames())) {
            Path target = staging.resolve(relative);
            if (manifest.getProperty(relative).equals("D")) {
                Files.createDirectories(target);
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(StorageFiles.checked(source.resolve(relative)), target, NOFOLLOW_LINKS);
                try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, NOFOLLOW_LINKS)) {
                    channel.force(true);
                }
            }
        }
        manager.persist("VERIFYING");
        verify(source, manifest);
        verify(staging, manifest);
        try (var directories = Files.walk(staging)) {
            for (Path directory : directories.filter(p -> Files.isDirectory(p, NOFOLLOW_LINKS))
                    .sorted(Comparator.reverseOrder()).toList()) StorageFiles.forceDirectory(directory);
        }
        manager.persist("COMMITTING");
        finishCommit(manager, source, destination);
    }

    private void finishCommit(ConfigurationRootManager manager, Path source, Path destination) throws IOException {
        Path staging = manager.absoluteMetadataPath("staging");
        Path manifestFile = manager.absoluteMetadataPath("manifest");
        if (!staging.getParent().equals(destination.getParent())
                || !staging.getFileName().toString().startsWith(".combatant-transfer-")
                || !manifestFile.getParent().equals(manager.bootstrapFile().getParent())
                || !Files.isRegularFile(manifestFile, NOFOLLOW_LINKS)) {
            throw new IOException("Invalid migration recovery paths");
        }
        Properties manifest = StorageFiles.read(manifestFile);
        verify(source, manifest);
        if (StorageFiles.exists(staging)) {
            preflight(source, destination);
            verify(staging, manifest);
            // Same-filesystem rename without REPLACE_EXISTING: an intervening destination is a conflict.
            Files.move(staging, destination);
        }
        StorageFiles.forceDirectory(destination.getParent());
        manager.lockDestination(destination);
        verify(destination, manifest);
        manager.activate(destination);
    }

    private static void verify(Path root, Properties expected) throws IOException {
        if (!snapshot(root).equals(expected)) throw new IOException("Configuration snapshot verification failed: " + root);
    }

    static Properties snapshot(Path root) throws IOException {
        StorageFiles.checked(root);
        if (!Files.isDirectory(root, NOFOLLOW_LINKS)) throw new IOException("Missing snapshot root: " + root);
        Properties manifest = new Properties();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                StorageFiles.checked(directory);
                if (!directory.equals(root)) manifest.setProperty(root.relativize(directory).toString(), "D");
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                StorageFiles.checked(file);
                if (!attributes.isRegularFile()) throw new IOException("Unsupported configuration entry: " + file);
                if (file.equals(root.resolve(ConfigurationRootManager.LOCK_FILE))) return FileVisitResult.CONTINUE;
                MessageDigest digest;
                try { digest = MessageDigest.getInstance("SHA-256"); }
                catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
                long size = 0;
                try (var input = Files.newInputStream(file, NOFOLLOW_LINKS)) {
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        digest.update(buffer, 0, count);
                        size += count;
                    }
                }
                manifest.setProperty(root.relativize(file).toString(), size + ":" + HexFormat.of().formatHex(digest.digest()));
                return FileVisitResult.CONTINUE;
            }
        });
        return manifest;
    }
}
