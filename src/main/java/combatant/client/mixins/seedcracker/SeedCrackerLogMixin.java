package combatant.client.mixins.seedcracker;

import combatant.client.compat.seedcracker.SeedCrackerOutput;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capture upstream's single outbound Log funnel before it reaches vanilla chat. */
@Pseudo
@Mixin(targets = "kaptainwutax.seedcrackerX.util.Log", remap = false)
public abstract class SeedCrackerLogMixin {
    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void combatant$forward(Component component, CallbackInfo ci) {
        SeedCrackerOutput.forward(component);
        ci.cancel();
    }
}
