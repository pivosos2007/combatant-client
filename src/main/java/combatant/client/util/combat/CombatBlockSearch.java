/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Allocation-free coordinate traversal shared by explosion placement planners. */
public final class CombatBlockSearch {
    private CombatBlockSearch() {
    }

    public static void forEachSphere(Vec3 center, int horizontalRange, int verticalRange, PositionConsumer consumer) {
        if (center == null || consumer == null) return;

        int radius = Math.max(1, horizontalRange);
        int yRadius = Math.max(0, Math.min(radius, verticalRange));
        double radiusSq = (double) radius * radius;
        int originX = Mth.floor(center.x);
        int originY = Mth.floor(center.y);
        int originZ = Mth.floor(center.z);

        for (int x = originX - radius; x <= originX + radius; x++) {
            double dx = x + 0.5 - center.x;
            for (int y = originY - yRadius; y <= originY + yRadius; y++) {
                double dy = y + 0.5 - center.y;
                double xySq = dx * dx + dy * dy;
                if (xySq > radiusSq) continue;
                for (int z = originZ - radius; z <= originZ + radius; z++) {
                    double dz = z + 0.5 - center.z;
                    if (xySq + dz * dz <= radiusSq) consumer.accept(x, y, z);
                }
            }
        }
    }

    @FunctionalInterface
    public interface PositionConsumer {
        void accept(int x, int y, int z);
    }
}
