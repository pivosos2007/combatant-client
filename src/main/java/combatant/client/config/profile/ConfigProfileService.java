/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import combatant.client.features.module.Module;

public final class ConfigProfileService {
    public static final ConfigProfileService INSTANCE = new ConfigProfileService();

    private final ConfigProfileStorage storage = new ConfigProfileStorage();
    private final ConfigProfileCollector collector = new ConfigProfileCollector();
    private final ConfigProfileApplier applier = new ConfigProfileApplier();
    private final ConfigProfileDiffEngine diffEngine = new ConfigProfileDiffEngine();
    private final ConfigShareCodec shareCodec = new ConfigShareCodec();

    private ConfigProfileService() {
    }

    public ConfigProfileStorage storage() {
        return storage;
    }

    public List<ConfigProfileMeta> list(ConfigProfileType type) {
        return storage.list(type);
    }

    public ConfigProfileSnapshot saveCurrent(ConfigProfileType type, String name) throws IOException {
        ConfigProfileMeta old = find(type, name);
        ConfigProfileSnapshot snapshot = collector.collect(type, name);
        if (old != null) {
            ConfigProfileMeta meta = new ConfigProfileMeta(
                    old.getId(),
                    old.name(),
                    old.type(),
                    old.author(),
                    old.createdAt(),
                    System.currentTimeMillis(),
                    ConfigProfileBinaryCodec.VERSION
            );
            snapshot = collector.collectWithMeta(meta);
        }
        storage.save(snapshot);
        return snapshot;
    }

    public ConfigProfileSnapshot overwriteCurrent(ConfigProfileMeta target) throws IOException {
        if (target == null) throw new IllegalArgumentException("profile meta is null");
        ConfigProfileMeta meta = target.withUpdatedAt(System.currentTimeMillis());
        ConfigProfileSnapshot snapshot = collector.collectWithMeta(meta);
        storage.save(snapshot);
        return snapshot;
    }

    public ConfigProfileSnapshot read(ConfigProfileType type, String idOrName) throws IOException {
        return storage.read(type, idOrName);
    }

    public ConfigProfileApplier.ApplyResult apply(ConfigProfileType type, String idOrName) throws IOException {
        return applier.apply(storage.read(type, idOrName));
    }

    public ConfigProfileDiff diffWithCurrent(ConfigProfileType type, String idOrName) throws IOException {
        ConfigProfileSnapshot selected = storage.read(type, idOrName);
        ConfigProfileSnapshot current = collector.collectWithMeta(new ConfigProfileMeta(
                "__current__",
                "Current",
                type,
                "Runtime",
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                ConfigProfileBinaryCodec.VERSION
        ));
        return diffEngine.compare(current, selected);
    }

    public ConfigProfileSnapshot rename(ConfigProfileType type, String idOrName, String newName) throws IOException {
        ConfigProfileSnapshot renamed = storage.rename(type, idOrName, newName);
        ConfigServerBindings.INSTANCE.renamed(type, ConfigProfileStorage.sanitizeFileName(idOrName), renamed.meta().getId());
        return renamed;
    }

    public boolean delete(ConfigProfileType type, String idOrName) throws IOException {
        boolean deleted = storage.delete(type, idOrName);
        if (deleted) ConfigServerBindings.INSTANCE.removed(type, ConfigProfileStorage.sanitizeFileName(idOrName));
        return deleted;
    }

    public ConfigProfileStorage.ImportResult importProfileFile(Path source, boolean moveSource) throws IOException {
        return storage.importProfileFile(source, moveSource);
    }

    // ---- share codes ----

    public String profileCode(ConfigProfileType type, String idOrName) throws IOException {
        return shareCodec.encodeProfile(storage.read(type, idOrName));
    }

    public String moduleCode(Module module) throws IOException {
        return shareCodec.encodeModule(collector.collectModule(module), collector.authorName());
    }

    public ConfigShareCodec.Decoded decodeShareCode(String text) throws ConfigShareCodec.ShareCodeException {
        return shareCodec.decode(text);
    }

    public ConfigProfileApplier.Inspection inspect(ConfigProfileSnapshot snapshot) {
        return applier.inspect(snapshot);
    }

    public ConfigProfileApplier.ApplyResult applySnapshot(ConfigProfileSnapshot snapshot) {
        return applier.apply(snapshot);
    }

    /**
     * Saves a decoded snapshot as a new profile. An existing profile with the same id is never replaced:
     * the imported one gets a numbered name instead.
     */
    public ConfigProfileMeta importSnapshot(ConfigProfileSnapshot snapshot) throws IOException {
        ConfigProfileType type = snapshot.meta().type();
        String baseName = snapshot.meta().name();
        String name = baseName;
        for (int n = 2; storage.exists(type, name); n++) {
            if (n > 999) throw new IOException("too many profiles named " + baseName);
            name = baseName + " (" + n + ")";
        }
        long now = System.currentTimeMillis();
        ConfigProfileMeta old = snapshot.meta();
        ConfigProfileMeta meta = new ConfigProfileMeta(ConfigProfileStorage.sanitizeFileName(name), name, type,
                old.author(), old.createdAt(), now, ConfigProfileBinaryCodec.VERSION, old.combatantVersion());
        ConfigProfileSnapshot saved = new ConfigProfileSnapshot(meta, snapshot.entries());
        storage.save(saved);
        return saved.meta();
    }

    /** Copies the stored {@code .cbcfg} bytes to {@code destination}, adding the extension when missing. */
    public Path exportProfileFile(ConfigProfileType type, String idOrName, Path destination) throws IOException {
        if (destination == null) throw new IllegalArgumentException("destination is null");
        Path source = storage.file(type, idOrName);
        if (!Files.isRegularFile(source)) throw new IOException("profile does not exist: " + idOrName);
        String fileName = destination.getFileName() == null ? "" : destination.getFileName().toString();
        Path target = fileName.toLowerCase(java.util.Locale.ROOT).endsWith(ConfigProfileStorage.EXTENSION)
                ? destination
                : destination.resolveSibling(fileName + ConfigProfileStorage.EXTENSION);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private ConfigProfileMeta find(ConfigProfileType type, String name) {
        String id = ConfigProfileStorage.sanitizeFileName(name);
        for (ConfigProfileMeta meta : storage.list(type)) {
            if (meta.getId().equals(id)) return meta;
        }
        return null;
    }
}
