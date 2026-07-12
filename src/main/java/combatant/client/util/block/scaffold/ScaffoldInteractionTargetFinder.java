/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.block.scaffold;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public enum ScaffoldInteractionTargetFinder {
    ;

    public static ScaffoldInteractionTarget findTarget(LocalPlayer player, BlockPos targetPos, ItemStack stack) {
        ScaffoldPlacementTarget target = ScaffoldTargetFinder.findTarget(
                player,
                targetPos,
                stack,
                List.of(BlockPos.ZERO),
                null,
                true
        );
        if (target == null) {
            return null;
        }

        return new ScaffoldInteractionTarget(
                target.getInteractedBlockPos(),
                target.getPlacedBlockPos(),
                target.getDirection(),
                target.getHitVec(),
                target.getHitVec().y,
                target.getRotation()
        );
    }
}
