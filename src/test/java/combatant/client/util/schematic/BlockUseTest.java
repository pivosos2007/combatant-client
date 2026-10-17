/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockUseTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void blocksThatDoSomethingOnAClickAreFound() {
        Block[] reacting = {
                Blocks.COMPOSTER, Blocks.CAKE, Blocks.RESPAWN_ANCHOR, Blocks.REDSTONE_WIRE, Blocks.DRAGON_EGG,
                Blocks.BELL, Blocks.FLOWER_POT, Blocks.CANDLE, Blocks.CAVE_VINES, Blocks.LEVER, Blocks.REPEATER,
                Blocks.COMPARATOR, Blocks.NOTE_BLOCK, Blocks.HOPPER, Blocks.DROPPER, Blocks.OAK_DOOR,
                Blocks.OAK_TRAPDOOR, Blocks.OAK_FENCE_GATE, Blocks.CHEST, Blocks.CRAFTING_TABLE,
                Blocks.DAYLIGHT_DETECTOR, Blocks.TNT
        };
        for (Block block : reacting) {
            assertTrue(BlockUse.reactsToClick(block.defaultBlockState()), block + " should need a sneak");
        }
    }

    @Test
    void plainBlocksAreSafeToClick() {
        Block[] plain = {Blocks.STONE, Blocks.OAK_PLANKS, Blocks.GLASS, Blocks.SLIME_BLOCK, Blocks.REDSTONE_BLOCK,
                Blocks.OBSERVER, Blocks.STICKY_PISTON, Blocks.REDSTONE_LAMP, Blocks.SAND};
        for (Block block : plain) {
            assertFalse(BlockUse.reactsToClick(block.defaultBlockState()), block + " should not need a sneak");
        }
    }
}
