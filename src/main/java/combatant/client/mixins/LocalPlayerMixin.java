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
 * Parts of sprint control, silent rotations, and movement-input correction
 * are adapted from LiquidBounce (https://github.com/CCBlueX/LiquidBounce).
 * Original copyright (c) CCBlueX.
 */

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import combatant.client.events.impl.*;
import combatant.client.features.module.modules.movement.*;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import combatant.client.util.combat.SprintController;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import combatant.client.events.Events;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.player.XCarry;
import combatant.client.features.module.modules.visuals.Freecam;
import combatant.client.mixininterface.ILocalPlayer;
import combatant.client.mixins.accessors.EntityAccessor;
import combatant.client.mixins.accessors.InputAccessor;
import combatant.client.mixins.accessors.LocalPlayerAccessor;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.RotationUtil;

import java.util.ArrayList;
import java.util.List;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin implements ILocalPlayer {

    @Inject(method = "getJumpRidingScale", at = @At("RETURN"), cancellable = true)
    private void combatant$mountJumpCharge(CallbackInfoReturnable<Float> cir) {
        if (!Events.BUS.hasListeners(MountJumpChargeEvent.class)) return;
        MountJumpChargeEvent event = Events.BUS.post(new MountJumpChargeEvent(
                (LocalPlayer) (Object) this, cir.getReturnValue()));
        float charge = event.getCharge();
        if (Float.isFinite(charge)) cir.setReturnValue(Mth.clamp(charge, 0.0f, 1.0f));
    }

    @ModifyArgs(method = "rideTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;setInput(ZZZZ)V"))
    private void combatant$entityControlBoatStrafe(Args args) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (!(self.getVehicle() instanceof AbstractBoat boat)) return;
        EntityControl control = Modules.get(EntityControl.class);
        if (control == null || !control.useBoatStrafing(self, boat)) return;
        args.set(0, false);
        args.set(1, false);
    }

    @ModifyExpressionValue(
            method = "modifyInput",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;itemUseSpeedMultiplier()F")
    )
    private float combatant$noSlowNeutralize(float original) {
        NoSlow noSlow = Modules.get(NoSlow.class);
        if (noSlow != null && noSlow.shouldModifyInput((LocalPlayer) (Object) this)) return 1.0f;
        return original;
    }

    @Inject(method = "modifyInput", at = @At("RETURN"), cancellable = true)
    private void combatant$noSlowApply(Vec2 input, CallbackInfoReturnable<Vec2> cir) {
        NoSlow noSlow = Modules.get(NoSlow.class);
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (noSlow == null || !noSlow.shouldModifyInput(self)) return;
        cir.setReturnValue(noSlow.getModifiedInput(cir.getReturnValue(), self));
    }

    @Shadow
    public ClientInput input;
    @Unique
    private float combatant$lastYaw;
    @Unique
    private float combatant$lastPitch;
    @Unique
    private double combatant$rotationPrevX;
    @Unique
    private double combatant$rotationPrevZ;
    @Unique
    private float combatant$rotationPrevBodyYaw;
    @Unique
    private boolean combatant$rotationBodyYawInitialized;
    @Unique
    private float freecam$lastHealth = Float.NaN;
    @Unique
    private boolean frozen = false;
    @Unique
    private Vec3 savedVelocity = Vec3.ZERO;
    @Unique
    private double savedX, savedY, savedZ;
    @Unique
    private float savedBodyYaw;
    @Unique
    private float savedHeadYaw;
    @Unique
    private Runnable combatant$eventPostAction;
    @Unique
    private EventSync combatant$activeSyncEvent;
    @Unique
    private boolean combatant$postUpdateLock = false;
    @Unique
    private Input lastInput;
    @Unique
    private boolean pendingSpoof = false;

    @Unique
    private static boolean combatant$sameInput(Input a, Input b) {
        if (a == b) return true;
        if (a == null || b == null) return false;

        return a.forward() == b.forward()
                && a.backward() == b.backward()
                && a.left() == b.left()
                && a.right() == b.right()
                && a.jump() == b.jump()
                && a.shift() == b.shift()
                && a.sprint() == b.sprint();
    }

    @Unique
    private static float combatant$impulse(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0f;
        }
        return positive ? 1.0f : -1.0f;
    }

    @Unique
    private static float combatant$simulateBodyYaw(
            float yaw,
            float prevBodyYaw,
            double prevX,
            double prevZ,
            double currentX,
            double currentZ,
            float handSwingProgress
    ) {
        double motionX = currentX - prevX;
        double motionZ = currentZ - prevZ;
        float motionSquared = (float) (motionX * motionX + motionZ * motionZ);
        float bodyYaw = prevBodyYaw;

        if (motionSquared > 0.0025000002F) {
            float movementYaw = (float) Mth.atan2(motionZ, motionX) * Mth.RAD_TO_DEG - 90.0F;
            float yawDiff = Math.abs(Mth.wrapDegrees(yaw) - movementYaw);
            bodyYaw = (yawDiff > 95.0F && yawDiff < 265.0F) ? movementYaw - 180.0F : movementYaw;
        }

        if (handSwingProgress - 0.2F > 0.0F) {
            bodyYaw = yaw;
        }

        float deltaYaw = Mth.wrapDegrees(bodyYaw - prevBodyYaw);
        bodyYaw = prevBodyYaw + deltaYaw * 0.3F;

        float yawOffsetDiff = Mth.wrapDegrees(yaw - bodyYaw);
        float maxHeadRotation = 52.0F;
        if (Math.abs(yawOffsetDiff) > maxHeadRotation) {
            bodyYaw += yawOffsetDiff - Math.signum(yawOffsetDiff) * maxHeadRotation;
        }

        return bodyYaw;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void combatant$cacheLastRotations(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        combatant$lastYaw = self.getYRot();
        combatant$lastPitch = self.getXRot();
        RotationManager.INSTANCE.runTickLifecycle();
        if (!combatant$rotationBodyYawInitialized) {
            combatant$rotationPrevX = self.getX();
            combatant$rotationPrevZ = self.getZ();
            combatant$rotationPrevBodyYaw = self.getVisualRotationYInDegrees();
            combatant$rotationBodyYawInitialized = true;
        }
    }

    @Inject(method = "handlePortalTransitionEffect", at = @At("HEAD"), cancellable = true)
    private void combatant$disablePortalNausea(boolean inPortal, CallbackInfo ci) {
        if (RuntimeGate.isPanic()) return;
        LocalPlayer self = (LocalPlayer) (Object) this;
        PortalProcessor portalManager = null;
        if (self instanceof EntityAccessor accessor) {
            portalManager = accessor.combatant$getPortalManager();
        }
        if (portalManager != null && portalManager.isInsidePortalThisTick()) {
            if (self instanceof LocalPlayerAccessor accessor) {
                accessor.combatant$setNauseaIntensity(0.0f);
                accessor.combatant$setLastNauseaIntensity(0.0f);
            }
            ci.cancel();
        }
    }

    @Override
    public float combatant$getLastYaw() {
        return combatant$lastYaw;
    }

    @Override
    public float combatant$getLastPitch() {
        return combatant$lastPitch;
    }

    @Override
    public void combatant$setLastYaw(float yaw) {
        this.combatant$lastYaw = yaw;
    }

    @Override
    public void combatant$setLastPitch(float pitch) {
        this.combatant$lastPitch = pitch;
    }

    @ModifyExpressionValue(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;canStartSprinting()Z"
            )
    )
    private boolean combatant$sprintMovementStart(boolean original) {
        return combatant$postSprintControl(original, SprintControlEvent.Source.MOVEMENT_TICK);
    }

    @ModifyExpressionValue(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Input;sprint()Z"
            )
    )
    private boolean combatant$sprintMovementInput(boolean original) {
        return combatant$postSprintControl(original, SprintControlEvent.Source.MOVEMENT_TICK);
    }

    @ModifyExpressionValue(
            method = "sendIsSprintingIfNeeded",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;isSprinting()Z"
            )
    )
    private boolean combatant$sprintNetwork(boolean original) {
        if (SprintController.INSTANCE.isSprintBlocked()) {
            return false;
        }

        return original;
    }

    @Unique
    private boolean combatant$postSprintControl(boolean original, SprintControlEvent.Source source) {
        LocalPlayer self = (LocalPlayer) (Object) this;

        if (self.input == null) {
            return original;
        }

        if (!Events.BUS.hasListeners(SprintControlEvent.class)) {
            return original;
        }

        Vec2 movement = self.input.getMoveVector();
        SprintControlEvent event = new SprintControlEvent(
                movement.y,
                movement.x,
                original,
                source
        );
        Events.BUS.post(event);
        return event.shouldSprint();
    }

    @Inject(method = "modifyInput", at = @At("RETURN"), cancellable = true)
    private void boostWhenUsing(Vec2 input, CallbackInfoReturnable<Vec2> cir) {
        NoStun ns = Modules.get(NoStun.class);
        if (ns == null || !ns.isEnabled()) return;
        if (!ns.isFunctionEnabled(NoStun.fnUseSpeed())) return;

        LocalPlayer player = (LocalPlayer) (Object) this;
        // NoSlow already lifted the slowdown; boosting on top of that would walk faster than not eating at all.
        NoSlow noSlow = Modules.get(NoSlow.class);
        if (noSlow != null && noSlow.shouldModifyInput(player)) return;
        if (player.isUsingItem() && !player.isPassenger()) {
            Vec2 base = cir.getReturnValue();

            // 0.0 -> vanilla (no change), 1.0 -> normal walking speed.
            // Vanilla item-use slowdown is typically ~0.2x; lerp 1..5 approximates reciprocal compensation.
            float t = ns.getEatSpeed01();
            float mult = 1.0f + t * 4.0f;

            if (mult > 1.001f) {
                cir.setReturnValue(base.scale(mult));
            }
        }
    }

    @Shadow
    protected abstract void sendPosition();

    @Inject(method = "applyInput", at = @At("HEAD"), cancellable = true)
    private void freecam$blockWASD(CallbackInfo ci) {
        Freecam fc = Modules.get(Freecam.class);
        if (fc != null && fc.isEnabled() && fc.isCameraInput()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/ClientInput;tick()V",
                    shift = At.Shift.AFTER
            )
    )
    private void freecam$afterInputTick(CallbackInfo ci) {
        Freecam fc = Modules.get(Freecam.class);
        if (fc == null || !fc.isEnabled() || !fc.isCameraInput()) return;

        ((InputAccessor) this.input).setMovementVector(Vec2.ZERO);

        boolean sneak = false;
        boolean sprint = this.input.keyPresses.sprint();

        this.input.keyPresses = new Input(
                false, false, false, false,
                false,
                sneak,
                sprint
        );
    }

    @Inject(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/ClientInput;tick()V",
                    shift = At.Shift.AFTER
            )
    )
    private void combatant$afterInputTickSprint(CallbackInfo ci) {
        if (this.input == null) return;

        Input original = this.input.keyPresses;
        if (original == null) return;

        Input input = original;

        if (Events.BUS.hasListeners(MovementInputEvent.class)) {
            MovementInputEvent event = new MovementInputEvent(input);
            Events.BUS.post(event);

            Input eventInput = event.toPlayerInput();
            if (!combatant$sameInput(input, eventInput)) {
                input = eventInput;
            }
        }

        Input transformedInput = combatant$transformDirection(input);
        if (!combatant$sameInput(input, transformedInput)) {
            input = transformedInput;
        }

        if (Events.BUS.hasListeners(SprintControlEvent.class)) {
            float forward = combatant$impulse(input.forward(), input.backward());
            float sideways = combatant$impulse(input.left(), input.right());

            SprintControlEvent event = new SprintControlEvent(
                    forward,
                    sideways,
                    input.sprint(),
                    SprintControlEvent.Source.INPUT
            );
            Events.BUS.post(event);

            if (event.shouldSprint() != input.sprint()) {
                input = new Input(
                        input.forward(),
                        input.backward(),
                        input.left(),
                        input.right(),
                        input.jump(),
                        input.shift(),
                        event.shouldSprint()
                );
            }
        }

        if (!combatant$sameInput(original, input)) {
            combatant$applyPlayerInput(input);
        }
    }

    @Unique
    private void combatant$applyPlayerInput(Input input) {
        if (this.input == null || input == null) {
            return;
        }

        InputAccessor accessor = (InputAccessor) this.input;
        accessor.setPlayerInput(input);

        float forward = combatant$impulse(input.forward(), input.backward());
        float sideways = combatant$impulse(input.left(), input.right());

        accessor.setMovementVector(new Vec2(sideways, forward).normalized());
    }

    @Unique
    private Input combatant$transformDirection(Input input) {
        // Post-input hook remains inert to avoid double-transforming movement input.
        return input;
    }

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Input;shift()Z",
                    ordinal = 0
            )
    )
    private boolean freecam$noFlyDown0(Input in) {
        Freecam fc = Modules.get(Freecam.class);
        if (fc != null && fc.isEnabled() && fc.isCameraInput()) {
            LocalPlayer self = (LocalPlayer) (Object) this;
            if (self.getAbilities().flying) return false;
        }
        return in.shift();
    }

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Input;shift()Z",
                    ordinal = 1
            ),
            require = 0
    )
    private boolean freecam$noFlyDown1(Input in) {
        Freecam fc = Modules.get(Freecam.class);
        if (fc != null && fc.isEnabled() && fc.isCameraInput()) {
            LocalPlayer self = (LocalPlayer) (Object) this;
            if (self.getAbilities().flying) return false;
        }
        return in.shift();
    }

    @Inject(method = "hurtTo", at = @At("HEAD"))
    private void freecam$onUpdateHealth(float newHealth, CallbackInfo ci) {
        Freecam fc = Modules.get(Freecam.class);

        if (fc == null || !fc.isEnabled() || !fc.disableOnDamage()) {
            freecam$lastHealth = newHealth;
            return;
        }

        if (!Float.isNaN(freecam$lastHealth) && newHealth < freecam$lastHealth) {
            fc.toggle();
        }

        freecam$lastHealth = newHealth;
    }

    @Unique
    private boolean combatant$moduleFreezeRequested() {
        if (!Events.BUS.hasListeners(PlayerFreezeQueryEvent.class)) return false;
        return Events.BUS.post(new PlayerFreezeQueryEvent((LocalPlayer) (Object) this)).isFrozen();
    }

    @Unique
    private boolean shouldFreezeNow() {
        if (combatant$moduleFreezeRequested()) return true;
        Freecam freecam = Modules.get(Freecam.class);
        if (freecam == null || !freecam.freezePlayer() || !freecam.isCameraInput()) return false;
        // Freecam keeps sending movement packets, so the server simulates every tick. Hold the player only where
        // vanilla physics holds it too (standing with no horizontal motion), never in mid-air or mid-slide.
        LocalPlayer self = (LocalPlayer) (Object) this;
        Vec3 motion = self.getDeltaMovement();
        return frozen || (self.onGround() && motion.x == 0.0 && motion.z == 0.0);
    }

    /**
     * Vanilla sends movement packets only while the player is the camera. Freecam views through its own entity,
     * and a player that stops reporting while still answering transactions is flagged (GrimAC BadPacketsR), so the
     * player keeps reporting like vanilla while Freecam is on.
     */
    @Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
    private void combatant$freecamKeepsReporting(CallbackInfoReturnable<Boolean> cir) {
        Freecam freecam = Modules.get(Freecam.class);
        if (freecam != null && freecam.isEnabled() && freecam.camEntity != null
                && Minecraft.getInstance().getCameraEntity() == freecam.camEntity) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void combatant$eventSync(CallbackInfo ci) {
        if (!Events.BUS.hasListeners(EventSync.class)) return;
        combatant$eventPostAction = null;
        LocalPlayer self = (LocalPlayer) (Object) this;
        EventSync event = new EventSync(self.getYRot(), self.getXRot());
        Events.BUS.post(event);
        combatant$activeSyncEvent = event;
        combatant$eventPostAction = event.getPostAction();
        if (event.isCancelled()) {
            combatant$activeSyncEvent = null;
            combatant$eventPostAction = null;
            ci.cancel();
        }
    }

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void combatant$eventPostSync(CallbackInfo ci) {
        if (Events.BUS.hasListeners(EventPostSync.class)) {
            Events.BUS.post(new EventPostSync());
        }
        if (combatant$eventPostAction != null) {
            combatant$eventPostAction.run();
            combatant$eventPostAction = null;
        }
        combatant$activeSyncEvent = null;
    }

    @ModifyExpressionValue(
            method = "sendPosition",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"
            )
    )
    private float combatant$eventSyncYaw(float original) {
        combatant$updateVisualBodyYaw();

        EventSync event = combatant$activeSyncEvent;
        boolean overridden = event != null && event.hasRotationOverride();
        Rotation rotation = RotationManager.INSTANCE.getCurrentRotation();
        float targetYaw = overridden ? event.getYaw() : rotation != null ? rotation.yaw() : original;
        // The event override is the normal path for RotationManager.onSync(). It must not bypass
        // yaw unwrapping: a target switching from +179 to -179 is a two-degree turn, not a
        // -358-degree packet delta (for instance, Grim AimModulo360 checks the raw, unwrapped delta).
        Rotation server = RotationManager.INSTANCE.getServerRotation();
        float referenceYaw = server != null && server != Rotation.ZERO ? server.yaw() : original;
        return RotationUtil.withFixedYaw(targetYaw, referenceYaw);
    }

    @ModifyExpressionValue(
            method = "sendPosition",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"
            )
    )
    private float combatant$eventSyncPitch(float original) {
        EventSync event = combatant$activeSyncEvent;
        if (event != null && event.hasRotationOverride()) return Mth.clamp(event.getPitch(), -90.0f, 90.0f);

        Rotation rotation = RotationManager.INSTANCE.getCurrentRotation();
        return rotation != null ? Mth.clamp(rotation.pitch(), -90.0f, 90.0f) : original;
    }

    @Inject(
            method = "applyInput",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"
            )
    )
    private void combatant$updateBodyYawOnInput(CallbackInfo ci) {
        // applyInput's yaw/pitch reads drive first-person hand sway and must retain camera angles.
        combatant$updateVisualBodyYaw();
    }

    @Inject(
            method = "sendChanges",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;sendPosition()V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void combatant$postPlayerUpdate(CallbackInfo ci) {
        if (!Events.BUS.hasListeners(PostPlayerUpdateEvent.class)) return;
        if (combatant$postUpdateLock) return;

        PostPlayerUpdateEvent event = new PostPlayerUpdateEvent();
        Events.BUS.post(event);

        if (!event.isCancelled()) return;

        ci.cancel();

        int iterations = event.getIterations();
        if (iterations <= 0) return;

        LocalPlayer self = (LocalPlayer) (Object) this;
        for (int i = 0; i < iterations; i++) {
            combatant$postUpdateLock = true;
            self.tick();
            combatant$postUpdateLock = false;
            sendPosition();
        }
    }

    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void cancelPackets(CallbackInfo ci) {
        if (combatant$moduleFreezeRequested()) {
            ci.cancel();
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void combatant$freezeTickMovement(CallbackInfo ci) {
        LocalPlayer p = (LocalPlayer) (Object) this;

        if (frozen && (p.getX() != savedX || p.getY() != savedY || p.getZ() != savedZ)) {
            frozen = false;
        }

        if (!shouldFreezeNow()) {
            if (frozen) {
                p.setDeltaMovement(savedVelocity);
                frozen = false;
            }
            return;
        }

        if (!frozen) {
            frozen = true;
            savedX = p.getX();
            savedY = p.getY();
            savedZ = p.getZ();
            savedVelocity = p.getDeltaMovement();

            if (Modules.get(Freecam.class) == null || !Modules.get(Freecam.class).isEnabled()) {
                savedBodyYaw = p.yBodyRot;
                savedHeadYaw = p.yHeadRot;
            }
        }

        p.setDeltaMovement(Vec3.ZERO);
        p.absSnapTo(savedX, savedY, savedZ);

        if (Modules.get(Freecam.class) == null || !Modules.get(Freecam.class).isEnabled()) {
            p.yBodyRot = savedBodyYaw;
            p.yHeadRot = savedHeadYaw;
        }

        ci.cancel();
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void combatant$noStunTickMovement(CallbackInfo ci) {
        NoStun ns = Modules.get(NoStun.class);
        if (ns == null || !ns.isEnabled()) return;

        Freecam fc = Modules.get(Freecam.class);
        if (fc != null && fc.isEnabled() && fc.isCameraInput()) {
            return;
        }

        LocalPlayer player = (LocalPlayer) (Object) this;
        if (ns.isFunctionEnabled(NoStun.fnNoHurtStun())) {
            if (player.hurtTime == 0) {
                lastInput = player.input.keyPresses;
            } else if (lastInput != null) {
                player.input.keyPresses = lastInput;
            }
        }

    }

    @Unique
    private void combatant$updateVisualBodyYaw() {
        LocalPlayer self = (LocalPlayer) (Object) this;
        Rotation rotation = RotationManager.INSTANCE.getCurrentRotation();
        if (rotation == null) {
            combatant$rotationPrevX = self.getX();
            combatant$rotationPrevZ = self.getZ();
            combatant$rotationPrevBodyYaw = self.getVisualRotationYInDegrees();
            return;
        }

        float newBodyYaw = combatant$simulateBodyYaw(
                rotation.yaw(),
                combatant$rotationPrevBodyYaw,
                combatant$rotationPrevX,
                combatant$rotationPrevZ,
                self.getX(),
                self.getZ(),
                self.getSwingAnimation(1.0f)
        );

        self.setYBodyRot(newBodyYaw);
        combatant$rotationPrevBodyYaw = newBodyYaw;
        combatant$rotationPrevX = self.getX();
        combatant$rotationPrevZ = self.getZ();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void detectPearlImpact(CallbackInfo ci) {
        NoPush noPush = Modules.get(NoPush.class);
        if (noPush == null || !noPush.off("pearl_phase"))
            return;

        LocalPlayer p = (LocalPlayer) (Object) this;

        List<ThrownEnderpearl> pearls = new ArrayList<>();
        p.level().getEntities(
                EntityTypeTest.forClass(ThrownEnderpearl.class),
                p.getBoundingBox().inflate(1.5),
                e -> true,
                pearls
        );

        // Prepare the movement adjustment immediately before pearl teleportation.
        if (!pearls.isEmpty()) {
            pendingSpoof = true;
        }
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void sendSpoofAfterPearl(CallbackInfo ci) {
        NoPush noPush = Modules.get(NoPush.class);
        if (noPush == null || !noPush.off("pearl_phase"))
            return;

        if (!pendingSpoof) return;
        LocalPlayer p = (LocalPlayer) (Object) this;
        // Avoid spoof on ceiling-only collision; it can freeze upward movement.
        if (p.verticalCollision && !p.horizontalCollision) {
            pendingSpoof = false;
            return;
        }
        pendingSpoof = false;

        Vec3 pos = p.position();

        p.connection.send(new ServerboundMovePlayerPacket.Pos(
                pos.x,
                pos.y + 0.0313, 
                pos.z,
                p.onGround(),
                false   // horizontalCollision
        ));
    }

    @Inject(method = "modifyInput", at = @At("RETURN"), cancellable = true)
    private void combatant$inventoryMove$screenSlowdown(Vec2 input, CallbackInfoReturnable<Vec2> cir) {
        InventoryMove module = Modules.get(InventoryMove.class);
        if (module == null || !module.isEnabled()) return;
        float scale = module.screenMovementScale();
        if (scale < 1.0F) cir.setReturnValue(cir.getReturnValue().scale(scale));
    }

    @Inject(method = "closeContainer", at = @At("HEAD"), cancellable = true)
    private void combatant$closeContainer(CallbackInfo ci) {
        InventoryMove move = Modules.get(InventoryMove.class);
        if (move != null && move.isEnabled() && move.shouldCancelCloseHandledScreen()) {
            ci.cancel();
            return;
        }

        if (!Modules.enabled(XCarry.class)) return;

        // Cancel only when closing own inventory screen (PlayerScreenHandler) to retain crafting/cursor items
        var screen = ClientScreen.current(net.minecraft.client.Minecraft.getInstance());
        if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> handled
                && handled.getMenu() instanceof InventoryMenu) {
            ((LocalPlayer) (Object) this).clientSideCloseContainer(); // client-side close without sending packet
            ci.cancel(); // skip packet CloseHandledScreen
        }
    }

    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void combatant$noPushOutOfBlocks(double x, double z, CallbackInfo ci) {
        if (!Events.BUS.hasListeners(EventPushOutOfBlocks.class)) return;
        EventPushOutOfBlocks event = new EventPushOutOfBlocks(x, z);
        Events.BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

}
