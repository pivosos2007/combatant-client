/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat.rich;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import combatant.client.util.text.TextJsonUtil;
import combatant.client.features.gui.chat.actions.ChatMessageActions;

import java.util.ArrayList;
import java.util.List;

/** Component-aware persistence for rich message nodes. */
public enum BetterChatMessageJson {
    ;

    private static volatile HolderLookup.Provider cachedLookup;
    private static volatile RegistryOps<JsonElement> cachedOps;

    public static JsonArray encode(BetterChatMessage message) {
        JsonArray nodes = new JsonArray();
        BetterChatMessage safe = message == null ? BetterChatMessage.empty() : message;
        for (BetterChatNode node : safe.nodes()) {
            JsonObject encoded = new JsonObject();
            if (node instanceof TextNode text) {
                encoded.addProperty("type", "text");
                encoded.addProperty("component", TextJsonUtil.toJson(ChatMessageActions.persistentCopy(text.component())));
            } else if (node instanceof ItemNode item) {
                encoded.addProperty("type", "item");
                ItemStack stack = item.stack();
                encodeItem(stack, encoded);
            }
            if (encoded.has("type")) nodes.add(encoded);
        }
        return nodes;
    }

    public static BetterChatMessage decode(JsonArray encoded) {
        if (encoded == null || encoded.isEmpty()) return BetterChatMessage.empty();
        List<BetterChatNode> nodes = new ArrayList<>(encoded.size());
        for (JsonElement element : encoded) {
            if (!element.isJsonObject()) continue;
            JsonObject node = element.getAsJsonObject();
            String type = node.has("type") ? node.get("type").getAsString() : "";
            switch (type) {
                case "text" -> {
                    if (!node.has("component")) continue;
                    Component component = TextJsonUtil.fromJson(node.get("component").getAsString());
                    if (component != null) nodes.add(new TextNode(component));
                }
                case "item" -> {
                    ItemStack stack = decodeItem(node);
                    if (!stack.isEmpty()) nodes.add(new ItemNode(stack));
                }
                default -> {
                }
            }
        }
        return new BetterChatMessage(nodes);
    }

    private static void encodeItem(ItemStack stack, JsonObject output) {
        if (stack == null || stack.isEmpty()) return;
        output.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        output.addProperty("count", stack.getCount());
        RegistryOps<JsonElement> ops = ops();
        if (ops == null) return;
        ItemStack.CODEC.encodeStart(ops, stack.copy()).result().ifPresent(json -> output.add("stack", json));
    }

    private static ItemStack decodeItem(JsonObject input) {
        if (input.has("stack")) {
            RegistryOps<JsonElement> ops = ops();
            if (ops != null) {
                var decoded = ItemStack.CODEC.parse(ops, input.get("stack")).result();
                if (decoded.isPresent()) return decoded.get();
            }
        }
        if (!input.has("id")) return ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(input.get("id").getAsString());
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        int count = input.has("count") ? Math.max(1, input.get("count").getAsInt()) : 1;
        return new ItemStack(item, count);
    }

    private static RegistryOps<JsonElement> ops() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        ClientPacketListener connection = mc.getConnection();
        HolderLookup.Provider lookup = connection != null
                ? connection.registryAccess()
                : mc.level != null ? mc.level.registryAccess() : null;
        if (lookup == null) return null;

        RegistryOps<JsonElement> current = cachedOps;
        if (current != null && cachedLookup == lookup) return current;
        synchronized (BetterChatMessageJson.class) {
            if (cachedOps == null || cachedLookup != lookup) {
                cachedLookup = lookup;
                cachedOps = RegistryOps.create(JsonOps.INSTANCE, lookup);
            }
            return cachedOps;
        }
    }
}
