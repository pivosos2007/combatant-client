/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import combatant.client.util.player.inventory.InventorySwap;

/** Gui.tick drives the held-item name popup; a lease must not pop up the tool's name. */
@Mixin(Gui.class)
public class SilentSwapViewGuiMixin {

    @WrapMethod(method = "tick")
    private void combatant$clientViewTick(Operation<Void> original) {
        InventorySwap.INSTANCE.beginClientView();
        try {
            original.call();
        } finally {
            InventorySwap.INSTANCE.endClientView();
        }
    }
}
