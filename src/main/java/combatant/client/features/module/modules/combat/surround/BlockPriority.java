/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.surround;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/** Which blast-resistant block Surround and SelfTrap reach for first. */
public enum BlockPriority {
    ANY,
    OBSIDIAN_ONLY,
    E_CHEST_ONLY,
    PREFER_OBSIDIAN,
    PREFER_E_CHEST;

    /** Blocks that survive a crystal blast well enough to wall someone in. */
    static final Set<Block> BLAST_RESISTANT = Set.of(
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN,
            Blocks.RESPAWN_ANCHOR,
            Blocks.NETHERITE_BLOCK,
            Blocks.ENDER_CHEST,
            Blocks.ANVIL,
            Blocks.CHIPPED_ANVIL,
            Blocks.DAMAGED_ANVIL
    );

    public boolean matches(Block block) {
        return switch (this) {
            case ANY -> BLAST_RESISTANT.contains(block);
            case OBSIDIAN_ONLY, PREFER_OBSIDIAN -> block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN;
            case E_CHEST_ONLY, PREFER_E_CHEST -> block == Blocks.ENDER_CHEST;
        };
    }

    /** True for the PREFER_* entries: when the preferred block is missing, any blast-resistant one will do. */
    public boolean fallsBack() {
        return this == PREFER_OBSIDIAN || this == PREFER_E_CHEST;
    }
}
