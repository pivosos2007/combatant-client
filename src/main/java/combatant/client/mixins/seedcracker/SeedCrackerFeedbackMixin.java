package combatant.client.mixins.seedcracker;

import combatant.client.compat.seedcracker.SeedCrackerOutput;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The native commands also use this separate sendFeedback path. */
@Pseudo
@Mixin(targets = "kaptainwutax.seedcrackerX.command.ClientCommand", remap = false)
public abstract class SeedCrackerFeedbackMixin {
    @Inject(method = "sendFeedback", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void combatant$forward(String message, ChatFormatting color, CallbackInfo ci) {
        SeedCrackerOutput.feedback(message, color);
        ci.cancel();
    }
}
