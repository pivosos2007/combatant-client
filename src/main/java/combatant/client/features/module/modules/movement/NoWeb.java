/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.rules.MovementRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Removes the cobweb slowdown. CANCEL ignores the web entirely; REDUCE removes only part of the slowdown.
 */
@ModuleInfo(
        id = "noweb",
        displayName = "NoWeb",
        aliases = {"AntiWeb"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.RAGE,
        description = "module.noweb.description")
public final class NoWeb extends Module {

    private final EnumValue<Mode> mode = enumMode("mode", Mode.CANCEL);
    private final NumberValue<Double> reduction = visibleWhen(
            num("reduction", 0.6D, 0.0D, 1.0D), () -> mode.get() == Mode.REDUCE);

    /**
     * The stuck multiplier to use for a cobweb the player just entered, or {@code null} when the vanilla slowdown
     * stands. A full cancel returns an all-ones multiplier.
     */
    public static Vec3 stuckOverride(BlockState state, Vec3 multiplier) {
        NoWeb module = Modules.get(NoWeb.class);
        if (module == null || !module.isEnabled() || !state.is(Blocks.COBWEB)) return null;
        double r = module.mode.get() == Mode.CANCEL ? 1.0 : module.reduction.get();
        return new Vec3(
                MovementRules.reducedStuckMultiplier(multiplier.x, r),
                MovementRules.reducedStuckMultiplier(multiplier.y, r),
                MovementRules.reducedStuckMultiplier(multiplier.z, r));
    }

    public enum Mode {
        CANCEL,
        REDUCE
    }
}
