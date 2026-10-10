/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import combatant.client.util.player.inventory.InventorySwap;

/**
 * Renders the hand and world with the slot the player actually picked. {@code tick} covers the
 * first-person hand swap animation (ItemInHandRenderer.tick), {@code extract} builds the frame's
 * render state (third-person held item, GUI hotbar), and {@code render} draws it.
 */
@Mixin(GameRenderer.class)
public class SilentSwapViewGameRendererMixin {

    @WrapMethod(method = "tick")
    private void combatant$clientViewTick(Operation<Void> original) {
        InventorySwap.INSTANCE.beginClientView();
        try {
            original.call();
        } finally {
            InventorySwap.INSTANCE.endClientView();
        }
    }

    @WrapMethod(method = "extract")
    private void combatant$clientViewExtract(DeltaTracker deltaTracker, boolean advanceGameTime, Operation<Void> original) {
        InventorySwap.INSTANCE.beginClientView();
        try {
            original.call(deltaTracker, advanceGameTime);
        } finally {
            InventorySwap.INSTANCE.endClientView();
        }
    }

    @WrapMethod(method = "render")
    private void combatant$clientViewRender(DeltaTracker deltaTracker, boolean advanceGameTime, Operation<Void> original) {
        InventorySwap.INSTANCE.beginClientView();
        try {
            original.call(deltaTracker, advanceGameTime);
        } finally {
            InventorySwap.INSTANCE.endClientView();
        }
    }
}
