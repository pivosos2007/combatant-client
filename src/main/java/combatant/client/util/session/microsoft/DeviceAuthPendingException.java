/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.session.microsoft;

public final class DeviceAuthPendingException extends RuntimeException {
    public DeviceAuthPendingException(String message) {
        super(message);
    }

    public DeviceAuthPendingException(String message, Throwable cause) {
        super(message, cause);
    }
}
