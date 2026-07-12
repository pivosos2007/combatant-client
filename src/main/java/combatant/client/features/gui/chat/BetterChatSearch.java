/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal search state & async search runner for BetterChat, styled like ClickGuiSearch.
 * Rendering handled elsewhere; this class only manages input text and results.
 */
public enum BetterChatSearch {
    ;
    private static final AtomicLong REQUEST = new AtomicLong(0);
    private static final int MAX_QUERY_CHARS = 256;
    private static boolean active = false;
    private static String text = "";
    private static volatile boolean busy = false;
    private static volatile List<ChatLine> results = null;

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean v) {
        active = v;
    }

    public static void deactivate() {
        active = false;
        reset();
    }

    public static String getText() {
        return text;
    }

    public static void setText(String value) {
        if (value == null || value.isEmpty()) {
            text = "";
            results = null;
            busy = false;
            return;
        }
        String cleaned = value.replace("\r", " ").replace("\n", " ");
        text = cleaned.length() > MAX_QUERY_CHARS ? cleaned.substring(0, MAX_QUERY_CHARS) : cleaned;
    }

    public static boolean hasQuery() {
        return !text.isEmpty();
    }

    public static boolean isBusy() {
        return busy;
    }

    public static List<ChatLine> results() {
        return results;
    }

    public static void append(char c) {
        if (c < 32) return;
        if (text.length() >= MAX_QUERY_CHARS) return;
        text += c;
    }

    public static void append(String value) {
        if (value == null || value.isEmpty()) return;
        String cleaned = value.replace("\r", " ").replace("\n", " ");
        if (cleaned.isEmpty()) return;
        int room = MAX_QUERY_CHARS - text.length();
        if (room <= 0) return;
        text += cleaned.length() > room ? cleaned.substring(0, room) : cleaned;
    }

    public static void clearText() {
        text = "";
        results = null;
        busy = false;
    }

    public static void backspace() {
        if (text.isEmpty()) return;
        text = text.substring(0, text.length() - 1);
    }

    public static void reset() {
        text = "";
        busy = false;
        results = null;
    }

    /**
     * Start async search across full store.
     */
    public static void startSearch(BetterChatStore store) {
        if (store == null) {
            busy = false;
            return;
        }
        if (text.isEmpty()) {
            busy = false;
            results = null;
            return;
        }
        final long req = REQUEST.incrementAndGet();
        busy = true;
        String needle = text.toLowerCase(Locale.ROOT);
        CompletableFuture.runAsync(() -> {
            List<ChatLine> snap = store.snapshot();
            List<ChatLine> out = new ArrayList<>(snap.size());
            for (ChatLine line : snap) {
                if (line.text().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                    out.add(line);
                }
            }
            if (REQUEST.get() == req) {
                results = out;
                busy = false;
            }
        });
    }
}
