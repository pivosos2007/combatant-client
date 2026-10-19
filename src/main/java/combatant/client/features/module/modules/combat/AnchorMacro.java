/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.automation.Action;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.modules.combat.anchormacro.AnchorSequenceModule;
import combatant.client.features.module.modules.combat.anchormacro.AnchorSteps;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Crosshair anchor macro: while the trigger is held and the crosshair is on a respawn anchor (or on a block an anchor
 * can be placed against), places the anchor, charges it with glowstone, swaps to a totem or sword and detonates it.
 */
@ModuleInfo(
        id = "anchormacro",
        displayName = "AnchorMacro",
        aliases = {"AnchorBomb", "AnchorBind"},
        category = ModuleCategory.COMBAT, subcategory = ModuleSubcategory.ATTACK,
        description = "module.anchormacro.description")
public final class AnchorMacro extends AnchorSequenceModule {

    @Override
    protected Action buildSequence(LocalPlayer player, ClientLevel level, BlockHitResult look, AnchorSteps steps,
                                   int delayTicks) {
        BlockPos looked = look.getBlockPos();
        BlockPos anchor = looked;
        if (!level.getBlockState(looked).is(Blocks.RESPAWN_ANCHOR)) {
            anchor = looked.relative(look.getDirection());
            if (!level.getBlockState(anchor).canBeReplaced()) return null;
        }
        return steps.fullSequence(anchor, delayTicks);
    }
}
