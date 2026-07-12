/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;

/**
 * Fired when the server updates tab-list header/footer text.
 */
public class PvpTabEvent extends Event {
    public final String header;
    public final String footer;
    public final long timeMs;

    public PvpTabEvent(String header, String footer) {
        this(header, footer, System.currentTimeMillis());
    }

    public PvpTabEvent(String header, String footer, long timeMs) {
        this.header = header == null ? "" : header;
        this.footer = footer == null ? "" : footer;
        this.timeMs = timeMs;
    }

    public String combinedText() {
        if (header.isBlank()) return footer;
        if (footer.isBlank()) return header;
        return header + "\n" + footer;
    }
}
