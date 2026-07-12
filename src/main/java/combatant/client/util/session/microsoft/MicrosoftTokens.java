/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.session.microsoft;

import com.google.gson.JsonObject;

record MicrosoftTokens(String accessToken, String refreshToken) {
    static MicrosoftTokens fromJson(JsonObject json) {
        try {
            return new MicrosoftTokens(
                    MicrosoftJson.string(json, "access_token"),
                    MicrosoftJson.string(json, "refresh_token")
            );
        } catch (Throwable t) {
            throw new MicrosoftAuthException("Unable to parse Microsoft OAuth token response", t);
        }
    }
}
