/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.color;

public enum ColorBlendMode {
    ORIGINAL("Original"),
    SOFT("Soft"),
    ATMOSPHERIC("Atmospheric"),
    CHROMATIC("Chromatic"),
    ESSENCE("Essence");

    private final String id;

    ColorBlendMode(String id) {
        this.id = id;
    }

    public static ColorBlendMode fromId(String id) {
        if (id == null) {
            return ORIGINAL;
        }
        for (ColorBlendMode mode : values()) {
            if (mode.id.equalsIgnoreCase(id) || mode.name().equalsIgnoreCase(id)) {
                return mode;
            }
        }
        return ORIGINAL;
    }

    public String id() {
        return id;
    }
}
