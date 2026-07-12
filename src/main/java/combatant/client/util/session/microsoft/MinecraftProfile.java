/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.session.microsoft;

import com.google.gson.JsonObject;

import java.util.UUID;
import java.util.regex.Pattern;

record MinecraftProfile(UUID uuid, String name) {
    private static final Pattern DASHLESS_UUID = Pattern.compile("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})");

    static MinecraftProfile fromJson(JsonObject json) {
        try {
            String id = MicrosoftJson.string(json, "id");
            if (!DASHLESS_UUID.matcher(id).matches()) {
                throw new IllegalStateException("Invalid Minecraft profile UUID: " + id);
            }
            String dashed = DASHLESS_UUID.matcher(id).replaceAll("$1-$2-$3-$4-$5");
            return new MinecraftProfile(UUID.fromString(dashed), MicrosoftJson.string(json, "name"));
        } catch (Throwable t) {
            throw new MicrosoftAuthException("Unable to parse Minecraft profile response", t);
        }
    }
}
