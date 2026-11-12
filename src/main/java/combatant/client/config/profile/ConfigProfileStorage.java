/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;

import combatant.client.config.ConfigPaths;
import combatant.client.config.persist.AtomicFileStore;
import combatant.client.util.logging.DebugLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConfigProfileStorage {
    public static final String EXTENSION = ".cbcfg";

    private final ConfigProfileBinaryCodec codec = new ConfigProfileBinaryCodec();
    private final Map<ConfigProfileType, List<ConfigProfileMeta>> cached = new EnumMap<>(ConfigProfileType.class);
    private final Map<ConfigProfileType, Long> refreshedAt = new EnumMap<>(ConfigProfileType.class);
    private long lastLooseScanMs;

    public synchronized void invalidate() {
        cached.clear();
        refreshedAt.clear();
    }

    public static String sanitizeFileName(String name) {
        if (name == null) return "config";
        String base = name.trim().toLowerCase(Locale.ROOT);
        if (base.endsWith(EXTENSION)) base = base.substring(0, base.length() - EXTENSION.length());
        if (base.isEmpty()) return "config";
        StringBuilder out = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        return out.isEmpty() ? "config" : out.toString();
    }

    public Path root() {
        return ConfigPaths.profilesRoot();
    }

    public Path directory(ConfigProfileType type) {
        return root().resolve(type.folderName());
    }

    public synchronized List<ConfigProfileMeta> list(ConfigProfileType type) {
        long now = System.currentTimeMillis();
        if (lastLooseScanMs == 0L) {
            normalizeLooseProfiles();
            lastLooseScanMs = now;
        }
        List<ConfigProfileMeta> existing = cached.get(type);
        if (existing != null && now - refreshedAt.getOrDefault(type, 0L) < 1600L) return existing;
        List<ConfigProfileMeta> out = new ArrayList<>();
        Path dir = directory(type);
        if (!Files.isDirectory(dir)) {
            cached.put(type, List.of());
            refreshedAt.put(type, now);
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(EXTENSION))
                    .forEach(path -> {
                        try {
                            AtomicFileStore.ReadResult read = AtomicFileStore.readRecovering(path, this::validProfileBytes);
                            if (!read.found()) return;
                            ConfigProfileMeta meta = codec.readMeta(read.bytes());
                            out.add(meta);
                        } catch (Exception e) {
                            DebugLog.error("Failed to read profile meta %s", e, path.toAbsolutePath());
                        }
                    });
        } catch (IOException e) {
            DebugLog.error("Failed to list profiles %s", e, dir.toAbsolutePath());
        }
        out.sort(Comparator.comparingLong(ConfigProfileMeta::updatedAt).reversed());
        List<ConfigProfileMeta> result = List.copyOf(out);
        cached.put(type, result);
        refreshedAt.put(type, now);
        return result;
    }

    public ImportResult importProfileFile(Path source, boolean moveSource) throws IOException {
        if (source == null) throw new IllegalArgumentException("profile path is null");
        if (!Files.isRegularFile(source)) throw new IOException("profile is not a regular file: " + source);
        String fileName = source.getFileName() == null ? "" : source.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
            throw new IOException("not a " + EXTENSION + " profile: " + source);
        }

        byte[] bytes = Files.readAllBytes(source);
        ConfigProfileMeta meta = codec.read(bytes).meta();
        Path dest = file(meta.type(), meta.getId());
        Files.createDirectories(dest.getParent());

        Path sourceAbs = source.toAbsolutePath().normalize();
        Path destAbs = dest.toAbsolutePath().normalize();
        if (!sourceAbs.equals(destAbs)) {
            writeValidated(dest, bytes);
            if (moveSource) Files.delete(source);
        }
        invalidate();
        return new ImportResult(meta, dest);
    }

    public ConfigProfileSnapshot read(ConfigProfileType type, String idOrName) throws IOException {
        Path path = file(type, idOrName);
        AtomicFileStore.ReadResult result = AtomicFileStore.readRecovering(path, this::validProfileBytes);
        if (!result.found()) throw new IOException("profile or valid backup not found: " + path);
        return codec.read(result.bytes());
    }

    public ConfigProfileSnapshot read(ConfigProfileMeta meta) throws IOException {
        if (meta == null) throw new IllegalArgumentException("profile meta is null");
        return read(meta.type(), meta.getId());
    }

    public void save(ConfigProfileSnapshot snapshot) throws IOException {
        if (snapshot == null) throw new IllegalArgumentException("snapshot is null");
        Files.createDirectories(directory(snapshot.meta().type()));
        Path path = file(snapshot.meta().type(), snapshot.meta().getId());
        ConfigProfileSnapshot stamped = new ConfigProfileSnapshot(
                snapshot.meta().withCombatantVersion(currentCombatantVersion()), snapshot.entries());
        writeValidated(path, codec.write(stamped));
        invalidate();
        DebugLog.config("Config profile saved: %s", path.toAbsolutePath());
    }

    private boolean validProfileBytes(byte[] bytes) {
        try {
            codec.read(bytes);
            return true;
        } catch (IOException | RuntimeException invalid) {
            return false;
        }
    }

    /** Atomic write that only rotates a decodable existing profile into {@code .bak}. */
    void writeValidated(Path path, byte[] bytes) throws IOException {
        AtomicFileStore.write(path, bytes, this::validProfileBytes);
    }

    private static String currentCombatantVersion() {
        try {
            return combatant.client.runtime.CombatantBuild.version();
        } catch (Throwable t) {
            return "";
        }
    }

    public boolean delete(ConfigProfileType type, String idOrName) throws IOException {
        Path path = file(type, idOrName);
        boolean deleted = Files.deleteIfExists(path);
        if (deleted) {
            // Explicit deletion must not be reversed by recovery from an orphaned .bak.
            Files.deleteIfExists(AtomicFileStore.backupOf(path));
            invalidate();
        }
        return deleted;
    }

    public ConfigProfileSnapshot rename(ConfigProfileType type, String idOrName, String newName) throws IOException {
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("profile name is blank");
        ConfigProfileSnapshot old = read(type, idOrName);
        ConfigProfileMeta oldMeta = old.meta();
        ConfigProfileMeta newMeta = oldMeta.withName(newName.trim(), System.currentTimeMillis());
        Path oldPath = file(type, oldMeta.getId());
        Path newPath = file(type, newMeta.getId());
        if (!oldMeta.getId().equals(newMeta.getId()) && Files.exists(newPath)) {
            throw new IOException("profile already exists: " + newMeta.name());
        }
        ConfigProfileSnapshot renamed = new ConfigProfileSnapshot(newMeta, old.entries());
        save(renamed);
        if (!oldPath.equals(newPath)) {
            Files.deleteIfExists(oldPath);
            Files.deleteIfExists(AtomicFileStore.backupOf(oldPath));
        }
        invalidate();
        return renamed;
    }

    public boolean hasBackup(ConfigProfileType type, String idOrName) {
        return Files.isRegularFile(AtomicFileStore.backupOf(file(type, idOrName)));
    }

    /** Validates and restores a .cbcfg backup without overwriting the backup itself. */
    public void restoreBackup(ConfigProfileType type, String idOrName) throws IOException {
        Path target = file(type, idOrName);
        Path backup = AtomicFileStore.backupOf(target);
        byte[] bytes = Files.readAllBytes(backup);
        ConfigProfileSnapshot recovered = codec.read(bytes);
        if (recovered.meta().type() != type || !recovered.meta().getId().equals(sanitizeFileName(idOrName)))
            throw new IOException("backup belongs to another profile");
        Path preserve = target.resolveSibling(target.getFileName() + ".before-restore");
        if (Files.isRegularFile(target)) Files.copy(target, preserve, StandardCopyOption.REPLACE_EXISTING);
        // Keep the recovered .bak intact; the pre-restore primary is saved separately.
        AtomicFileStore.write(target, bytes, existing -> false);
        invalidate();
    }

    public boolean exists(ConfigProfileType type, String idOrName) {
        return Files.exists(file(type, idOrName));
    }

    public Path file(ConfigProfileType type, String idOrName) {
        String id = sanitizeFileName(idOrName);
        if (!id.endsWith(EXTENSION)) id += EXTENSION;
        return directory(type).resolve(id);
    }

    private void normalizeLooseProfiles() {
        if (!Files.isDirectory(root())) return;
        List<Path> candidates = new ArrayList<>();
        try (var stream = Files.walk(root())) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXTENSION))
                    .forEach(candidates::add);
        } catch (IOException e) {
            DebugLog.error("Failed to scan loose config profiles %s", e, root().toAbsolutePath());
            return;
        }

        for (Path candidate : candidates) {
            try {
                ConfigProfileMeta meta = codec.read(Files.readAllBytes(candidate)).meta();
                Path dest = file(meta.type(), meta.getId());
                Path candidateAbs = candidate.toAbsolutePath().normalize();
                Path destAbs = dest.toAbsolutePath().normalize();
                if (candidateAbs.equals(destAbs)) continue;
                Files.createDirectories(dest.getParent());
                Files.move(candidate, dest, StandardCopyOption.REPLACE_EXISTING);
                DebugLog.config("Moved config profile %s -> %s", candidateAbs, destAbs);
            } catch (Exception e) {
                DebugLog.error("Failed to normalize config profile %s", e, candidate.toAbsolutePath());
            }
        }
    }

    public record ImportResult(ConfigProfileMeta meta, Path path) {
    }
}
