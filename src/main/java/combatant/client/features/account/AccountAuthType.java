/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.account;

public enum AccountAuthType {
    OFFLINE("offline"),
    MICROSOFT("microsoft");

    private final String id;

    AccountAuthType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static AccountAuthType byId(String raw) {
        if (raw == null || raw.isBlank()) {
            return OFFLINE;
        }
        for (AccountAuthType type : values()) {
            if (type.id.equalsIgnoreCase(raw.trim()) || type.name().equalsIgnoreCase(raw.trim())) {
                return type;
            }
        }
        return OFFLINE;
    }
}
