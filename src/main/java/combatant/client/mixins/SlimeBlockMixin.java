/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.NoStun;

@Mixin(SlimeBlock.class)
public class SlimeBlockMixin {

    @Inject(method = "stepOn", at = @At("HEAD"), cancellable = true)
    private void nostun$onSteppedOn(Level world, BlockPos pos, BlockState state, Entity entity, CallbackInfo ci) {
        NoStun ns = Modules.get(NoStun.class);
        if (ns == null || !ns.isEnabled()) return;
        if (!ns.isFunctionEnabled(NoStun.fnEnvBlocks())) return;
        if (!ns.isEnvBlockEnabled(NoStun.envSlime())) return;
        ci.cancel();
    }
}
