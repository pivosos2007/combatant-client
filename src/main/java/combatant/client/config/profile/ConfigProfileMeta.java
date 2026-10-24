/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;

/**
 * @param combatantVersion Combatant version that saved the profile; empty for profiles written before
 *                         the field existed (no Minecraft version is recorded)
 */
public record ConfigProfileMeta(
        String id,
        String name,
        ConfigProfileType type,
        String author,
        long createdAt,
        long updatedAt,
        int version,
        String combatantVersion
) {
    public ConfigProfileMeta {
        if (id == null || id.isBlank()) id = ConfigProfileStorage.sanitizeFileName(name);
        if (name == null || name.isBlank()) name = id;
        if (type == null) type = ConfigProfileType.MODULES;
        if (author == null || author.isBlank()) author = "Unknown";
        if (version <= 0) version = ConfigProfileBinaryCodec.VERSION;
        if (combatantVersion == null) combatantVersion = "";
    }

    public ConfigProfileMeta(String id, String name, ConfigProfileType type, String author,
                             long createdAt, long updatedAt, int version) {
        this(id, name, type, author, createdAt, updatedAt, version, "");
    }

    public ConfigProfileMeta withUpdatedAt(long timestamp) {
        return new ConfigProfileMeta(id, name, type, author, createdAt, timestamp, version, combatantVersion);
    }

    public ConfigProfileMeta withCombatantVersion(String nextCombatantVersion) {
        return new ConfigProfileMeta(id, name, type, author, createdAt, updatedAt, version, nextCombatantVersion);
    }

    public String getId() {
        return id;
    }

    public ConfigProfileMeta withName(String nextName, long timestamp) {
        String nextId = ConfigProfileStorage.sanitizeFileName(nextName);
        return new ConfigProfileMeta(nextId, nextName, type, author, createdAt, timestamp, version, combatantVersion);
    }
}
