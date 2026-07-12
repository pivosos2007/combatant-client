/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.config.values.ItemCooldownRulesValue;
import combatant.client.events.Events;
import combatant.client.events.impl.AttackEntityEvent;
import combatant.client.events.impl.EventBreakBlock;
import combatant.client.features.gui.hud.draggable.impl.TargetHud;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.AttributeSwap;
import combatant.client.features.module.modules.combat.Criticals;
import combatant.client.features.module.modules.combat.Hitbox;
import combatant.client.features.module.modules.combat.PvpCooldowns;
import combatant.client.features.module.modules.misc.HitSounds;
import combatant.client.features.module.modules.visuals.Freecam;
import combatant.client.features.module.modules.visuals.HitEffect;
import combatant.client.mixins.accessors.WorldAccessor;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.target.TargetManager;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

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

        PvpCooldowns cooldowns = Modules.get(PvpCooldowns.class);
        if (cooldowns == null) return;

        // No hard-coded PvP item blocking here anymore. This path is opt-in and item-list based.
        if (cooldowns.shouldBlockItemUse(stack.getItem())) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "useItem", at = @At("RETURN"))
    private void onInteractItemReturn(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = cir.getReturnValue();
        if (!result.consumesAction()) return;

        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return;

        PvpCooldowns cooldowns = Modules.get(PvpCooldowns.class);
        if (cooldowns == null) return;

        cooldowns.tryStartLocalCooldown(stack.getItem(), ItemCooldownRulesValue.Trigger.INTERACT_ACCEPT);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void onInteractBlockHead(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        // no PvP cooldown block on interactBlock
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void onInteractBlockReturn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        // no PvP cooldown prediction on interactBlock
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void freecam$block(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                               CallbackInfoReturnable<InteractionResult> cir) {
        Freecam fc = Modules.get(Freecam.class);
        if (fc != null && fc.isEnabled() && !fc.allowInteract()) {
            cir.setReturnValue(InteractionResult.PASS);
            cir.cancel();
        }
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void freecam$item(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Freecam fc = Modules.get(Freecam.class);
        if (fc != null && fc.isEnabled() && !fc.allowInteract()) {
            cir.setReturnValue(InteractionResult.PASS);
            cir.cancel();
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void freecam$entity(Player player, Entity entity, EntityHitResult hit, InteractionHand hand,
                                CallbackInfoReturnable<InteractionResult> cir) {
        Freecam fc = Modules.get(Freecam.class);
        if (fc != null && fc.isEnabled() && !fc.allowInteract()) {
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

        Hitbox hitbox = Modules.get(Hitbox.class);
        if (hitbox != null) hitbox.markHit(target);

        HitSounds hitSounds = Modules.get(HitSounds.class);
        if (hitSounds != null) {
            hitSounds.handleHit(target);
        }

        HitEffect hitEffect = Modules.get(HitEffect.class);
        if (hitEffect != null) {
            hitEffect.handleHit(target);
        }

        TargetHud.notifyHit(target);
        TargetManager.onAttack(target);
    }

    @Inject(method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("RETURN"))
    private void combatant$attributeSwapAfterClientAttack(Player player, Entity target, CallbackInfo ci) {
        if (player != Minecraft.getInstance().player) return;
        AttributeSwap.tryBreakShieldAfterClientAttack(target);
    }
}
