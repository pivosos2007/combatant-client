/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.heuristic;

import java.util.UUID;

public record HeuristicObservation(
        UUID targetUuid,
        double observerX,
        double observerZ,
        double bearingRadians,
        long observedAtMs,
        long sourceRevision,
        double weight
) {
    public HeuristicObservation {
        if (targetUuid == null) throw new IllegalArgumentException("targetUuid");
        if (!Double.isFinite(observerX) || !Double.isFinite(observerZ) || !Double.isFinite(bearingRadians)) {
            throw new IllegalArgumentException("Non-finite observation");
        }
        if (!(weight > 0.0) || !Double.isFinite(weight)) weight = 1.0;
    }

    public double dirX() { return -Math.sin(bearingRadians); }
    public double dirZ() { return Math.cos(bearingRadians); }
}
