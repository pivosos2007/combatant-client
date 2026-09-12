/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.chat;

import combatant.client.features.command.CommandOutput;
import combatant.client.features.gui.chat.actions.ChatMessageActions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.WeakHashMap;

/** Merges uncacheable client diagnostics into the visible BetterChat tail without persisting them. */
final class BetterChatRuntimeMessages {
    private static final WeakHashMap<CommandOutput.MessageLine, ChatLine> CACHE = new WeakHashMap<>();

    private BetterChatRuntimeMessages() { }

    static synchronized List<ChatLine> merge(List<ChatLine> stored,
                                             List<CommandOutput.MessageLine> runtime,
                                             int limit) {
        if (runtime == null || runtime.isEmpty()) return stored;
        List<ChatLine> result = new ArrayList<>(stored.size() + runtime.size());
        result.addAll(stored);
        for (CommandOutput.MessageLine line : runtime) {
            if (line == null || line.text() == null) continue;
            result.add(CACHE.computeIfAbsent(line, key -> {
                ChatLine created = new ChatLine(key.text(), key.timestampMs());
                ChatMessageActions.copyBinding(key.text(), created.text());
                return created;
            }));
        }
        result.sort(Comparator.comparingLong(ChatLine::timestampMs));
        if (limit > 0 && result.size() > limit) {
            return new ArrayList<>(result.subList(result.size() - limit, result.size()));
        }
        return result;
    }

    static synchronized void clear() {
        CACHE.clear();
    }
}
