/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.session;

import java.util.UUID;

public record MicrosoftSessionResult(String name, UUID uuid, String accessToken, String refreshToken) {

    public MicrosoftSessionResult {
        name = name == null ? "" : name.trim();
        accessToken = accessToken == null ? "" : accessToken;
        refreshToken = refreshToken == null ? "" : refreshToken;
    }

    public SessionLoginData toLoginData() {
        return new SessionLoginData(name, uuid, accessToken, true);
    }

    @Override
    public String toString() {
        return "MicrosoftSessionResult{" +
                "name='" + name + '\'' +
                ", uuid=" + uuid +
                ", accessToken=[TOKEN]" +
                ", refreshToken=[TOKEN]" +
                '}';
    }
}
