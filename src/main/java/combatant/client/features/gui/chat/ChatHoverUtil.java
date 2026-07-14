/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat;

import combatant.client.util.item.TopEnchantUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import combatant.client.util.item.IllegalItemUtil;
import combatant.client.util.item.RarityColorUtil;
import combatant.client.util.logging.DebugLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hover payload helpers + cache fallbacks.
 */
public enum ChatHoverUtil {
    ;

    public static HoverTip fromHover(HoverEvent hover, Minecraft mc) {
        switch (hover) {
            case null -> {
                return null;
            }
            case HoverEvent.ShowText(Component value) -> {
                String[] split = value.getString().split("\n");
                List<ColoredLine> lines = new ArrayList<>(split.length);
                for (String s : split) lines.add(new ColoredLine(s, 0));
                return new HoverTip(lines, null, false, ItemStack.EMPTY);
            }
            case HoverEvent.ShowItem(net.minecraft.world.item.ItemStackTemplate item) -> {
                ItemStack stack = item.create();
                BetterChatHoverCache cache = BetterChatStoreManager.getActiveCache();
                boolean cachedFlag = false;
                if (cache != null) {
                    String key = BetterChatStoreManager.hoverItemKey(stack);
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    boolean cacheLoaded = cache.wasLoadedFromDisk();
                    ItemStack cached = cache.getItemByKeyOrId(key, id);
                    if (!cached.isEmpty()) {
                        stack = cached;
                        cachedFlag = cacheLoaded && cache.wasItemKeyLoaded(key);
                        DebugLog.info("[BetterChat][Hover] cache hit for item %s", id);
                    } else {
                        BetterChatHoverCache.ItemLookup lookup = cache.findItemByDisplayNameWithMeta(stack.getHoverName().getString());
                        if (!lookup.stack().isEmpty()) {
                            stack = lookup.stack();
                            cachedFlag = cacheLoaded && lookup.loadedFromFile();
                            DebugLog.info("[BetterChat][Hover] cache name-hit for item %s", stack.getHoverName().getString());
                        } else {
                            DebugLog.info("[BetterChat][Hover] cache miss for item %s", id);
                        }
                    }
                }
                return buildItemTip(stack, mc, cachedFlag);
            }
            case HoverEvent.ShowEntity(HoverEvent.EntityTooltipInfo entity) -> {
                BetterChatHoverCache cache = BetterChatStoreManager.getActiveCache();
                boolean cachedFlag = false;
                if (cache != null) {
                    String key = BetterChatStoreManager.hoverEntityKey(entity);
                    cachedFlag = cache.wasLoadedFromDisk() && cache.wasEntityKeyLoaded(key);
                }
                return buildEntityTip(entity, mc, cachedFlag);
            }
            default -> {
            }
        }
        return null;
    }

    public static HoverTip buildItemTip(ItemStack stack, Minecraft mc, boolean fromCache) {
        if (stack == null) return null;
        ItemStack copy = stack.copy();
        List<ColoredLine> lines = new ArrayList<>();
        String baseName = copy.getHoverName().getString();
        String name = "[" + baseName + "]";
        int count = copy.getCount();
        int rarityColor = argbFrom(RarityColorUtil.INSTANCE.getRarityColor(copy));
        int illegalColor = IllegalItemUtil.illegalColor();
        int topColor = TopEnchantUtil.topColor();
        boolean[] hasIllegal = new boolean[1];
        boolean[] hasTop = new boolean[1];
        Set<String> shownEnchantLabels = new HashSet<>();
        java.util.Map<String, Integer> labelIndex = new java.util.HashMap<>();

        IllegalItemUtil.collectEnchants(copy, (label, overMax) -> {
            int enchColor = overMax ? illegalColor : 0xFFFFFFFF;
            if (overMax) {
                hasIllegal[0] = true;
            }
            shownEnchantLabels.add(label);
            int idx = lines.size();
            lines.add(new ColoredLine(label, enchColor));
            labelIndex.put(label, idx);
        });
        TopEnchantUtil.collectTopEnchants(copy, (label, isTop) -> {
            if (!isTop) return;
            hasTop[0] = true;
            if (labelIndex.containsKey(label)) {
                int idx = labelIndex.get(label);
                ColoredLine current = lines.get(idx);
                if (current.color() != illegalColor) {
                    lines.set(idx, new ColoredLine(label, topColor));
                }
            } else {
                shownEnchantLabels.add(label);
                int idx = lines.size();
                lines.add(new ColoredLine(label, topColor));
                labelIndex.put(label, idx);
            }
        });

        int nameColor = hasIllegal[0] ? illegalColor : (hasTop[0] ? topColor : rarityColor);
        lines.addFirst(new ColoredLine(count > 1 ? name + " x" + count : name, nameColor));

        try {
            List<Component> vanilla = copy.getTooltipLines(Item.TooltipContext.EMPTY, mc.player, TooltipFlag.NORMAL);
            for (int i = 0; i < vanilla.size(); i++) {
                if (i == 0) continue;
                Component t = vanilla.get(i);
                String s = t.getString();
                int c = t.getStyle().getColor() != null ? 0xFF000000 | t.getStyle().getColor().getValue() : 0xFFFFFFFF;
                String trimmed = s.trim();
                if (!trimmed.isEmpty() && shownEnchantLabels.contains(trimmed)) {
                    continue;
                }
                if (hasIllegal[0] && c == 0xFFFFFFFF) c = illegalColor;
                lines.add(new ColoredLine(s, c));
            }
        } catch (Throwable ignored) {
        }

        lines.add(new ColoredLine("ID: " + BuiltInRegistries.ITEM.getKey(copy.getItem()), 0xFF888888));
        if (fromCache) {
            lines.add(new ColoredLine(I18n.get("better_chat.hover.cached"), 0xFFFFD700));
        }
        return new HoverTip(lines, null, fromCache, copy);
    }

