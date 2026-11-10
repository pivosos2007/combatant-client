/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat;

import java.util.List;

/** Keeps message grouping independent so viewport accounting follows individual messages. */
final class BetterChatMessageGrouping {
    private BetterChatMessageGrouping() {
    }

    static int[] groupIds(List<ChatLine> messages) {
        int size = messages == null ? 0 : messages.size();
        int[] groups = new int[size];
        for (int i = 0; i < size; i++) {
            groups[i] = i;
        }
        return groups;
    }
}
