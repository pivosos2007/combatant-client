/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.events.Events;
import combatant.client.events.impl.PlayerCollisionRestitutionEvent;
import combatant.client.events.impl.PlayerMoveEvent;
import combatant.client.events.impl.PlayerEffectMoveEvent;
import combatant.client.events.impl.ElytraTravelEvent;
import combatant.client.events.impl.PlayerStepSuccessEvent;
import combatant.client.events.impl.PlayerVelocityStrafe;
import combatant.client.events.impl.VehicleMoveEvent;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.NoPush;
import combatant.client.features.module.modules.movement.NoStun;
import combatant.client.mixininterface.IEntity;
import combatant.client.mixins.accessors.EntityAccessor;
import combatant.client.mixins.accessors.EntityInvoker;
import combatant.client.render.helpers.TrailPoint;

import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
public abstract class EntityMixin implements IEntity {

    @Unique
    private final List<TrailPoint> trails = new ArrayList<>();
    @Shadow
    public double xOld;
    @Shadow
    public double yOld;
    @Shadow
    public double zOld;
    @Unique
    private MoverType combatant$lastMoveType = MoverType.SELF;
    @Unique
    private Vec3 combatant$stepBeforePos = Vec3.ZERO;
    @Unique
    private Vec3 combatant$stepRequestedMovement = Vec3.ZERO;
    @Unique
    private boolean combatant$stepWasOnGround = false;

    @Unique
    @Override
    public Vec3 get$InstantRenderPos() {
        return new Vec3(xOld, yOld, zOld);
    }

    @Unique
    @Override
    public List<TrailPoint> combatant$getTrails() {
        return trails;
    }

