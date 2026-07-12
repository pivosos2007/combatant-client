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
import combatant.client.events.impl.*;
import combatant.client.features.module.modules.movement.*;
import combatant.client.util.combat.SprintController;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import combatant.client.events.impl.*;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.*;
import combatant.client.features.module.modules.player.XCarry;
import combatant.client.features.module.modules.visuals.Freecam;
import combatant.client.mixininterface.ILocalPlayer;
import combatant.client.mixins.accessors.EntityAccessor;
import combatant.client.mixins.accessors.InputAccessor;
import combatant.client.mixins.accessors.LocalPlayerAccessor;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;

import java.util.ArrayList;
import java.util.List;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin implements ILocalPlayer {

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
    private int boostTicks = 0;
    @Unique
    private double boostX = 0, boostZ = 0;
    @Unique
    private boolean wasGround = true;

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
        if (player.isUsingItem() && !player.isPassenger()) {
            Vec2 base = cir.getReturnValue();

            // 0.0 -> vanilla (no change), 1.0 -> normal walking speed.
            // In vanilla, most item-use slowdowns are ~0.2x, so we approximate by lerping 1..5.
            float t = ns.getEatSpeed01();
            float mult = 1.0f + t * 4.0f;

            if (mult > 1.001f) {
                cir.setReturnValue(base.scale(mult));
            }
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void combatant$elytraFlyVanilla(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        ElytraFly elytraFly = Modules.get(ElytraFly.class);
        if (elytraFly == null || !elytraFly.shouldApplyVanillaHook(player)) {
            return;
        }

        Vec3 velocity = player.getDeltaMovement();
        Vec3 look = player.getViewVector(1.0f);
        float pitch = player.getXRot();

        velocity = velocity.scale(elytraFly.getVanillaDragFactor());
        velocity = velocity.add(look.scale(elytraFly.getVanillaForwardBoostFactor()));

        double lift = -Math.sin(Math.toRadians(pitch)) * elytraFly.getVanillaPitchLiftFactor();
        player.setDeltaMovement(velocity.add(0.0, lift, 0.0));
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
        // Free correction now runs at KeyboardInput.tick(), matching the input pipeline.
        // Keep this post-input hook inert to avoid double-transforming movement input.
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
    private boolean shouldFreezeNow() {
        boolean freezeModule = Modules.enabled(Freeze.class);
        Freecam freecam = Modules.get(Freecam.class);
        boolean freecamFreeze = freecam != null && freecam.freezePlayer() && freecam.isCameraInput();
        return freezeModule || freecamFreeze;
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
        if (event != null && event.hasRotationOverride()) return event.getYaw();

        Rotation rotation = RotationManager.INSTANCE.getCurrentRotation();
        return rotation != null ? rotation.yaw() : original;
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

    @ModifyExpressionValue(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"
            )
    )
    private float combatant$visualTickYaw(float original) {
        combatant$updateVisualBodyYaw();

        Rotation rotation = RotationManager.INSTANCE.getCurrentRotation();
        return rotation != null ? rotation.yaw() : original;
    }

    @ModifyExpressionValue(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"
            )
    )
    private float combatant$visualTickPitch(float original) {
        Rotation rotation = RotationManager.INSTANCE.getCurrentRotation();
        return rotation != null ? Mth.clamp(rotation.pitch(), -90.0f, 90.0f) : original;
    }

    @Inject(
            method = "tick",
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
        if (Modules.enabled(Freeze.class)) {
            ci.cancel();
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void combatant$freezeTickMovement(CallbackInfo ci) {
        LocalPlayer p = (LocalPlayer) (Object) this;

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

        // ---------------------------------
        // No stun (hurt-time input freeze)
        // ---------------------------------
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
                self.attackAnim
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

        // перл соприкасается → сейчас будет телепорт → готовим спуф
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
        // Avoid spoof when we only bonk a ceiling (causes freezes while looking up).
        if (p.verticalCollision && !p.horizontalCollision) {
            pendingSpoof = false;
            return;
        }
        pendingSpoof = false;

        Vec3 pos = p.position();

        p.connection.send(new ServerboundMovePlayerPacket.Pos(
                pos.x,
                pos.y + 0.0313, // легитная фаза
                pos.z,
                p.onGround(),
                false   // horizontalCollision
        ));
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void onCollideBoost(CallbackInfo ci) {

        Speed speed = Modules.get(Speed.class);
        if (speed == null || !speed.enabled("entity_boost"))
            return;

        LocalPlayer p = (LocalPlayer) (Object) this;

        // если буст в процессе — продолжаем применять постепенно
        if (boostTicks > 0) {
            boostTicks--;

            Vec3 vel = p.getDeltaMovement();
            double vx = vel.x + boostX;
            double vz = vel.z + boostZ;

            // лимит скорости
            double max = 0.48;
            double h = Math.hypot(vx, vz);
            if (h > max) {
                double s = max / h;
                vx *= s;
                vz *= s;
            }

            p.setDeltaMovement(vx, vel.y, vz);
            return;
        }

        // ищем сущности рядом
        List<Entity> nearby = p.level().getEntities(p, p.getBoundingBox().inflate(0.15), e -> e instanceof LivingEntity);
        if (nearby.isEmpty()) return;

        // направление движения
        Vec3 vel = p.getDeltaMovement();
        double dirX = vel.x;
        double dirZ = vel.z;

        if (dirX == 0 && dirZ == 0) return;

        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 1e-5) return;

        dirX /= len;
        dirZ /= len;

        // сила буста
        double boost = speed.entityBoostStrength.get();

        // разбиваем буст на 3 тика
        boostX = (dirX * boost) / 3.0;
        boostZ = (dirZ * boost) / 3.0;
        boostTicks = 3;
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void omniLongJump(CallbackInfo ci) {
        Speed speed = Modules.get(Speed.class);
        if (speed == null || !speed.enabled("jump_boost")) return;
        LocalPlayer p = (LocalPlayer) (Object) this;

        boolean onGround = p.onGround();

        if (p.isFallFlying()) {
            wasGround = onGround;
            return;
        }

        if (wasGround && !onGround && p.isSprinting() && !p.isInWater()) {

            Vec2 move = p.input.getMoveVector();
            float mx = move.x;
            float mz = move.y;

            if (mx == 0 && mz == 0) {
                wasGround = onGround;
                return;
            }

            float norm = Mth.sqrt(mx * mx + mz * mz);
            mx /= norm;
            mz /= norm;

            float yaw = p.getYRot();
            float rad = (float) Math.toRadians(yaw);

            double dirX = mz * -Mth.sin(rad) + mx * Mth.cos(rad);
            double dirZ = mz * Mth.cos(rad) + mx * Mth.sin(rad);

            Vec3 vel = p.getDeltaMovement();

            // ================================================
            // ВАЖНО: конвертация UI → internal
            //
            // UI = 1.25 → internal = 1.04
            // internal = ui * 0.832
            // ================================================
            double ui = speed.jumpAcceleration.get();
            double internal = ui * 0.832;

            // OmniSprint ванильная сила = 0.33
            double strength = 0.33 * internal;

            double vx = dirX * strength;
            double vz = dirZ * strength;

            // безопасный OmniSprint cap
            double max = 0.42;
            double h = Math.hypot(vx, vz);

            if (h > max) {
                double scale = max / h;
                vx *= scale;
                vz *= scale;
            }

            p.setDeltaMovement(vx, vel.y, vz);
        }

        wasGround = onGround;
    }

    @Inject(method = "modifyInput", at = @At("RETURN"), cancellable = true)
    private void applyLegitSpeed(Vec2 input, CallbackInfoReturnable<Vec2> cir) {

        // Speed OFF → выходим
        Speed speed = Modules.get(Speed.class);
        if (speed == null || !speed.isEnabled()) return;

        // LEGIT режим выключен → ничего не делаем
        if (!speed.enabled("legit_speed")) return;

        // Базовое значение
        Vec2 base = cir.getReturnValue();

        // Применяем множитель
        float mult = speed.legitMultiplier.get();
        cir.setReturnValue(base.scale(mult));
    }

    @Inject(method = "closeContainer", at = @At("HEAD"), cancellable = true)
    private void combatant$inventoryMove$deferCloseHandledScreen(CallbackInfo ci) {
        InventoryMove module = Modules.get(InventoryMove.class);
        if (module == null || !module.isEnabled()) return;
        if (module.shouldCancelCloseHandledScreen()) {
            ci.cancel();
        }
    }

    @Inject(method = "closeContainer", at = @At("HEAD"), cancellable = true)
    private void combatant$keepInventoryOpen(CallbackInfo ci) {
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




