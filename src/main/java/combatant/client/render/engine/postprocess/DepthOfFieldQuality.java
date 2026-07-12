/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.postprocess;

import combatant.client.config.values.EnumValue;

public enum DepthOfFieldQuality implements EnumValue.IdProvider {
    LOW("low", 8),
    MEDIUM("medium", 14),
    HIGH("high", 22);

    private final String id;
    private final int taps;

    DepthOfFieldQuality(String id, int taps) {
        this.id = id;
        this.taps = taps;
    }

    @Override
    public String id() {
        return id;
    }

    public int taps() {
        return taps;
    }
}
