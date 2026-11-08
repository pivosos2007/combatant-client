/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.movement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Prioritizes block columns by swept player-footprint coverage, not a rounded center at one future tick. */
final class MlgLandingSelector {
    private MlgLandingSelector() { }

    record Column(int x, int z, double score) { }

    static List<Column> rank(double previousX, double previousZ, double landingX, double landingZ,
                             double halfWidth) {
        if (!Double.isFinite(previousX) || !Double.isFinite(previousZ)
                || !Double.isFinite(landingX) || !Double.isFinite(landingZ)) return List.of();
        halfWidth = Math.max(0.15, Math.min(0.49, halfWidth));
        int minX = (int) Math.floor(Math.min(previousX, landingX) - halfWidth);
        int maxX = (int) Math.floor(Math.max(previousX, landingX) + halfWidth);
        int minZ = (int) Math.floor(Math.min(previousZ, landingZ) - halfWidth);
        int maxZ = (int) Math.floor(Math.max(previousZ, landingZ) + halfWidth);
        // At most a 3x3 neighborhood for normal player speeds, bounded even for unusual velocity.
        minX = Math.max(minX, (int) Math.floor(landingX) - 1);
        maxX = Math.min(maxX, (int) Math.floor(landingX) + 1);
        minZ = Math.max(minZ, (int) Math.floor(landingZ) - 1);
        maxZ = Math.min(maxZ, (int) Math.floor(landingZ) + 1);
        List<Column> result = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double atLanding = area(landingX, landingZ, halfWidth, x, z);
                double beforeLanding = area(previousX, previousZ, halfWidth, x, z);
                if (atLanding < 1.0E-5) continue;
                // A large overlap on landing matters most; previous-tick overlap breaks edge ties.
                result.add(new Column(x, z, atLanding + .35 * beforeLanding));
            }
        }
        result.sort(Comparator.comparingDouble(Column::score).reversed());
        return result;
    }

    static double area(double x, double z, double halfWidth, int blockX, int blockZ) {
        double overlapX = Math.max(0, Math.min(x + halfWidth, blockX + 1.) - Math.max(x - halfWidth, blockX));
        double overlapZ = Math.max(0, Math.min(z + halfWidth, blockZ + 1.) - Math.max(z - halfWidth, blockZ));
        return overlapX * overlapZ;
    }
}
