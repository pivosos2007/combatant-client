/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on In-Game Account Switcher
 * (https://github.com/The-Fireplace-Minecraft-Mods/In-Game-Account-Switcher).
 * Copyright (c) 2015-2022 The_Fireplace and 2021-2026 VidTu.
 *
 * Original portions remain under LGPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.session.microsoft;

import com.google.gson.JsonObject;

import java.net.URI;
import java.time.Duration;

/**
 * Microsoft device-code response.
 * Backend flow is derived from In-Game Account Switcher (LGPL-3.0-or-later),
 * Copyright (C) 2015-2022 The_Fireplace, Copyright (C) 2021-2026 VidTu.
 */
public record MicrosoftDeviceCode(String deviceCode,
                                  String userCode,
                                  URI verificationUri,
                                  Duration expiresIn,
                                  Duration interval) {

    static MicrosoftDeviceCode fromJson(JsonObject json) {
        try {
            String device = MicrosoftJson.string(json, "device_code");
            String user = MicrosoftJson.string(json, "user_code");
            URI uri = new URI(MicrosoftJson.string(json, "verification_uri")).parseServerAuthority();
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalStateException("Invalid verification URI scheme: " + uri);
            }
            Duration expires = Duration.ofSeconds(MicrosoftJson.number(json, "expires_in"));
            Duration poll = Duration.ofSeconds(MicrosoftJson.number(json, "interval"));
            if (expires.isZero() || expires.isNegative()) {
                throw new IllegalStateException("Invalid device-code expiration: " + expires);
            }
            if (poll.isZero() || poll.isNegative() || poll.compareTo(expires) >= 0) {
                throw new IllegalStateException("Invalid device-code polling interval: " + poll);
            }
            return new MicrosoftDeviceCode(device, user, uri, expires, poll);
        } catch (Throwable t) {
            throw new MicrosoftAuthException("Unable to parse Microsoft device-code response", t);
        }
    }
}
