/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.seedcracker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No external server-seed database traffic from an optional Combatant integration. */
@Pseudo
@Mixin(targets = "kaptainwutax.seedcrackerX.util.Database", remap = false)
public abstract class SeedCrackerDatabaseMixin {
    @Inject(method = "fetchSeeds", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void combatant$disableDatabaseFetch(CallbackInfo ci) {
        ci.cancel();
    }
}
