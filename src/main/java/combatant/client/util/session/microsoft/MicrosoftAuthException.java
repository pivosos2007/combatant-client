/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.session.microsoft;

public class MicrosoftAuthException extends RuntimeException {
    private final String key;

    public MicrosoftAuthException(String message) {
        this(message, null, "combatant.auth.microsoft.error");
    }

    public MicrosoftAuthException(String message, Throwable cause) {
        this(message, cause, "combatant.auth.microsoft.error");
    }

    public MicrosoftAuthException(String message, String key) {
        this(message, null, key);
    }

    public MicrosoftAuthException(String message, Throwable cause, String key) {
        super(message + (key == null || key.isBlank() ? "" : " (key: " + key + ")"), cause);
        this.key = key == null ? "combatant.auth.microsoft.error" : key;
    }

    public String key() {
        return key;
    }
}
