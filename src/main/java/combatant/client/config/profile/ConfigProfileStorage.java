/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;

import combatant.client.config.ConfigPaths;
import combatant.client.util.logging.DebugLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ConfigProfileStorage {
    public static final String EXTENSION = ".cbcfg";
    private static final Path ROOT = ConfigPaths.profilesRoot();

    private final ConfigProfileBinaryCodec codec = new ConfigProfileBinaryCodec();

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
        return out.length() == 0 ? "config" : out.toString();
    }

    public Path root() {
        return ROOT;
    }

    public Path directory(ConfigProfileType type) {
        return ROOT.resolve(type.folderName());
    }

    public List<ConfigProfileMeta> list(ConfigProfileType type) {
        normalizeLooseProfiles();
        List<ConfigProfileMeta> out = new ArrayList<>();
        Path dir = directory(type);
        if (!Files.isDirectory(dir)) return out;
        try (var stream = Files.list(dir)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(EXTENSION))
                    .forEach(path -> {
                        try {
                            ConfigProfileMeta meta = codec.readMeta(Files.readAllBytes(path));
                            out.add(meta);
                        } catch (Exception e) {
                            DebugLog.error("Failed to read profile meta %s", e, path.toAbsolutePath());
                        }
                    });
        } catch (IOException e) {
            DebugLog.error("Failed to list profiles %s", e, dir.toAbsolutePath());
        }
        out.sort(Comparator.comparingLong(ConfigProfileMeta::updatedAt).reversed());
        return out;
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
            if (moveSource) {
                Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.write(dest, bytes);
            }
        }
        return new ImportResult(meta, dest);
    }

    public ConfigProfileSnapshot read(ConfigProfileType type, String idOrName) throws IOException {
        Path path = file(type, idOrName);
        return codec.read(Files.readAllBytes(path));
    }

    public ConfigProfileSnapshot read(ConfigProfileMeta meta) throws IOException {
        if (meta == null) throw new IllegalArgumentException("profile meta is null");
        return read(meta.type(), meta.getId());
    }

    public void save(ConfigProfileSnapshot snapshot) throws IOException {
        if (snapshot == null) throw new IllegalArgumentException("snapshot is null");
        Files.createDirectories(directory(snapshot.meta().type()));
        Path path = file(snapshot.meta().type(), snapshot.meta().getId());
        Files.write(path, codec.write(snapshot));
        DebugLog.config("Config profile saved: %s", path.toAbsolutePath());
    }

    public boolean delete(ConfigProfileType type, String idOrName) throws IOException {
        Path path = file(type, idOrName);
        return Files.deleteIfExists(path);
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
        if (!oldPath.equals(newPath)) Files.deleteIfExists(oldPath);
        return renamed;
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
        if (!Files.isDirectory(ROOT)) return;
        List<Path> candidates = new ArrayList<>();
        try (var stream = Files.walk(ROOT)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXTENSION))
                    .forEach(candidates::add);
        } catch (IOException e) {
            DebugLog.error("Failed to scan loose config profiles %s", e, ROOT.toAbsolutePath());
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
