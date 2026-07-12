/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * This file belongs to Combatant's MediaPlayerInfo integration, based on
 * https://github.com/Redstonecrafter0/MediaPlayerInfo.
 * Copyright (c) Redstonecrafter0 and contributors.
 *
 * Licensed under the GNU Affero General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.media;

import java.util.Locale;

/**
 * Modified Java port for Combatant by combatant.client.
 * Based on Redstonecrafter0/MediaPlayerInfo.
 */
public enum RepeatMode {
    OFF,
    ONE,
    ALL,
    UNKNOWN;

    public static RepeatMode fromMpris(String value) {
        if (value == null) return UNKNOWN;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "none" -> OFF;
            case "track" -> ONE;
            case "playlist" -> ALL;
            default -> UNKNOWN;
        };
    }
}
