/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public enum ConfigProfileDateFormatter {
    ;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public static String created(ConfigProfileMeta meta) {
        return DATE.format(toDate(meta.createdAt()));
    }

    public static String updated(ConfigProfileMeta meta) {
        LocalDate created = toDate(meta.createdAt());
        LocalDate updated = toDate(meta.updatedAt());
        String text = DATE.format(updated);
        if (created.equals(updated)) {
            text += " (" + TIME.format(Instant.ofEpochMilli(meta.updatedAt()).atZone(ZoneId.systemDefault())) + ")";
        }
        return text;
    }

    private static LocalDate toDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
