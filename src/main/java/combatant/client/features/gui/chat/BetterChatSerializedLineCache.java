/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.chat;

import combatant.client.features.gui.chat.actions.ChatMessageActions;
import combatant.client.util.text.TextJsonUtil;

import java.util.WeakHashMap;

/** Memoizes expensive component serialization without retaining trimmed chat lines. */
final class BetterChatSerializedLineCache {
    private static final WeakHashMap<ChatLine, String> CACHE = new WeakHashMap<>();

    private BetterChatSerializedLineCache() { }

    static synchronized String text(ChatLine line) {
        return CACHE.computeIfAbsent(line, value ->
                TextJsonUtil.toJson(ChatMessageActions.persistentCopy(value.rawText())));
    }

    static synchronized void clear() {
        CACHE.clear();
    }
}
