/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.heuristic;

import java.util.UUID;

public record HeuristicEstimate(
        UUID targetUuid,
        double x,
        double z,
        double uncertaintyMajor,
        double uncertaintyMinor,
        double uncertaintyAngleRadians,
        double residualRms,
        double confidence,
        int sampleCount,
        int inlierCount,
        long updatedAtMs,
        long segmentId
) {}
