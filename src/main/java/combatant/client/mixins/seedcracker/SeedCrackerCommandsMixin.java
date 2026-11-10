/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.seedcracker;

import combatant.client.config.subsystem.SeedCrackerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables SeedCrackerX command registration when native commands are hidden.
 * The optional injection tolerates builds without the expected registration method.
 */
@Pseudo
@Mixin(targets = "kaptainwutax.seedcrackerX.init.ClientCommands", remap = false)
public abstract class SeedCrackerCommandsMixin {
    @Inject(method = "registerCommands", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void combatant$replaceCommands(CallbackInfo ci) {
        if (SeedCrackerConfig.get().hideNativeCommands()) ci.cancel();
    }
}
