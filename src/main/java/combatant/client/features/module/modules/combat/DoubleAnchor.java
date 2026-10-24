/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.automation.Action;
import combatant.client.automation.Actions;
import combatant.client.automation.placement.PlacementPlanner;
import combatant.client.config.values.EnumValue;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.modules.combat.anchormacro.AnchorMacroPlanner;
import combatant.client.features.module.modules.combat.anchormacro.AnchorSequenceModule;
import combatant.client.features.module.modules.combat.anchormacro.AnchorSteps;
import combatant.client.features.module.modules.combat.anchormacro.DoubleAnchorLayout;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Double anchor: places two neighbouring anchors, charges both, then detonates them one after the other. Anchors
 * survive each other's explosions, so the second blast lands right after the first (a popped totem is gone).
 */
@ModuleInfo(
        id = "doubleanchor",
        displayName = "DoubleAnchor",
        aliases = {"DoubleAnchorBomb", "AnchorDouble"},
        category = ModuleCategory.COMBAT, subcategory = ModuleSubcategory.ATTACK,
        description = "module.doubleanchor.description")
public final class DoubleAnchor extends AnchorSequenceModule {

    private final EnumValue<DoubleAnchorLayout.Layout> layout = enumMode(
            "layout", DoubleAnchorLayout.Layout.STACKED, DoubleAnchorLayout.Layout.values());

    @Override
    protected Action buildSequence(LocalPlayer player, ClientLevel level, BlockHitResult look, AnchorSteps steps,
                                   int delayTicks) {
        BlockPos looked = look.getBlockPos();
        BlockPos first = looked;
        boolean firstExists = level.getBlockState(looked).is(Blocks.RESPAWN_ANCHOR);
        if (!firstExists) {
            first = looked.relative(look.getDirection());
            if (!level.getBlockState(first).canBeReplaced()) return null;
        }
        int needed = firstExists ? 1 : 2;
        if (AnchorSteps.anchorItemCount(player) < needed) {
            warn("notification.anchormacro.stopped.no_anchor_item");
            return null;
        }

        BlockPos firstPos = first;
        double reach = effectiveRange();
        BlockPos second = DoubleAnchorLayout.chooseSecond(firstPos, layout.get(), candidate ->
                !PlacementPlanner.isBlocked(player, candidate, false)
                        && player.getEyePosition().distanceToSqr(Vec3.atCenterOf(candidate)) <= reach * reach);
        if (second == null) {
            warn("notification.doubleanchor.no_space");
            return null;
        }

        int charges = AnchorMacroPlanner.clampTarget(chargesTarget());
        Action[] sequence = new Action[2 * charges + 4];
        int i = 0;
        sequence[i++] = steps.place(firstPos, 0);
        sequence[i++] = steps.place(second, delayTicks);
        for (int c = 0; c < charges; c++) sequence[i++] = steps.charge(firstPos, delayTicks);
        for (int c = 0; c < charges; c++) sequence[i++] = steps.charge(second, delayTicks);
        sequence[i++] = steps.detonate(firstPos, delayTicks);
        sequence[i] = steps.detonate(second, delayTicks);
        return Actions.sequence("DoubleAnchor", sequence);
    }
}
