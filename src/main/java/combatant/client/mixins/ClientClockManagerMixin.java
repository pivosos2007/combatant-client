/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.WorldTweaks;

@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class ClientClockManagerMixin {

    @Inject(method = "totalTicks", at = @At("HEAD"), cancellable = true)
    private void combatant$overrideRenderClock(CallbackInfoReturnable<Long> cir) {
        WorldTweaks worldTweaks = Modules.get(WorldTweaks.class);
        if (worldTweaks == null || !worldTweaks.shouldOverrideTime()) return;

        cir.setReturnValue(worldTweaks.getRenderClockTicks());
    }
}
