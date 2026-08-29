/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat;

import combatant.client.features.gui.chat.rich.BetterChatMessage;
import net.minecraft.network.chat.Component;
import combatant.client.features.gui.hud.draggable.impl.BetterChat;
import combatant.client.util.chat.ChatSpamHeuristics;

import java.util.ArrayList;
import java.util.List;

public final class BetterChatStore {

    private static final long STACK_WINDOW_MS = 30_000L;

    private final List<ChatLine> lines = new ArrayList<>();
    private long revision = 0L;

    public synchronized void add(Component text) {
        add(BetterChatMessage.text(text), System.currentTimeMillis(), true, 1);
    }

    public synchronized void add(Component text, long timestampMs) {
        add(BetterChatMessage.text(text), timestampMs, false, 1);
    }

    public synchronized boolean add(Component text, long timestampMs, boolean stackDuplicates) {
        return add(BetterChatMessage.text(text), timestampMs, stackDuplicates, 1);
    }

    public synchronized boolean add(Component text, long timestampMs, boolean stackDuplicates, int repeatCount) {
        return add(BetterChatMessage.text(text), timestampMs, stackDuplicates, repeatCount);
    }

    public synchronized void add(BetterChatMessage message) {
        add(message, System.currentTimeMillis(), true, 1);
    }

    public synchronized boolean add(BetterChatMessage message, long timestampMs, boolean stackDuplicates) {
        return add(message, timestampMs, stackDuplicates, 1);
    }

    public synchronized boolean add(BetterChatMessage message, long timestampMs, boolean stackDuplicates, int repeatCount) {
        BetterChatMessage safeMessage = message == null ? BetterChatMessage.empty() : message;
        int safeRepeatCount = Math.max(1, repeatCount);
        if (stackDuplicates && safeRepeatCount == 1 && !lines.isEmpty()) {
            int lastIndex = lines.size() - 1;
            ChatLine previous = lines.get(lastIndex);
            long delta = Math.max(0L, timestampMs - previous.timestampMs());
            boolean same = previous.rawMessage().isTextOnly() && safeMessage.isTextOnly()
                    ? ChatSpamHeuristics.sameMessage(previous.rawText(), safeMessage.accessibleComponent())
                    : previous.rawMessage().semanticallyEquals(safeMessage);
            if (delta <= STACK_WINDOW_MS && same) {
                lines.set(lastIndex, previous.repeated(timestampMs));
                revision++;
                return true;
            }
        }

        lines.add(new ChatLine(safeMessage, timestampMs, safeRepeatCount));
        trimToLimit();
        revision++;
        return false;
    }

    public synchronized void clear() {
        lines.clear();
        revision++;
    }

    public synchronized List<ChatLine> tail(int count) {
        int start = Math.max(0, lines.size() - count);
        return new ArrayList<>(lines.subList(start, lines.size()));
    }

    public synchronized int size() {
        return lines.size();
    }

    public synchronized List<ChatLine> snapshot() {
        return new ArrayList<>(lines);
    }

    public synchronized long revision() {
        return revision;
    }

    private void trimToLimit() {
        int limit = currentLimit();
        if (limit <= 0) limit = 1;
        if (lines.size() <= limit) return;
        int overflow = lines.size() - limit;
        int drop = Math.min(lines.size(), Math.max(overflow, 100));
        lines.subList(0, drop).clear();
    }

    private int currentLimit() {
        BetterChat cfg = BetterChat.get();
        if (cfg == null) return 32000;
        if (!cfg.historyEnabled()) return 100;
        return Math.max(1, cfg.historyLimit());
    }
}
