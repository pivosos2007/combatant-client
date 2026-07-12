/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.util.player.EnvironmentMovementController;

@Mixin(Block.class)
public abstract class BlockMixin {

    @Inject(
            method = "getFriction",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nostun$overrideSlipperiness(CallbackInfoReturnable<Float> cir) {
        Float override = EnvironmentMovementController.getSlipperinessOverride((Block) (Object) this);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }

    @Inject(
            method = "getSpeedFactor",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nostun$overrideVelocityMultiplier(CallbackInfoReturnable<Float> cir) {
        Float override = EnvironmentMovementController.getVelocityMultiplierOverride((Block) (Object) this);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
