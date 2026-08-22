/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CombatBlockSearchTest {
    @ParameterizedTest
    @MethodSource("searches")
    void matchesLegacyPlacementSphereExactly(Vec3 center, int horizontalRange, int verticalRange) {
        Set<BlockPos> expected = legacySearch(center, horizontalRange, verticalRange);
        Set<BlockPos> actual = new HashSet<>();

        CombatBlockSearch.forEachSphere(center, horizontalRange, verticalRange,
                (x, y, z) -> actual.add(new BlockPos(x, y, z)));

        assertEquals(expected, actual);
    }

    private static Stream<Arguments> searches() {
        return Stream.of(
                Arguments.of(new Vec3(0.0, 64.0, 0.0), 1, 1),
                Arguments.of(new Vec3(12.37, -3.2, -8.81), 5, 3),
                Arguments.of(new Vec3(-0.01, 255.99, -31.5), 6, 20),
                Arguments.of(new Vec3(-9.9, 4.1, 7.7), 0, -4)
        );
    }

    private static Set<BlockPos> legacySearch(Vec3 center, int horizontalRange, int verticalRange) {
        int radius = Math.max(1, horizontalRange);
        int yRadius = Math.max(0, Math.min(radius, verticalRange));
        double radiusSq = (double) radius * radius;
        BlockPos origin = BlockPos.containing(center);
        Set<BlockPos> result = new HashSet<>();

        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int y = origin.getY() - yRadius; y <= origin.getY() + yRadius; y++) {
                for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (Vec3.atCenterOf(pos).distanceToSqr(center) <= radiusSq) {
                        result.add(pos);
                    }
                }
            }
        }
        return result;
    }
}
