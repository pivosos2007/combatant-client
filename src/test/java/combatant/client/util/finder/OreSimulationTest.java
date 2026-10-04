/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.finder;

import net.minecraft.SharedConstants;
import net.minecraft.data.worldgen.placement.OrePlacements;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the part of NetheriteFinder most likely to break on a version bump: rebuilding the
 * vanilla registries and finding each debris feature's generation step and index. Simulation
 * itself needs mixin accessors and a level, so it is covered in game, not here.
 */
class OreSimulationTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void resolvesBothDebrisFeaturesInTheNether() {
        OreSimulation sim = OreSimulation.forNether(
                OrePlacements.ORE_ANCIENT_DEBRIS_LARGE,
                OrePlacements.ORE_ANCIENT_DEBRIS_SMALL);

        List<OreSimulation.OreSpec> specs = sim.specs();
        assertEquals(2, specs.size(), "both debris features should generate in the nether");

        OreSimulation.OreSpec large = specs.get(0);
        OreSimulation.OreSpec small = specs.get(1);
        // Both debris features live in the same decoration step, at different indices.
        assertEquals(large.step(), small.step());
        assertTrue(large.index() != small.index(), "features in one step must have distinct indices");
        assertTrue(large.size() > small.size(), "large debris vein is bigger than the small one");
        assertEquals(1.0f, large.discardChanceOnAirExposure());
        assertEquals(1.0f, small.discardChanceOnAirExposure());
        assertTrue(!large.modifiers().isEmpty() && !small.modifiers().isEmpty());
    }
}
