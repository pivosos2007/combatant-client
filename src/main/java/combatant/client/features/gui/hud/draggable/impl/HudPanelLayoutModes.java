/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;

enum HudPanelLayoutModes {
    ;
    static final String SPLIT_HEADER = "Split Header";
    static final String UNIFIED_DIVIDER = "Unified Divider";

    static final double SCALE_DEFAULT = 1.0;
    static final double SCALE_MIN = 1.0;
    static final double SCALE_MAX = 1.4;
    static final float SCALE_BASE_MULTIPLIER = 2.0f;

    static float effectiveScale(NumberValue<Double> value) {
        if (value == null || value.get() == null) {
            return SCALE_BASE_MULTIPLIER;
        }
        return value.get().floatValue() * SCALE_BASE_MULTIPLIER;
    }

    static String current(ModeValue value) {
        String mode = value != null ? value.get() : null;
        return UNIFIED_DIVIDER.equals(mode) ? UNIFIED_DIVIDER : SPLIT_HEADER;
    }
}
