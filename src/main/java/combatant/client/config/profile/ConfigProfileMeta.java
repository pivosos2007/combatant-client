/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;

public record ConfigProfileMeta(
        String id,
        String name,
        ConfigProfileType type,
        String author,
        long createdAt,
        long updatedAt,
        int version
) {
    public ConfigProfileMeta {
        if (id == null || id.isBlank()) id = ConfigProfileStorage.sanitizeFileName(name);
        if (name == null || name.isBlank()) name = id;
        if (type == null) type = ConfigProfileType.MODULES;
        if (author == null || author.isBlank()) author = "Unknown";
        if (version <= 0) version = ConfigProfileBinaryCodec.VERSION;
    }

    public ConfigProfileMeta withUpdatedAt(long timestamp) {
        return new ConfigProfileMeta(id, name, type, author, createdAt, timestamp, version);
    }

    public String getId() {
        return id;
    }

    public ConfigProfileMeta withName(String nextName, long timestamp) {
        String nextId = ConfigProfileStorage.sanitizeFileName(nextName);
        return new ConfigProfileMeta(nextId, nextName, type, author, createdAt, timestamp, version);
    }
}
