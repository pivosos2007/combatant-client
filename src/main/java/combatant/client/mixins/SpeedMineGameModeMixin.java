/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.player.SpeedMine;

/**
 * Hands left-click mining to SpeedMine. Kept apart from {@link MultiPlayerGameModeMixin} so the
 * upstream mixin stays untouched.
 *
 * <p>Returning true keeps vanilla's swing and crack particles while skipping its own START/STOP
 * packets and client-side progress, which would otherwise fight the packet miner.</p>
 */
@Mixin(MultiPlayerGameMode.class)
public class SpeedMineGameModeMixin {

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void combatant$speedMineStart(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (combatant$speedMineHandles(pos, direction)) cir.setReturnValue(true);
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void combatant$speedMineContinue(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (combatant$speedMineHandles(pos, direction)) cir.setReturnValue(true);
    }

    private static boolean combatant$speedMineHandles(BlockPos pos, Direction direction) {
        SpeedMine speedMine = Modules.get(SpeedMine.class);
        return speedMine != null && speedMine.handleAttack(pos, direction);
    }
}
