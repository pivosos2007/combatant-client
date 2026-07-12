/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.session.microsoft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

final class MicrosoftJson {
    static final Gson GSON = new Gson();

    private MicrosoftJson() {
    }

    static String string(JsonObject json, String key) {
        try {
            return json.get(key).getAsString();
        } catch (Throwable t) {
            throw new JsonParseException("Expected string '" + key + "': " + json, t);
        }
    }

    static long number(JsonObject json, String key) {
        try {
            return json.get(key).getAsLong();
        } catch (Throwable t) {
            throw new JsonParseException("Expected number '" + key + "': " + json, t);
        }
    }

    static JsonObject object(JsonObject json, String key) {
        try {
            return json.get(key).getAsJsonObject();
        } catch (Throwable t) {
            throw new JsonParseException("Expected object '" + key + "': " + json, t);
        }
    }

    static JsonArray array(JsonObject json, String key) {
        try {
            return json.get(key).getAsJsonArray();
        } catch (Throwable t) {
            throw new JsonParseException("Expected array '" + key + "': " + json, t);
        }
    }
}
