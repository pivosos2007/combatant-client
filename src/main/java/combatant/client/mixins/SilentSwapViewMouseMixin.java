/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import combatant.client.util.player.inventory.InventorySwap;

/** Scrolling steps from the slot on screen, not from a leased one, or the wheel jumps around. */
@Mixin(MouseHandler.class)
public class SilentSwapViewMouseMixin {

    @WrapMethod(method = "onScroll")
    private void combatant$clientViewScroll(long window, double xOffset, double yOffset, Operation<Void> original) {
        InventorySwap.INSTANCE.beginClientView();
        try {
            original.call(window, xOffset, yOffset);
        } finally {
            InventorySwap.INSTANCE.endClientView();
        }
    }
}
