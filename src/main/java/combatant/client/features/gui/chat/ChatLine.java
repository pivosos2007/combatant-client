/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat;

import net.minecraft.network.chat.Component;

public record ChatLine(Component text, long timestampMs) {
    public float ageSeconds() {
        return (System.currentTimeMillis() - timestampMs) / 1000f;
    }
}
