/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.events.impl.ItemUseBlockEvent;
import combatant.client.events.impl.ItemUseAcceptedEvent;
import combatant.client.events.impl.InteractionBlockEvent;
import combatant.client.events.impl.ClientAttackEffectsEvent;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.events.Events;
import combatant.client.events.impl.AttackEntityEvent;
import combatant.client.events.impl.EventBreakBlock;
import combatant.client.events.impl.BlockUseAttemptEvent;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.AttributeSwap;
import combatant.client.features.module.modules.combat.Criticals;
import combatant.client.mixins.accessors.WorldAccessor;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySlotLocks;
import combatant.client.util.player.inventory.InventoryComponentMap;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Unique
    private boolean combatant$blockUseHeldBlock;

    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void combatant$guardModuleLockedSlots(int containerId, int slot, int button,
                                                  ContainerInput input, Player player, CallbackInfo ci) {
        if (player == null || player.containerMenu == null || player.containerMenu.containerId != containerId) return;
        if (InventorySlotLocks.blocks(player.containerMenu, slot, button, input, player,
                InventorySwap.INSTANCE.executingActionOwner())) ci.cancel();
    }

    @Inject(method = "handleCreativeModeItemAdd", at = @At("HEAD"), cancellable = true)
    private void combatant$guardCreativeItemWrite(ItemStack stack, int inventoryMenuSlot, CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        int inventoryIndex = InventoryComponentMap.playerInventoryIndex(
                player.inventoryMenu, inventoryMenuSlot, player);
        if (inventoryIndex >= 0 && InventorySlotLocks.blocks(
                inventoryIndex, InventorySwap.INSTANCE.executingActionOwner())) ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void combatant$skipTickWithoutPlayer(CallbackInfo ci) {
        if (Minecraft.getInstance().player == null) {
            ci.cancel();
        }
    }

    @Inject(method = "ensureHasSentCarriedItem", at = @At("HEAD"), cancellable = true)
    private void combatant$skipCarriedItemSyncWithoutPlayer(CallbackInfo ci) {
        if (Minecraft.getInstance().player == null) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(
            method = "ensureHasSentCarriedItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;getSelectedSlot()I"
            )
    )
    private int combatant$hookSilentSelectedSlot(int original) {
        return InventorySwap.INSTANCE.effectiveSelectedSlot();
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void combatant$breakBlockEvent(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        if (!Events.BUS.hasListeners(EventBreakBlock.class)) return;
        Events.BUS.post(new EventBreakBlock(pos));
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void onInteractItemHead(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return;

        if (Events.BUS.post(new ItemUseBlockEvent(stack.getItem())).isCancelled()) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "useItem", at = @At("RETURN"))
    private void onInteractItemReturn(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = cir.getReturnValue();
        if (player instanceof LocalPlayer localPlayer && Events.BUS.hasListeners(BlockUseAttemptEvent.class)) {
            Events.BUS.post(new BlockUseAttemptEvent(localPlayer, hand, null, result,
                    player.getItemInHand(hand).getItem() instanceof BlockItem));
        }
        if (!result.consumesAction()) return;

        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return;

        Events.BUS.post(new ItemUseAcceptedEvent(stack.getItem()));
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void combatant$beforeBlockUse(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        combatant$blockUseHeldBlock = player.getItemInHand(hand).getItem() instanceof BlockItem;
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void combatant$afterBlockUse(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                        CallbackInfoReturnable<InteractionResult> cir) {
        if (!Events.BUS.hasListeners(BlockUseAttemptEvent.class)) return;
        Events.BUS.post(new BlockUseAttemptEvent(
                player, hand, hit,
                cir.getReturnValue(), combatant$blockUseHeldBlock));
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void freecam$block(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                               CallbackInfoReturnable<InteractionResult> cir) {
        if (Events.BUS.post(new InteractionBlockEvent()).isCancelled()) {
            cir.setReturnValue(InteractionResult.PASS);
            cir.cancel();
        }
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void freecam$item(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (Events.BUS.post(new InteractionBlockEvent()).isCancelled()) {
            cir.setReturnValue(InteractionResult.PASS);
            cir.cancel();
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void freecam$entity(Player player, Entity entity, EntityHitResult hit, InteractionHand hand,
                                CallbackInfoReturnable<InteractionResult> cir) {
        if (Events.BUS.post(new InteractionBlockEvent()).isCancelled()) {
            cir.setReturnValue(InteractionResult.PASS);
            cir.cancel();
        }
    }

    @Inject(method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"))
    private void onAttackEntity(Player player, Entity target, CallbackInfo ci) {
        if (!((WorldAccessor) player.level()).combatant$isClient()) return;

        if (player instanceof LocalPlayer clientPlayer) {
            Criticals criticals = Modules.get(Criticals.class);
            if (criticals != null && criticals.isEnabled()) {
                criticals.beforeAttack(Minecraft.getInstance(), clientPlayer, target);
            }
        }

        if (Events.BUS.hasListeners(AttackEntityEvent.class)) {
            Events.BUS.post(new AttackEntityEvent(player, target));
        }

        Events.BUS.post(new ClientAttackEffectsEvent(player, target));
    }

    @Inject(method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("RETURN"))
    private void combatant$attributeSwapAfterClientAttack(Player player, Entity target, CallbackInfo ci) {
        if (player != Minecraft.getInstance().player) return;
        AttributeSwap.tryBreakShieldAfterClientAttack(target);
    }
}
