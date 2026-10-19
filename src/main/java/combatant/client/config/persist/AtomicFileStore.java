/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.persist;

import combatant.client.util.logging.DebugLog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Storage + recovery layer for config files.
 *
 * <p>Writes go to a temp file in the same directory, are fsynced and then moved over the target
 * atomically; the previous good content is kept as a single {@code <name>.bak}. Reads validate the
 * primary file and fall back to the backup, quarantining a corrupt primary as
 * {@code <name>.corrupt-<timestamp>}.
 */
public final class AtomicFileStore {
    public static final String BACKUP_SUFFIX = ".bak";
    public static final String CORRUPT_INFIX = ".corrupt-";
    private static final String TEMP_INFIX = ".tmp-";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final AtomicLong TEMP_SEQ = new AtomicLong();

    /** Decides whether file bytes are a complete, usable document. */
    @FunctionalInterface
    public interface Validator {
        boolean isValid(byte[] bytes);
    }

    public enum Origin {
        /** Neither file existed. */
        MISSING,
        PRIMARY,
        /** Primary was unusable; content comes from the backup (which was restored as primary). */
        BACKUP,
        /** Files existed but none was usable; primary (if any) was quarantined. */
        UNUSABLE
    }

    public record ReadResult(byte[] bytes, Origin origin) {
        public boolean found() {
            return bytes != null;
        }
    }

    private AtomicFileStore() {
    }

    public static Path backupOf(Path file) {
        return file.resolveSibling(file.getFileName() + BACKUP_SUFFIX);
    }

    // =============================================================
    //   Write
    // =============================================================

    /**
     * Atomically replaces {@code target}. The existing primary is copied to {@code .bak} first, but only
     * when {@code validator} (if non-null) accepts it, so a corrupt primary never overwrites a good backup.
     */
    public static void write(Path target, byte[] data, Validator validator) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        Path temp = target.resolveSibling(target.getFileName() + TEMP_INFIX + Long.toHexString(TEMP_SEQ.incrementAndGet()));
        try {
            writeAndSync(temp, data);
            rotateBackup(target, validator);
            moveIntoPlace(temp, target);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        }
    }

    private static void writeAndSync(Path temp, byte[] data) throws IOException {
        try (FileChannel channel = FileChannel.open(temp,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static void rotateBackup(Path target, Validator validator) {
        if (!Files.isRegularFile(target)) return;
        try {
            if (validator != null && !validator.isValid(Files.readAllBytes(target))) return;
            Path backup = backupOf(target);
            Path backupTemp = backup.resolveSibling(backup.getFileName() + TEMP_INFIX + Long.toHexString(TEMP_SEQ.incrementAndGet()));
            try {
                Files.copy(target, backupTemp, StandardCopyOption.REPLACE_EXISTING);
                moveIntoPlace(backupTemp, backup);
            } finally {
                Files.deleteIfExists(backupTemp);
            }
        } catch (IOException e) {
            DebugLog.warn("Config backup rotation failed for %s: %s", target.toAbsolutePath(), e.toString());
        }
    }

    private static void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailure) {
            // Windows can refuse an atomic replace while another process holds the target open.
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException fallbackFailure) {
                fallbackFailure.addSuppressed(atomicFailure);
                throw fallbackFailure;
            }
        }
    }

    // =============================================================
    //   Read / recovery
    // =============================================================

    /**
     * Reads {@code file}; if missing, empty-handed. If the primary fails validation the backup is
     * tried, the corrupt primary is quarantined, and a good backup is restored as the primary.
     */
    public static ReadResult readRecovering(Path file, Validator validator) {
        Path backup = backupOf(file);
        boolean primaryExists = Files.isRegularFile(file);
        byte[] primary = null;
        if (primaryExists) {
            primary = readQuietly(file);
            if (primary != null && isValid(validator, primary)) {
                return new ReadResult(primary, Origin.PRIMARY);
            }
            DebugLog.warn("Config file is corrupt or partial: %s", file.toAbsolutePath());
            if (!quarantine(file) && Files.exists(file)) {
                // Corrupt primary could not be moved aside: keep the good backup untouched, do not restore over it.
                byte[] keep = Files.isRegularFile(backup) ? readQuietly(backup) : null;
                if (keep != null && isValid(validator, keep)) {
                    DebugLog.warn("Corrupt config could not be quarantined; using backup in memory only: %s", file.toAbsolutePath());
                    return new ReadResult(keep, Origin.BACKUP);
                }
                return new ReadResult(null, Origin.UNUSABLE);
            }
        }

        byte[] backupBytes = Files.isRegularFile(backup) ? readQuietly(backup) : null;
        if (backupBytes != null && isValid(validator, backupBytes)) {
            try {
                write(file, backupBytes, validator);
            } catch (IOException e) {
                DebugLog.warn("Could not restore config backup %s: %s", backup.toAbsolutePath(), e.toString());
            }
            DebugLog.warn("Config recovered from backup: %s", backup.toAbsolutePath());
            return new ReadResult(backupBytes, Origin.BACKUP);
        }

        if (!primaryExists && backupBytes == null && !Files.exists(backup)) {
            return new ReadResult(null, Origin.MISSING);
        }
        if (backupBytes != null || Files.exists(backup)) {
            DebugLog.warn("Config backup is unusable: %s", backup.toAbsolutePath());
        }
        return new ReadResult(null, Origin.UNUSABLE);
    }

    /** Raw read without recovery side effects (null when absent/unreadable). */
    public static byte[] readIfExists(Path file) {
        return Files.isRegularFile(file) ? readQuietly(file) : null;
    }

    private static boolean isValid(Validator validator, byte[] bytes) {
        if (validator == null) return true;
        try {
            return validator.isValid(bytes);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] readQuietly(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            DebugLog.warn("Could not read config %s: %s", file.toAbsolutePath(), e.toString());
            return null;
        }
    }

    /** Test seam: when set, replaces the quarantine move (return false to simulate a failed/locked move). */
    public static volatile java.util.function.Predicate<Path> quarantineOverride;

    private static boolean quarantine(Path file) {
        java.util.function.Predicate<Path> override = quarantineOverride;
        if (override != null) return override.test(file);
        Path dest = file.resolveSibling(file.getFileName() + CORRUPT_INFIX + LocalDateTime.now().format(STAMP));
        try {
            Files.move(file, dest, StandardCopyOption.REPLACE_EXISTING);
            DebugLog.warn("Corrupt config quarantined: %s", dest.toAbsolutePath());
            return true;
        } catch (IOException e) {
            DebugLog.warn("Could not quarantine corrupt config %s: %s", file.toAbsolutePath(), e.toString());
            return false;
        }
    }
}
