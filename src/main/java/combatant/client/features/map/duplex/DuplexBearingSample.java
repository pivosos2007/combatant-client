/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.duplex;

import java.util.UUID;

public record DuplexBearingSample(
        UUID targetUuid,
        double observerX,
        double observerZ,
        double bearingRadians,
        long observedAtMs,
        long sourceRevision
) {
    public double dirX() { return -Math.sin(bearingRadians); }
    public double dirZ() { return Math.cos(bearingRadians); }
}
