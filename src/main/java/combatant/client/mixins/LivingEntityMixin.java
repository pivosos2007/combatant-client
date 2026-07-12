/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.mixins;

/*
 * Parts of jump rotation correction are adapted from LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Original copyright (c) CCBlueX.
 */

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.config.values.ItemCooldownRulesValue;
import combatant.client.events.Events;
import combatant.client.events.impl.PlayerJumpEvent;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.PvpCooldowns;
import combatant.client.features.module.modules.combat.TPSSync;
import combatant.client.features.module.modules.movement.ElytraFly;
import combatant.client.features.module.modules.movement.NoStun;
import combatant.client.features.module.modules.movement.Timer;
import combatant.client.features.module.modules.visuals.NoRender;
import combatant.client.features.module.modules.visuals.WorldTweaks;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.pvp.client.CooldownsState;
import combatant.client.util.pvp.opponents.TotemPopCounter;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Unique
    private float combatant$itemUseRemainder = 0f;

    @Unique
    private float combatant$effectTickRemainder = 0f;

    @Shadow
    protected int useItemRemaining;
    @Unique
    private PlayerJumpEvent combatant$playerJumpEvent;

    @Shadow
    protected abstract float getJumpPower();

    @Inject(method = "getAttributeValue", at = @At("RETURN"))
    private void combatant$noStunSlownessMovementSpeed(Holder<Attribute> attribute, CallbackInfoReturnable<Double> cir) {
        if (!attribute.equals(Attributes.MOVEMENT_SPEED)) return;
        return;

    }

    @ModifyReturnValue(method = "getMainHandItem", at = @At("RETURN"))
    private ItemStack combatant$applyInventorySwapLeaseForMainHand(ItemStack original) {
        Minecraft mc = Minecraft.getInstance();
        if ((Object) this == mc.player) {
            InventorySwap.INSTANCE.isHotbarLeased();
        }

        return original;
    }


    /**
     * Optional local cooldown synthesis for servers that lock consumables without sending vanilla cooldown packets.
     * This never cancels item use; it only starts a visible local timer when explicitly configured.
     */
    @Inject(method = "updatingUsingItem", at = @At("HEAD"))
    private void combatant$onFinishEating(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (self != mc.player) return;

        ItemStack stack = self.getUseItem();
        if (stack == null || stack.isEmpty()) return;
        if (self.getUseItemRemainingTicks() != 1) return;

        PvpCooldowns cooldowns = Modules.get(PvpCooldowns.class);
        if (cooldowns == null) return;

        Item item = stack.getItem();
        cooldowns.tryStartLocalCooldown(item, ItemCooldownRulesValue.Trigger.CONSUME_FINISH);
    }

    @Inject(method = "updatingUsingItem", at = @At("HEAD"), cancellable = true)
    private void combatant$fixItemUseSpeed(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) {
            combatant$itemUseRemainder = 0f;
            return;
        }

        if (!player.level().isClientSide()) return;
        if (!player.isUsingItem()) {
            combatant$itemUseRemainder = 0f;
            return;
        }

        float scale = combatant$clientTickScale();
        if (scale >= 0.999f) {
            combatant$itemUseRemainder = 0f;
            return;
        }

        combatant$itemUseRemainder += scale;
        if (combatant$itemUseRemainder < 1.0f) {
            ci.cancel();
            return;
        }

        combatant$itemUseRemainder -= 1.0f;
    }

    @Redirect(
            method = "tickEffects",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/effect/MobEffectInstance;tickClient()V"
            )
    )
    private void combatant$scaleClientEffects(MobEffectInstance instance) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide()) {
            instance.tickClient();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (!(self instanceof Player player) || mc == null || player != mc.player) {
            instance.tickClient();
            return;
        }

        float scale = combatant$clientTickScale();
        if (scale >= 0.999f) {
            combatant$effectTickRemainder = 0f;
            instance.tickClient();
            return;
        }

        combatant$effectTickRemainder += scale;
        if (combatant$effectTickRemainder < 1.0f) {
            return;
        }
        combatant$effectTickRemainder -= 1.0f;
        instance.tickClient();
    }

    @Unique
    private float combatant$clientTickScale() {
        float mult = Timer.getTickTimer();
        if (mult <= 0.0001f) return 1.0f;

        float serverDelta = 1.0f;
        TPSSync tps = Modules.get(TPSSync.class);
        if (tps != null && tps.isEnabled()) {
            serverDelta = tps.getServerTickDelta();
        }

        float scale = serverDelta / mult;
        if (scale > 1.0f) return 1.0f;
        return Math.max(scale, 0.0f);
    }

    /**
     * Local totem cooldowns are handled from the confirmed entity-status packet when explicitly configured.
     */
    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void combatant$onTotemUse(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        // no local use blocking or prediction here
    }

    /**
     * Сброс кулдаунов при смерти.
     */
    @Inject(method = "die", at = @At("HEAD"))
    private void combatant$onDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player) {
            TotemPopCounter.onPlayerDeath(player.getUUID());
        }

        PvpCooldowns cooldowns = Modules.get(PvpCooldowns.class);
        if (cooldowns == null || cooldowns.isSystemEnabled()) {
            CooldownsState.MANAGER.clear();
        }
    }

    @Inject(
            method = "getBlockSpeedFactor",
            at = @At("HEAD"),
            cancellable = true
    )
    private void weathercontrol$dontDampRiptide(CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (!(self instanceof Player player)) return;
        if (!player.isAutoSpinAttack()) return;

        Level world = player.level();
        if (world == null) return;

        if (WorldTweaks.isServerRaining(world)) {
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void preventCrash(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (!(self instanceof Player p)) return;
        ElytraFly elytraFly = Modules.get(ElytraFly.class);
        if (elytraFly == null || !elytraFly.isEnabled()) return;
        if (!elytraFly.noCrash.get()) return;
        if (!p.isFallFlying()) return;

        Vec3 vel = p.getDeltaMovement();

        if (vel.horizontalDistance() < 1) return;
        AABB next = p.getBoundingBox().move(vel);

        if (!p.level().noCollision(next)) {
            p.setDeltaMovement(0, 0, 0);
        }
    }

    @ModifyExpressionValue(
            method = "updateFallFlyingMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 combatant$modifyGlidingRotationVector(Vec3 original) {
        LivingEntity self = (LivingEntity) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (!(self instanceof LocalPlayer player) || mc == null || player != mc.player) {
            return original;
        }

        Rotation rotation = RotationManager.INSTANCE.getMovementRotation();
        var rotationTarget = RotationManager.INSTANCE.getActiveRotationTarget();
        if (rotationTarget == null
                || rotationTarget.movementCorrection == MovementCorrection.OFF
                || rotationTarget.movementCorrection == MovementCorrection.STRICT) {
            return original;
        }

        return rotation.directionVector();
    }

    @ModifyExpressionValue(
            method = "updateFallFlyingMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"
            )
    )
    private float combatant$modifyGlidingPitch(float original) {
        LivingEntity self = (LivingEntity) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (!(self instanceof LocalPlayer player) || mc == null || player != mc.player) {
            return original;
        }

        Rotation rotation = RotationManager.INSTANCE.getMovementRotation();
        var rotationTarget = RotationManager.INSTANCE.getActiveRotationTarget();
        if (rotationTarget == null || rotationTarget.movementCorrection == MovementCorrection.OFF) {
            return original;
        }

        return rotation.pitch();
    }

    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void killHasEffect(Holder<MobEffect> effect,
                               CallbackInfoReturnable<Boolean> cir) {
        NoRender noRender = Modules.get(NoRender.class);
        if (noRender == null || !noRender.off("bad_effects")) return;

        if (isBad(effect)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getEffect", at = @At("HEAD"), cancellable = true)
    private void killGetEffect(Holder<MobEffect> effect,
                               CallbackInfoReturnable<MobEffectInstance> cir) {
        NoRender noRender = Modules.get(NoRender.class);
        if (noRender == null || !noRender.off("bad_effects")) return;

        if (isBad(effect)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void combatant$onJumpHead(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof LocalPlayer player)) {
            combatant$playerJumpEvent = null;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || player != mc.player) {
            combatant$playerJumpEvent = null;
            return;
        }

        PlayerJumpEvent event = new PlayerJumpEvent(self.getYRot(), getJumpPower());
        Events.BUS.post(event);
        if (event.isCancelled()) {
            combatant$playerJumpEvent = null;
            ci.cancel();
            return;
        }

        combatant$playerJumpEvent = event;
    }

    @ModifyExpressionValue(
            method = "jumpFromGround",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getJumpPower()F"
            )
    )
    private float combatant$modifyJumpMotion(float original) {
        return combatant$playerJumpEvent != null ? combatant$playerJumpEvent.getMotion() : original;
    }

    @ModifyExpressionValue(
            method = "jumpFromGround",
            at = @At(
                    value = "NEW",
                    target = "(DDD)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 combatant$fixJumpRotation(Vec3 original) {
        LivingEntity self = (LivingEntity) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (!(self instanceof LocalPlayer player) || mc == null || player != mc.player) {
            return original;
        }

        Rotation rotation = RotationManager.INSTANCE.getMovementRotation();
        var rotationTarget = RotationManager.INSTANCE.getActiveRotationTarget();
        if (rotationTarget == null || rotationTarget.movementCorrection == MovementCorrection.OFF) {
            return original;
        }

        float yaw = rotation.yaw() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw) * 0.2F, 0.0, Mth.cos(yaw) * 0.2F);
    }

    @Redirect(
            method = "jumpFromGround",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"
            )
    )
    private float combatant$omniSprintJumpYaw(LivingEntity instance) {
        return combatant$playerJumpEvent != null ? combatant$playerJumpEvent.getYaw() : instance.getYRot();
    }

    @Inject(method = "jumpFromGround", at = @At("RETURN"))
    private void combatant$onJumpReturn(CallbackInfo ci) {
        combatant$playerJumpEvent = null;
    }

    @Unique
    private boolean isBad(Holder<MobEffect> entry) {
        MobEffect eff = entry.value();
        return eff == MobEffects.BLINDNESS.value()
                || eff == MobEffects.DARKNESS.value()
                || eff == MobEffects.NAUSEA.value();
    }

    @Inject(
            method = "getBlockSpeedFactor",
            at = @At("RETURN"),
            cancellable = true
    )
    private void nostun$soulSandVelocity(CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || player != client.player) return;

        NoStun ns = Modules.get(NoStun.class);
        if (ns == null || !ns.isEnabled()) return;
        if (ns.isVulcan297()) {
            BlockPos pos = player.blockPosition();
            BlockState state = player.level().getBlockState(pos);
            if (state.is(Blocks.SOUL_SAND)) {
                cir.setReturnValue(ns.getSoulSandVelocityMultiplier(cir.getReturnValue()));
            }
            return;
        }

        // общий тумблер env
        if (!ns.isFunctionEnabled(NoStun.fnEnvBlocks())) return;

        // конкретный тумблер песка душ
        if (!ns.isEnvBlockEnabled(NoStun.envSoulSand())) return;

        BlockPos pos = player.blockPosition();
        BlockState state = player.level().getBlockState(pos);

        if (!state.is(Blocks.SOUL_SAND)) return;

        float vanilla = cir.getReturnValue();

        if (vanilla < 1.0F) {
            cir.setReturnValue(1.0F);
        }
    }

}
