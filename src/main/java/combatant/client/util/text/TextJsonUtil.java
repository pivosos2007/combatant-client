/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;

public enum TextJsonUtil {
    ;

    private static volatile HolderLookup.Provider cachedLookup;
    private static volatile DynamicOps<JsonElement> cachedOps;

    private static HolderLookup.Provider lookup() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        ClientPacketListener net = mc.getConnection();
        if (net != null) return net.registryAccess();
        if (mc.level != null) return mc.level.registryAccess();
        return null;
    }

    private static DynamicOps<JsonElement> ops() {
        HolderLookup.Provider lookup = lookup();
        if (lookup != null) {
            DynamicOps<JsonElement> cached = cachedOps;
            if (cached != null && cachedLookup == lookup) {
                return cached;
            }
            synchronized (TextJsonUtil.class) {
                if (cachedOps == null || cachedLookup != lookup) {
                    cachedLookup = lookup;
                    cachedOps = RegistryOps.create(JsonOps.INSTANCE, lookup);
                }
                return cachedOps;
            }
        }
        return JsonOps.INSTANCE;
    }

    public static Component fromJson(String json) {
        if (json == null) return null;
        try {
            JsonElement el = JsonParser.parseString(json);
            return ComponentSerialization.CODEC.parse(ops(), el).result().orElse(Component.literal(json));
        } catch (Throwable ignored) {
        }
        return Component.literal(json);
    }

    public static String toJson(Component text) {
        if (text == null) return "";
        try {
            JsonElement el = ComponentSerialization.CODEC.encodeStart(ops(), text).result().orElse(null);
            if (el != null) return el.toString();
        } catch (Throwable ignored) {
        }
        return text.getString();
    }
}
