/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import combatant.client.util.player.inventory.InventorySlotLocks;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.InventoryMove;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow protected Slot hoveredSlot;
    @Shadow @Final protected AbstractContainerMenu menu;

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void combatant$lockedSlotClicked(Slot slot, int slotId, int button,
                                            ContainerInput input, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && InventorySlotLocks.blocks(menu,
                slotId, button, input, mc.player, null)) ci.cancel();
    }

    // While an inventory is open, F is handled by the screen rather than Minecraft.handleKeybinds.
    // Consume this key before vanilla can swap ANY hovered item with the protected offhand.
    @Inject(method = "checkHotbarKeyPressed", at = @At("HEAD"), cancellable = true)
    private void combatant$guardInventoryOffhandKey(KeyEvent key, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && InventorySlotLocks.isLocked(InventorySlotLocks.OFFHAND)
                && mc.options.keySwapOffhand.matches(key)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void combatant$lockedSlotOverlay(GuiGraphicsExtractor graphics, Slot slot,
                                            int mouseX, int mouseY, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !InventorySlotLocks.isLocked(slot, mc.player)) return;
        InventorySlotLocks.renderLockOverlay(graphics, slot.x, slot.y);
    }

    @Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), cancellable = true)
    private void combatant$lockedItemTooltip(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int index = InventorySlotLocks.playerSlotIndex(hoveredSlot, mc.player);
        if (index < 0) return;
        String reason = InventorySlotLocks.reason(index);
        if (reason == null || cir.getReturnValue() == null) return;
        List<Component> result = new ArrayList<>(cir.getReturnValue());
        result.add(Component.translatable("combatant.inventory.locked_by", reason)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        cir.setReturnValue(result);
    }


    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void combatant$inventoryMove$deferClose(CallbackInfo ci) {
        InventoryMove module = Modules.get(InventoryMove.class);
        if (module == null || !module.isEnabled()) return;
        if (module.shouldCancelHandledScreenClose((AbstractContainerScreen<?>) (Object) this)) {
            ci.cancel();
        }
    }
}
