/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import combatant.client.util.player.inventory.InventorySlotLocks;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeSlotLockMixin {
    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void combatant$guardModuleLockedSlots(int containerId, int slot, int button,
                                                  ContainerInput input, Player player, CallbackInfo ci) {
        if (player == null || player.containerMenu == null || player.containerMenu.containerId != containerId) return;
        if (InventorySlotLocks.blocks(player.containerMenu, slot, button, input, player,
                InventorySwap.INSTANCE.executingActionOwner())) ci.cancel();
    }
}
