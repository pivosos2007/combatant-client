/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import combatant.client.util.player.inventory.InventorySlotLocks;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuSlotLockMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void combatant$guardClientSlotMutation(int slot, int button, ContainerInput input,
                                                   Player player, CallbackInfo ci) {
        // Do not apply client-side input policy to the integrated server's player.
        if (player == null || player != Minecraft.getInstance().player) return;
        if (InventorySlotLocks.blocks((AbstractContainerMenu) (Object) this, slot, button,
                input, player, InventorySwap.INSTANCE.executingActionOwner())) {
            ci.cancel();
        }
    }
}
