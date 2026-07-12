/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.BedwarsESP;
import combatant.client.features.module.modules.visuals.BlockESP;
import combatant.client.util.block.bed.BedBlockUtil;

public enum BlockObservationHub {
    ;

    public static void observeSodiumBlock(int x, int y, int z, BlockState state) {
        if (state == null || state.isAir()) {
            return;
        }

        BlockEspSodiumCandidateCollector.observeSodiumBlock(x, y, z, state);

        if (BedBlockUtil.isBed(state)) {
            BedwarsESP bedwarsEsp = Modules.get(BedwarsESP.class);
            if (bedwarsEsp != null && bedwarsEsp.isEnabled()) {
                bedwarsEsp.acceptSodiumBlockState(x, y, z, state);
            }
        }
    }

    public static void observeSodiumRenderedBlock(BlockPos pos, BlockState state) {
        if (pos == null || state == null || state.isAir()) {
            return;
        }

        BlockEspSodiumCandidateCollector.observeSodiumRenderedBlock(pos.getX(), pos.getY(), pos.getZ(), state);
    }

    public static void observeWorldUpdate(BlockPos pos, BlockState state) {
        if (pos == null || state == null) {
            return;
        }

        BlockESP blockEsp = Modules.get(BlockESP.class);
        if (blockEsp != null && blockEsp.isEnabled()) {
            blockEsp.acceptWorldBlockUpdate(pos, state);
        }

        BedwarsESP bedwarsEsp = Modules.get(BedwarsESP.class);
        if (bedwarsEsp != null && bedwarsEsp.isEnabled()) {
            bedwarsEsp.acceptWorldBlockUpdate(pos, state);
        }
    }
}
