/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.NoSlow;
import combatant.client.features.module.modules.movement.Sprint;

@Mixin(ClientInput.class)
public abstract class InputMixin {

    @Inject(method = "hasForwardImpulse", at = @At("HEAD"), cancellable = true)
    private void allowAllDirections(CallbackInfoReturnable<Boolean> cir) {
        Sprint module = Modules.get(Sprint.class);
        boolean sprintAllow = module != null && module.shouldAllowDirectionalSprint();

        NoSlow noSlow = Modules.get(NoSlow.class);
        Minecraft mc = Minecraft.getInstance();
        boolean noSlowAllow = noSlow != null && noSlow.shouldAllowSprint(mc != null ? mc.player : null);

        if (!sprintAllow && !noSlowAllow) return;

        ClientInput input = (ClientInput) (Object) this;
        if (input.keyPresses == null) return;

        if (sprintAllow) {
            if (input.keyPresses.forward()
                    || input.keyPresses.backward()
                    || input.keyPresses.left()
                    || input.keyPresses.right()) {
                cir.setReturnValue(true);
            }
        } else if (noSlowAllow) {
            if (input.keyPresses.forward()) {
                cir.setReturnValue(true);
            }
        }
    }
}
