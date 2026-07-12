/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;


import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import combatant.client.util.player.inventory.InventorySwap;

@Mixin(Inventory.class)
public abstract class PlayerInventoryMixin {

    @Shadow
    @Final
    public Player player;

    @Shadow
    public abstract ItemStack getItem(int slot);

    @ModifyReturnValue(method = "getSelectedSlot", at = @At("RETURN"))
    private int combatant$silentSelectedSlot(int original) {
        Minecraft mc = Minecraft.getInstance();
        return player == mc.player ? InventorySwap.INSTANCE.effectiveSelectedSlot() : original;
    }

    @ModifyReturnValue(method = "getSelectedItem", at = @At("RETURN"))
    private ItemStack combatant$silentSelectedStack(ItemStack original) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player && InventorySwap.INSTANCE.isHotbarLeased()) {
            return getItem(InventorySwap.INSTANCE.effectiveSelectedSlot());
        }

        return original;
    }
}
