/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;

import java.util.Locale;

public enum ConfigProfileType {
    MODULES((byte) 0, "modules"),
    HUD((byte) 1, "hud"),
    THEMES((byte) 2, "themes");

    private final byte wireId;
    private final String folderName;

    ConfigProfileType(byte wireId, String folderName) {
        this.wireId = wireId;
        this.folderName = folderName;
    }

    public static ConfigProfileType fromWireId(int id) {
        for (ConfigProfileType type : values()) {
            if ((type.wireId & 0xFF) == id) return type;
        }
        throw new IllegalArgumentException("Unknown profile type id: " + id);
    }

    public static ConfigProfileType fromName(String name) {
        if (name == null) return MODULES;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (ConfigProfileType type : values()) {
            if (type.name().toLowerCase(Locale.ROOT).equals(normalized) || type.folderName.equals(normalized)) {
                return type;
            }
        }
        return MODULES;
    }

    public byte wireId() {
        return wireId;
    }

    public String folderName() {
        return folderName;
    }
}
