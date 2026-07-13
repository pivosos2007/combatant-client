/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class ConfigProfileService {
    public static final ConfigProfileService INSTANCE = new ConfigProfileService();

    private final ConfigProfileStorage storage = new ConfigProfileStorage();
    private final ConfigProfileCollector collector = new ConfigProfileCollector();
    private final ConfigProfileApplier applier = new ConfigProfileApplier();
    private final ConfigProfileDiffEngine diffEngine = new ConfigProfileDiffEngine();

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
        return storage.rename(type, idOrName, newName);
    }

    public boolean delete(ConfigProfileType type, String idOrName) throws IOException {
        return storage.delete(type, idOrName);
    }

    public ConfigProfileStorage.ImportResult importProfileFile(Path source, boolean moveSource) throws IOException {
        return storage.importProfileFile(source, moveSource);
    }

    private ConfigProfileMeta find(ConfigProfileType type, String name) {
        String id = ConfigProfileStorage.sanitizeFileName(name);
        for (ConfigProfileMeta meta : storage.list(type)) {
            if (meta.getId().equals(id)) return meta;
        }
        return null;
    }
}
