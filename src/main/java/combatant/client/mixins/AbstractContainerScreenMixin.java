/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.InventoryMove;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void combatant$inventoryMove$deferClose(CallbackInfo ci) {
        InventoryMove module = Modules.get(InventoryMove.class);
        if (module == null || !module.isEnabled()) return;
        if (module.shouldCancelHandledScreenClose((AbstractContainerScreen<?>) (Object) this)) {
            ci.cancel();
        }
    }
}
