/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.chat;

import combatant.client.features.gui.chat.BetterChatMessageLayout.Segment;
import combatant.client.features.gui.chat.rich.BetterChatMessage;
import combatant.client.features.gui.chat.rich.ItemNode;
import combatant.client.features.gui.chat.rich.TextNode;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Converts styled/rich chat nodes into a stable sequence consumed by the glyph layout. */
final class BetterChatRichMessageFlattener {
    private BetterChatRichMessageFlattener() { }

    static List<Segment> flatten(BetterChatMessage message) {
        List<Segment> segments = new ArrayList<>();
        BetterChatMessage safe = message == null ? BetterChatMessage.empty() : message;
        for (var node : safe.nodes()) {
            if (node instanceof TextNode text) {
                appendTextNode(text, segments);
            } else if (node instanceof ItemNode item) {
                ItemStack stack = item.stack();
                if (!stack.isEmpty()) segments.add(Segment.richItem(item.plainText(), stack));
            }
        }
        return segments;
    }

    private static void appendTextNode(TextNode text, List<Segment> segments) {
        String[] previousItemKey = {null};
        text.component().visit((style, value) -> {
            if (value == null || value.isEmpty()) return Optional.empty();
            Style safeStyle = style == null ? Style.EMPTY : style;
            ItemStack hoveredItem = itemFromStyle(safeStyle);
            if (hoveredItem.isEmpty()) {
                previousItemKey[0] = null;
                segments.add(Segment.text(value, safeStyle));
                return Optional.empty();
            }

            String itemKey = BetterChatStoreManager.hoverItemKey(hoveredItem);
            if (!itemKey.equals(previousItemKey[0])) {
                int iconOffset = value.codePointAt(0) == '[' ? Character.charCount(value.codePointAt(0)) : 0;
                if (iconOffset > 0) segments.add(Segment.text(value.substring(0, iconOffset), safeStyle));
                segments.add(Segment.decorativeItem(hoveredItem, safeStyle));
                if (iconOffset < value.length()) segments.add(Segment.text(value.substring(iconOffset), safeStyle));
            } else {
                segments.add(Segment.text(value, safeStyle));
            }
            previousItemKey[0] = itemKey;
            return Optional.empty();
        }, Style.EMPTY);
    }

    private static ItemStack itemFromStyle(Style style) {
        HoverEvent hover = style == null ? null : style.getHoverEvent();
        if (!(hover instanceof HoverEvent.ShowItem(net.minecraft.world.item.ItemStackTemplate template))) {
            return ItemStack.EMPTY;
        }
        ItemStack source = template.create();
        if (source == null || source.isEmpty()) return ItemStack.EMPTY;
        BetterChatHoverCache cache = BetterChatStoreManager.getActiveCache();
        if (cache == null) return source.copy();
        ItemStack cached = cache.getItem(BetterChatStoreManager.hoverItemKey(source));
        return cached.isEmpty() ? source.copy() : cached;
    }
}
