/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.events.Events;
import combatant.client.events.impl.KeybindIsPressedEvent;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {

    @Shadow
    private boolean isDown;

    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void combatant$keybindIsPressedEvent(CallbackInfoReturnable<Boolean> cir) {
        if (!Events.BUS.hasListeners(KeybindIsPressedEvent.class)) {
            return;
        }

        KeybindIsPressedEvent event = new KeybindIsPressedEvent((KeyMapping) (Object) this, isDown);
        Events.BUS.post(event);
        cir.setReturnValue(event.isPressed());
    }
}
