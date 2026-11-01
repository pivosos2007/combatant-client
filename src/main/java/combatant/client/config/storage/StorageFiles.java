package combatant.client.config.storage;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Properties;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.*;

public final class StorageFiles {
    private StorageFiles() { }

    public static boolean exists(Path path) throws IOException {
        try {
            Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS);
            return true;
        } catch (NoSuchFileException e) {
            return false;
        }
    }

    public static Path checked(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path cursor = absolute.getRoot();
        for (Path part : absolute) {
            cursor = cursor.resolve(part);
            if (!exists(cursor)) continue;
            BasicFileAttributes attributes = Files.readAttributes(cursor, BasicFileAttributes.class, NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther()
                    || !cursor.toRealPath().equals(cursor.toRealPath(NOFOLLOW_LINKS))) {
                throw new IOException("Filesystem links/reparse points are not supported: " + cursor);
            }
        }
        return absolute;
    }

    public static Properties read(Path file) throws IOException {
        checked(file);
        Properties properties = new Properties();
        if (exists(file)) {
            try (var input = Files.newInputStream(file, NOFOLLOW_LINKS)) {
                properties.load(input);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid storage metadata: " + file, e);
            }
        }
        return properties;
    }

    public static void write(Path file, Properties properties) throws IOException {
        checked(file);
        Files.createDirectories(file.getParent());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        properties.store(bytes, "Combatant storage metadata");
        Path temporary = Files.createTempFile(file.getParent(), ".combatant-metadata-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, WRITE, NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes.toByteArray());
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // Do not downgrade to a non-atomic replacement of the authoritative root pointer.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            forceDirectory(file.getParent());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void forceDirectory(Path directory) throws IOException {
        // Windows does not expose directory fsync through FileChannel.
        if (System.getProperty("os.name", "").startsWith("Windows")) return;
        try (FileChannel channel = FileChannel.open(directory, READ)) {
            channel.force(true);
        }
    }

    public static Lease lock(Path file) throws IOException {
        checked(file);
        FileChannel channel = FileChannel.open(file, CREATE, WRITE, NOFOLLOW_LINKS);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IOException("Storage is already in use: " + file);
            return new Lease(channel, lock);
        } catch (IOException | OverlappingFileLockException e) {
            channel.close();
            throw new IOException("Cannot exclusively lock storage: " + file, e);
        }
    }

    public record Lease(FileChannel channel, FileLock lock) implements AutoCloseable {
        @Override public void close() throws IOException {
            channel.close();
        }
    }
}