    public static HoverTip buildEntityTip(HoverEvent.EntityTooltipInfo content, Minecraft mc, boolean fromCache) {
        if (content == null) return null;
        List<ColoredLine> lines = new ArrayList<>();
        content.name.ifPresent(t -> lines.add(new ColoredLine(t.getString(), 0)));
        lines.add(new ColoredLine("Сущность: " + content.type.getDescription().getString(), 0));
        lines.add(new ColoredLine("UUID: " + content.uuid, 0xFF888888));
        boolean online = false;
        if (mc != null && mc.player != null && mc.player.connection != null) {
            online = mc.player.connection.getOnlinePlayers().stream()
                    .anyMatch(e -> e.getProfile() != null && e.getProfile().id().equals(content.uuid));
        }
        if (online) {
            lines.add(new ColoredLine(I18n.get("better_chat.hover.online_player"), 0xFF8CF0FF));
        } else if (fromCache) {
            lines.add(new ColoredLine(I18n.get("better_chat.hover.cached"), 0xFFFFD700));
        }
        return new HoverTip(lines, content.uuid.toString(), fromCache, ItemStack.EMPTY);
    }

    /**
     * Quick heuristic tooltip by nickname from playerlist, used when hover data was not persisted with history.
     */
    public static HoverTip inferFromNick(String nick, Minecraft mc) {
        if (nick == null || nick.isEmpty() || mc == null || mc.player == null || mc.player.connection == null)
            return null;
        PlayerInfo entry = mc.player.connection.getOnlinePlayers()
                .stream()
                .filter(e -> e.getProfile() != null && nick.equalsIgnoreCase(e.getProfile().name()))
                .findFirst()
                .orElse(null);
        if (entry == null || entry.getProfile() == null) return null;
        String name = entry.getProfile().name();
        String uuid = entry.getProfile().id().toString();
        List<ColoredLine> lines = List.of(
                new ColoredLine(name, 0),
                new ColoredLine(I18n.get("better_chat.hover.online_player"), 0xFF8CF0FF),
                new ColoredLine("UUID: " + uuid, 0xFF888888)
        );
        return new HoverTip(lines, uuid, false, ItemStack.EMPTY);
    }

    /**
     * Fallback: find matching item/entity by display text ("[имя]" или "<<имя>>") or plain name.
     */
    @SuppressWarnings("unused")
    public static HoverTip inferFromDisplay(String rawWord, boolean looksLikeItem, Minecraft mc) {
        BetterChatHoverCache cache = BetterChatStoreManager.getActiveCache();
        if (cache == null) return null;
        boolean cacheLoaded = cache.wasLoadedFromDisk();

        String cleaned = rawWord.replaceAll("[\\[\\]<>«»]", "").trim();
        if (!cleaned.isEmpty()) {
            BetterChatHoverCache.ItemLookup lookup = cache.findItemByDisplayNameWithMeta(cleaned);
            if (!lookup.stack().isEmpty()) {
                return buildItemTip(lookup.stack(), mc, cacheLoaded && lookup.loadedFromFile());
            }
        }

        BetterChatHoverCache.EntityLookup entLookup = cache.findEntityByNameWithMeta(rawWord);
        if (entLookup.content() != null) {
            return buildEntityTip(entLookup.content(), mc, cacheLoaded && entLookup.loadedFromFile());
        }
        return null;
    }

    public static int argbFrom(float[] rgba) {
        if (rgba == null || rgba.length < 3) return 0xFFFFFFFF;
        float r = rgba[0], g = rgba[1], b = rgba[2];
        float a = rgba.length > 3 ? rgba[3] : 1f;
        int ai = (int) (a * 255) & 0xFF;
        int ri = (int) (r * 255) & 0xFF;
        int gi = (int) (g * 255) & 0xFF;
        int bi = (int) (b * 255) & 0xFF;
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    public record ColoredLine(String text, int color) {
    }

    public record HoverTip(List<ColoredLine> lines, String uuid, boolean cached, ItemStack item) {
        public boolean cached() {
            return cached;
        }
    }
}
