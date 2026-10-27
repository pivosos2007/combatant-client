/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.placement;

import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.block.scaffold.ScaffoldPlacementTarget;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One block to place: the cell, the support face to click, and what to place with.
 *
 * @param pinned the support is fixed by the caller (directional blocks) and is never re-solved
 * @param check  optional last gate before the click, evaluated with the rotation the server judges the click with
 *               (the one it already has in NORMAL mode, the planned one without rotating); null accepts
 */
public record PlacementTarget(BlockPos pos, ScaffoldPlacementTarget support, PlacementSlot slot,
                              boolean pinned, ClickCheck check) {

    @FunctionalInterface
    public interface ClickCheck {
        boolean accepts(LocalPlayer player, Rotation clickRotation, BlockHitResult hit);
    }

    public PlacementTarget(BlockPos pos, ScaffoldPlacementTarget support, PlacementSlot slot) {
        this(pos, support, slot, false, null);
    }

    public PlacementTarget withSupport(ScaffoldPlacementTarget newSupport) {
        return new PlacementTarget(pos, newSupport, slot, pinned, check);
    }
}
