/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.seedcracker;

import combatant.client.compat.seedcracker.SeedCrackerOutput;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes SeedCrackerX command feedback through {@link SeedCrackerOutput},
 * suppressing its native feedback path to avoid duplicate messages.
 */
@Pseudo
@Mixin(targets = "kaptainwutax.seedcrackerX.command.ClientCommand", remap = false)
public abstract class SeedCrackerFeedbackMixin {
    @Inject(method = "sendFeedback", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void combatant$forward(String message, ChatFormatting color, CallbackInfo ci) {
        SeedCrackerOutput.feedback(message, color);
        ci.cancel();
    }
}