    // Minecraft 26.3 applies block bounce impulses inside Entity collision restitution.
    // Keep the mixin generic: consumers decide which block/state should modify the final velocity.
    @Inject(method = "restituteMovementAfterCollisions", at = @At("TAIL"))
    private void combatant$afterCollisionRestitution(BlockState state, boolean xCollision,
                                                     boolean zCollision, Vec3 attemptedMovement,
                                                     CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof LocalPlayer player)
                || !Events.BUS.hasListeners(PlayerCollisionRestitutionEvent.class)) return;

        Vec3 velocity = self.getDeltaMovement();
        PlayerCollisionRestitutionEvent event = Events.BUS.post(new PlayerCollisionRestitutionEvent(
                player, state, xCollision, zCollision, self.verticalCollisionBelow,
                attemptedMovement, velocity));
        Vec3 adjustedVelocity = event.getVelocity();
        if (adjustedVelocity != null && !adjustedVelocity.equals(velocity)) {
            self.setDeltaMovement(adjustedVelocity);
        }
    }

    @Inject(method = "move", at = @At("HEAD"))
    private void combatant$cacheMoveType(MoverType type, Vec3 movement, CallbackInfo ci) {
        combatant$lastMoveType = type;
        Entity self = (Entity) (Object) this;
        if (self instanceof LocalPlayer) {
            combatant$stepBeforePos = self.position();
            combatant$stepRequestedMovement = movement;
            combatant$stepWasOnGround = self.onGround();
        }
    }

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, index = 2)
    private Vec3 combatant$onMove(Vec3 movement) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof LocalPlayer player)) return combatant$onVehicleMove(self, movement);

        Vec3 out = movement;
        if (Events.BUS.hasListeners(PlayerMoveEvent.class)) {
            PlayerMoveEvent event = Events.BUS.post(new PlayerMoveEvent(combatant$lastMoveType, out));
            if (event.getMovement() != null) out = event.getMovement();
        }
        if (combatant$lastMoveType == MoverType.SELF && !player.isPassenger()
                && !player.isFallFlying() && Events.BUS.hasListeners(PlayerEffectMoveEvent.class)) {
            PlayerEffectMoveEvent.EffectType effect = player.hasEffect(MobEffects.LEVITATION)
                    ? PlayerEffectMoveEvent.EffectType.LEVITATION : player.hasEffect(MobEffects.SLOW_FALLING)
                    ? PlayerEffectMoveEvent.EffectType.SLOW_FALLING : null;
            if (effect != null) {
                PlayerEffectMoveEvent event = Events.BUS.post(new PlayerEffectMoveEvent(player, effect, out));
                Vec3 adjusted = event.getMovement();
                if (adjusted != null && Double.isFinite(adjusted.x)
                        && Double.isFinite(adjusted.y) && Double.isFinite(adjusted.z)) {
                    if (!adjusted.equals(out)) player.setDeltaMovement(adjusted);
                    out = adjusted;
                }
            }
        }
        if (combatant$lastMoveType == MoverType.SELF && player.isFallFlying()
                && Events.BUS.hasListeners(ElytraTravelEvent.class)) {
            ElytraTravelEvent event = Events.BUS.post(new ElytraTravelEvent(player, out));
            Vec3 velocity = event.getVelocity();
            if (velocity != null && Double.isFinite(velocity.x)
                    && Double.isFinite(velocity.y) && Double.isFinite(velocity.z)) {
                if (!velocity.equals(out)) player.setDeltaMovement(velocity);
                out = velocity;
            }
        }
        return out;
    }

    @Unique
    private Vec3 combatant$onVehicleMove(Entity self, Vec3 movement) {
        if (combatant$lastMoveType != MoverType.SELF || !self.level().isClientSide()) return movement;
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || player.getVehicle() != self || self.getControllingPassenger() != player) return movement;
        if (!Events.BUS.hasListeners(VehicleMoveEvent.class)) return movement;

        VehicleMoveEvent event = new VehicleMoveEvent(self, combatant$lastMoveType, movement);
        Events.BUS.post(event);
        Vec3 out = event.getMovement();
        if (out == null || out == movement) return movement;
        if (event.isAliasesVelocity()) self.setDeltaMovement(out);
        return out;
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void combatant$onMoveReturn(MoverType type, Vec3 movement, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof LocalPlayer)) return;
        if (!Events.BUS.hasListeners(PlayerStepSuccessEvent.class)) return;

        Vec3 before = combatant$stepBeforePos;
        Vec3 after = self.position();
        Vec3 requested = combatant$stepRequestedMovement;
        double yDelta = after.y - before.y;
        double horizontalRequested = Math.hypot(requested.x, requested.z);
        if (yDelta <= 0.5 || horizontalRequested <= 1.0E-5) return;
        if (!combatant$stepWasOnGround) return;
        if (requested.y > 0.05) return;

        Events.BUS.post(new PlayerStepSuccessEvent(after.subtract(before), before, after));
    }

    @Redirect(
            method = "moveRelative",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getInputVector(Lnet/minecraft/world/phys/Vec3;FF)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 combatant$velocityStrafe(Vec3 movementInput, float speed, float yaw) {
        Entity self = (Entity) (Object) this;
        Vec3 vanilla = EntityInvoker.combatant$movementInputToVelocity(movementInput, speed, yaw);
        if (!(self instanceof LocalPlayer)) return vanilla;
        if (!Events.BUS.hasListeners(PlayerVelocityStrafe.class)) return vanilla;

        PlayerVelocityStrafe event = new PlayerVelocityStrafe(movementInput, speed, yaw, vanilla);
        Events.BUS.post(event);
        return event.getVelocity();
    }

    @Redirect(
            method = "push(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;push(DDD)V"
            )
    )
    private void combatant$cancelEntityPush(Entity instance, double x, double y, double z) {
        NoPush noPush = Modules.get(NoPush.class);
        if (noPush == null || !noPush.off("players")) {
            instance.push(x, y, z);
        }
    }

    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void nostun$cancelSlowMovement(BlockState state, Vec3 multiplier, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof LocalPlayer)) return;

        Vec3 webOverride = combatant.client.features.module.modules.movement.NoWeb.stuckOverride(state, multiplier);
        if (webOverride != null && self instanceof EntityAccessor accessor) {
            self.resetFallDistance();
            accessor.combatant$setMovementMultiplier(webOverride);
            ci.cancel();
            return;
        }

        NoStun ns = Modules.get(NoStun.class);
        if (ns == null || !ns.isEnabled()) return;

        Vec3 override = ns.getSlowMovementOverride(state, multiplier);
        if (override != null && self instanceof EntityAccessor accessor) {
            self.resetFallDistance();
            accessor.combatant$setMovementMultiplier(override);
            ci.cancel();
            return;
        }

        if (ns.shouldCancelSlowMovement(state)) {
            ci.cancel();
        }
    }

}
