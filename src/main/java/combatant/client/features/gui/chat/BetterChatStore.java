/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat;

import net.minecraft.network.chat.Component;
import combatant.client.features.gui.hud.draggable.impl.BetterChat;

import java.util.ArrayList;
import java.util.List;

public final class BetterChatStore {

    private final List<ChatLine> lines = new ArrayList<>();
    private long revision = 0L;

    public synchronized void add(Component text) {
        add(text, System.currentTimeMillis());
    }

    public synchronized void add(Component text, long timestampMs) {
        lines.add(new ChatLine(text, timestampMs));
        int limit = currentLimit();
        if (limit <= 0) limit = 1; // safeguard
        if (lines.size() > limit) {
            int overflow = lines.size() - limit;
            int drop = Math.min(lines.size(), Math.max(overflow, 100));
            lines.subList(0, drop).clear();
        }
        revision++;
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

    private int currentLimit() {
        BetterChat cfg = BetterChat.get();
        if (cfg == null) return 32000;
        if (!cfg.historyEnabled()) return 100; // keep near-vanilla limit
        return Math.max(1, cfg.historyLimit());
    }
}


