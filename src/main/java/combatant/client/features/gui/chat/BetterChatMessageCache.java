/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.chat;

import combatant.client.features.gui.chat.rich.BetterChatMessage;

import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Function;

/** Weak, message-owned caches. Old chat lines disappear here when their store drops them. */
final class BetterChatMessageCache {
    private final WeakHashMap<ChatLine, BetterChatMessageLayout.CachedMessageLayout> layouts = new WeakHashMap<>();
    private final WeakHashMap<ChatLine, List<BetterChatMessageLayout.Segment>> segments = new WeakHashMap<>();

    synchronized BetterChatMessageLayout.CachedMessageLayout layout(ChatLine line) {
        return layouts.get(line);
    }

    synchronized void putLayout(ChatLine line, BetterChatMessageLayout.CachedMessageLayout layout) {
        if (line != null && layout != null) layouts.put(line, layout);
    }

    synchronized List<BetterChatMessageLayout.Segment> segments(
            ChatLine line,
            Function<BetterChatMessage, List<BetterChatMessageLayout.Segment>> factory) {
        if (line == null) return List.of();
        return segments.computeIfAbsent(line, ignored -> List.copyOf(factory.apply(line.message())));
    }

    synchronized void invalidateLayouts() {
        layouts.clear();
    }

    synchronized void clear() {
        layouts.clear();
        segments.clear();
    }
}
