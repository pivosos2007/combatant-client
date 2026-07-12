/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.events.impl.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.*;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.ElytraTarget;
import combatant.client.mixins.accessors.InputAccessor;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.player.MovementUtil;
import combatant.client.util.player.inventory.FireworkUseController;
import combatant.client.util.player.inventory.InventorySwap;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

//todo Description
@ModuleInfo(id = "elytrafly", displayName = "ElytraFly", aliases = {"elytra", "efly", "elytraflight"}, category = ModuleCategory.MOVEMENT)
public class ElytraFly extends Module {

    private static final int FIREWORK_ROTATION_PRIORITY = 8;
    private static final int ROTATION_EVENT_PRIORITY = 60;

    private static final double GRAVITY_COMPENSATION = 0.008;
    private static final double NATURAL_LIFT_FACTOR = 0.005;
    private static final float MAX_PITCH_ANGLE = 90.0f;
    private static final float MAX_DIVE_SPEED_MULTIPLIER = 1.2f;
    private static final float DIVE_SPEED_REDUCTION = 0.01f;
    private static final float MIN_ACCELERATION_THRESHOLD = 0.01f;
    private static final float DECELERATION_FACTOR = 0.98f;
    private static final float SPEED_EFFECT_MULTIPLIER = 0.1f;
    private static final double PITCH_REDUCTION_FACTOR = 0.3;
    private static final double GROUND_PENALTY_FACTOR = 0.8;
    private static final double NEAR_GROUND_HORIZONTAL_BOOST = 1.3;
    private static final double NEAR_GROUND_VERTICAL_REDUCTION = 0.3;
    private static final float DIVE_THRESHOLD_ANGLE = 15.0f;
    private static final double DIVE_BOOST_MULTIPLIER = 2.0;
    private static final double PACKET_START_COLLISION_XZ_CONTRACT = 0.25;
    private static final double PACKET_START_COLLISION_Y_OFFSET = -0.3;
    public final BooleanValue noCrash = bool("elytrafly_no_crash", true);
    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<Mode> mode = enumMode("elytrafly_mode", Mode.BOOST, Mode.values());
    private final BooleanValue instantStart = bool("elytrafly_instant_start", false);
    private final BooleanValue instantStop = bool("elytrafly_instant_stop", false);
    private final BooleanValue speedEnabled =
            visibleWhen(bool("elytrafly_speed", true), () -> mode.get() == Mode.STATIC);
    private final NumberValue<Float> verticalSpeed =
            visibleWhen(num("elytrafly_speed_vertical", 0.0f, 0.0f, 5.0f), () -> mode.get() == Mode.STATIC && speedEnabled.get());
    private final NumberValue<Float> horizontalSpeed =
            visibleWhen(num("elytrafly_speed_horizontal", 0.0f, 0.0f, 8.0f), () -> mode.get() == Mode.STATIC && speedEnabled.get());
    private final BooleanValue notInFluid = bool("elytrafly_not_in_fluid", false);
    private final BooleanValue durabilityExploit = bool("elytrafly_durability_exploit", false);
    private final BooleanValue staticDurabilityExploitNotWhileNoMove =
            visibleWhen(bool("elytrafly_static_durability_exploit_not_while_no_move", false), () -> mode.get() == Mode.STATIC);
    private final BooleanValue staticGlide =
            visibleWhen(bool("elytrafly_static_glide", false), () -> mode.get() == Mode.STATIC);
    private final NumberValue<Float> staticVerticalGlide =
            visibleWhen(num("elytrafly_static_glide_vertical", 0.01f, 0.0f, 1.0f), () -> mode.get() == Mode.STATIC && staticGlide.get());
    private final NumberValue<Float> staticHorizontalGlide =
            visibleWhen(num("elytrafly_static_glide_horizontal", 0.0f, 0.0f, 1.0f), () -> mode.get() == Mode.STATIC && staticGlide.get());
    private final NumberValue<Float> vanillaDragPercent =
            visibleWhen(num("elytrafly_vanilla_drag_percent", 99.0f, 90.0f, 100.0f), () -> mode.get() == Mode.VANILLA);
    private final NumberValue<Float> vanillaForwardBoost =
            visibleWhen(num("elytrafly_vanilla_forward_boost", 18.0f, 0.0f, 100.0f), () -> mode.get() == Mode.VANILLA);
    private final NumberValue<Float> vanillaPitchLift =
            visibleWhen(num("elytrafly_vanilla_pitch_lift", 4.5f, 0.0f, 30.0f), () -> mode.get() == Mode.VANILLA);
    private final NumberValue<Float> boostSpeed =
            visibleWhen(num("elytrafly_boost_speed", 0.018f, 0.0f, 2.0f), () -> mode.get() == Mode.BOOST);
    private final NumberValue<Float> boostAcceleration =
            visibleWhen(num("elytrafly_boost_acceleration", 0.018f, 0.001f, 0.1f), () -> mode.get() == Mode.BOOST);
    private final BooleanValue boostAutoBoost =
            visibleWhen(bool("elytrafly_boost_auto_boost", true), () -> mode.get() == Mode.BOOST);
    private final NumberValue<Float> boostVerticalControl =
            visibleWhen(num("elytrafly_boost_vertical_control", 0.0f, 0.0f, 1.0f), () -> mode.get() == Mode.BOOST);
    private final BooleanValue boostSmartGround =
            visibleWhen(bool("elytrafly_boost_smart_ground", true), () -> mode.get() == Mode.BOOST);
    private final NumberValue<Float> boostGroundDistance =
            visibleWhen(num("elytrafly_boost_ground_distance", 3.0f, 1.5f, 7.0f), () -> mode.get() == Mode.BOOST && boostSmartGround.get());
    private final BooleanValue boostDiveMechanics =
            visibleWhen(bool("elytrafly_boost_dive_mechanics", false), () -> mode.get() == Mode.BOOST);
    private final NumberValue<Float> boostDiveAcceleration =
            visibleWhen(num("elytrafly_boost_dive_acceleration", 0.05f, 0.01f, 0.1f), () -> mode.get() == Mode.BOOST && boostDiveMechanics.get());
    private final NumberValue<Float> boostDiveEfficiency =
            visibleWhen(num("elytrafly_boost_dive_efficiency", 0.8f, 0.4f, 1.5f), () -> mode.get() == Mode.BOOST && boostDiveMechanics.get());
    private final BooleanValue fireworkConsiderInventory =
            visibleWhen(bool("elytrafly_firework_consider_inventory", false), () -> mode.get() == Mode.FIREWORK);
    private final NumberValue<Integer> fireworkCooldownMin =
            visibleWhen(num("elytrafly_firework_cooldown_min", 20, 0, 300), () -> mode.get() == Mode.FIREWORK);
    private final NumberValue<Integer> fireworkCooldownMax =
            visibleWhen(num("elytrafly_firework_cooldown_max", 20, 0, 300), () -> mode.get() == Mode.FIREWORK);
    private final BooleanValue fireworkSilentRotation =
            visibleWhen(bool("elytrafly_firework_silent_rotation", true), () -> mode.get() == Mode.FIREWORK);
    private final NumberValue<Float> fireworkPitch =
            visibleWhen(num("elytrafly_firework_pitch", 0.0f, -90.0f, 90.0f), () -> mode.get() == Mode.FIREWORK && fireworkSilentRotation.get());
    private final BooleanValue fireworkVelocityControl =
            visibleWhen(bool("elytrafly_firework_velocity_control", true), () -> mode.get() == Mode.FIREWORK);
    private final NumberValue<Float> fireworkVelocityFactor =
            visibleWhen(num("elytrafly_firework_velocity_factor", 0.1f, 0.0f, 2.0f), () -> mode.get() == Mode.FIREWORK && fireworkVelocityControl.get());
    private final BooleanValue packetAutoStart =
            visibleWhen(bool("elytrafly_packet_auto_start", true), () -> mode.get() == Mode.PACKET);
    private final EnumValue<PacketSubMode> packetSubMode =
            visibleWhen(enumMode("elytrafly_packet_sub_mode", PacketSubMode.MOTION, PacketSubMode.values()), () -> mode.get() == Mode.PACKET);
    private final BooleanValue packetCancelCorrections =
            visibleWhen(bool("elytrafly_packet_cancel_corrections", true), () -> mode.get() == Mode.PACKET);
    private boolean needsToRestart;
    private Mode lastMode = mode.get();
    private float currentAcceleration;
    private float currentDiveSpeed;
    private boolean cachedGroundCheck;
    private int groundCheckCooldown;
    private int fireworkSkipTicks;
    private boolean queuedFireworkUse;

    {
        setDefaultBind("LEFT_ALT+L");
    }

    @Override
    public void onEnable() {
        resetRuntimeState();
        needsToRestart = false;
        lastMode = mode.get();
    }

    @Override
    public void onDisable() {
        RotationManager.INSTANCE.clear(this);
        InventorySwap.INSTANCE.releaseHotbar(this);
        resetRuntimeState();
        needsToRestart = true;
    }

    @Override
    public void onTick() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            resetRuntimeState();
            return;
        }

        syncModeChange();
        tickFireworkCooldown();

        if (shouldNotOperate(player)) {
            needsToRestart = false;
            resetModeState(mode.get());
            return;
        }

        boolean stop = shouldStopOperating(player);
        if (stop && player.isFallFlying()) {
            player.stopFallFlying();
            sendStartGlidingPacket(player);
            needsToRestart = false;
            return;
        }

        if (player.isFallFlying()) {
            applyTickMode(player);

            if (durabilityExploit.get() && shouldRunDurabilityExploit()) {
                sendStartGlidingPacket(player);
                needsToRestart = true;
            }
        } else if (shouldStartOperating(player)) {
            suppressJumpInput(player);
            player.startFallFlying();
            sendStartGlidingPacket(player);
        }
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (event.getType() != MoverType.SELF) return;
        if (!isEnabled() || !player.isFallFlying()) return;

        switch (mode.get()) {
            case STATIC -> applyStaticMove(event, player);
            case BOOST -> applyBoostMove(event, player);
            default -> {
            }
        }
    }

    @EventHandler(priority = ROTATION_EVENT_PRIORITY)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) {
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        if (!isEnabled() || shouldNotOperate(player)) {
            RotationManager.INSTANCE.clear(this);
            return;
        }

        if (mode.get() == Mode.FIREWORK && fireworkSilentRotation.get() && queuedFireworkUse && player.isFallFlying()) {
            queueSilentRotation(player, fireworkPitch.get(), FIREWORK_ROTATION_PRIORITY, 3);
            return;
        }

        RotationManager.INSTANCE.clear(this);
    }

    private void queueSilentRotation(LocalPlayer player, float pitch, int priority, int resetTicks) {
        float yaw = player.getYRot();
        Rotation rotation = new Rotation(yaw, Mth.clamp(pitch, -90.0f, 90.0f), false);
        RotationTarget plan = new RotationTarget(
                rotation,
                null,
                List.of(),
                Math.max(2, resetTicks),
                0.5f,
                false,
                MovementCorrection.SILENT,
                null
        );
        RotationManager.INSTANCE.setRotationTarget(plan, priority, this);
    }

    public boolean shouldNotOperate() {
        return mc.player == null || shouldNotOperate(mc.player);
    }

    public boolean shouldApplyVanillaHook(LocalPlayer player) {
        return isEnabled()
                && mode.get() == Mode.VANILLA
                && player != null
                && player.isFallFlying();
    }

    public double getVanillaDragFactor() {
        return vanillaDragPercent.get() / 100.0;
    }

    public double getVanillaForwardBoostFactor() {
        return vanillaForwardBoost.get() / 1000.0;
    }

    public double getVanillaPitchLiftFactor() {
        return vanillaPitchLift.get() / 1000.0;
    }

    public Vec3 predictTargetingPos(LocalPlayer player) {
        if (player == null) {
            return Vec3.ZERO;
        }

        Vec3 velocity = switch (mode.get()) {
            case VANILLA -> predictVanillaVelocity(player);
            default -> player.getDeltaMovement();
        };

        return player.position().add(velocity);
    }

    private boolean shouldNotOperate(LocalPlayer player) {
        if (player == null) return true;
        if (player.isPassenger()) return true;
        if (player.getAbilities().instabuild || player.hasEffect(MobEffects.LEVITATION)) return true;

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        return !chest.is(Items.ELYTRA) || nextDamageWillBreak(chest);
    }

    private Vec3 predictVanillaVelocity(LocalPlayer player) {
        Vec3 velocity = player.getDeltaMovement();
        Vec3 look = player.getViewVector(1.0f);
        float pitch = player.getXRot();

        velocity = velocity.scale(getVanillaDragFactor());
        velocity = velocity.add(look.scale(getVanillaForwardBoostFactor()));

        double lift = -Math.sin(Math.toRadians(pitch)) * getVanillaPitchLiftFactor();
        return velocity.add(0.0, lift, 0.0);
    }

    private boolean shouldStopOperating(LocalPlayer player) {
        if (player == null) return false;
        return instantStop.get() && mc.options.keyShift.isDown() && player.onGround()
                || notInFluid.get() && isInLiquid(player);
    }

    private boolean shouldStartOperating(LocalPlayer player) {
        if (player == null) return false;
        return (instantStart.get() && mc.options.keyJump.isDown() && player.getDeltaMovement().y != 0.0)
                || needsToRestart;
    }

    private boolean shouldRunDurabilityExploit() {
        if (mode.get() == Mode.VANILLA) return false;
        if (mode.get() != Mode.STATIC) return true;
        return !staticDurabilityExploitNotWhileNoMove.get() || !MovementUtil.isMoving();
    }

    private void applyTickMode(LocalPlayer player) {
        switch (mode.get()) {
            case VANILLA -> {
            }
            case BOOST -> applyBoostTick(player);
            case FIREWORK -> applyFireworkTick(player);
            default -> {
            }
        }
    }

    private void applyStaticMove(PlayerMoveEvent event, LocalPlayer player) {
        Vec3 movement = event.getMovement();
        if (movement == null) return;

        boolean useSpeed = speedEnabled.get();
        if (useSpeed && MovementUtil.isMoving()) {
            event.setMovement(applyStrafe(movement, player, horizontalSpeed.get(), 1.0));
        } else {
            double glideX = 0.0;
            double glideZ = 0.0;
            if (staticGlide.get()) {
                double len = Math.hypot(movement.x, movement.z);
                if (len > 1.0E-6) {
                    glideX = movement.x / len * staticHorizontalGlide.get();
                    glideZ = movement.z / len * staticHorizontalGlide.get();
                }
            }
            event.setMovement(new Vec3(glideX, movement.y, glideZ));
        }

        Vec3 updated = event.getMovement();
        if (updated == null) return;

        double newY;
        if (mc.options.keyJump.isDown() && useSpeed) {
            newY = verticalSpeed.get();
        } else if (mc.options.keyShift.isDown() && useSpeed) {
            newY = -verticalSpeed.get();
        } else {
            newY = staticGlide.get() ? -staticVerticalGlide.get() : 0.0;
        }

        event.setMovement(new Vec3(updated.x, newY, updated.z));
    }

    private void applyBoostTick(LocalPlayer player) {
        boolean nearGround = isNearGround(player);
        float divePullUpBoost = handleDiveMechanics(player);
        boolean shouldBoost = mc.options.keyJump.isDown()
                || boostAutoBoost.get()
                || divePullUpBoost > 0.0f;

        handleBoostAcceleration(shouldBoost);

        if (MovementUtil.isMoving()) {
            double speed = calculateEffectiveBoostSpeed(player, nearGround);
            player.setDeltaMovement(applyStrafe(player.getDeltaMovement(), player, speed, 1.0));
        }
    }

    private void applyBoostMove(PlayerMoveEvent event, LocalPlayer player) {
        Vec3 movement = event.getMovement();
        if (movement == null) return;

        boolean nearGround = isNearGround(player);
        double divePullUpBoost = player.getXRot() < 0.0f && currentDiveSpeed > 0.0f
                ? (-player.getXRot() / MAX_PITCH_ANGLE) * boostDiveEfficiency.get() * currentDiveSpeed * SPEED_EFFECT_MULTIPLIER
                : 0.0;

        if (currentAcceleration > 0.0f || currentDiveSpeed > 0.0f) {
            movement = movement.add(calculateBoostVector(player, nearGround, divePullUpBoost));
        }

        double newY = calculateBoostVerticalMovement(movement, divePullUpBoost);
        event.setMovement(new Vec3(movement.x, newY, movement.z));
    }

    private void applyFireworkTick(LocalPlayer player) {
        if (!shouldUseFirework(player)) {
            return;
        }
        queuedFireworkUse = true;
    }

    private boolean canPacketStartGliding(LocalPlayer player) {
        if (player == null || mc.level == null) return false;
        if (player.onGround() || player.isFallFlying() || player.isInWater() || player.isInLava()) return false;
        if (player.getDeltaMovement().y > 0.0) return false;

        var collisionBox = player.getBoundingBox()
                .inflate(-PACKET_START_COLLISION_XZ_CONTRACT, 0.0, -PACKET_START_COLLISION_XZ_CONTRACT)
                .move(0.0, PACKET_START_COLLISION_Y_OFFSET, 0.0);
        return !mc.level.getBlockCollisions(player, collisionBox).iterator().hasNext();
    }

    private boolean shouldUseFirework(LocalPlayer player) {
        if (player == null || !player.isFallFlying() || player.isUsingItem()) return false;
        if (fireworkSkipTicks > 0) return false;
        if (!hasFireworkRocket(player)) return false;

        ElytraTarget elytraTarget = Modules.get(ElytraTarget.class);
        if (elytraTarget != null && elytraTarget.isManagingFireworks()) {
            return false;
        }

        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof FireworkRocketEntity firework && firework.getOwner() == player) {
                return false;
            }
        }

        return true;
    }

    private boolean hasFireworkRocket(LocalPlayer player) {
        if (player == null) return false;
        if (player.getOffhandItem().is(Items.FIREWORK_ROCKET)
                || player.getMainHandItem().is(Items.FIREWORK_ROCKET)) {
            return true;
        }

        int max = fireworkConsiderInventory.get() ? player.getInventory().getContainerSize() : 9;
        for (int i = 0; i < max; i++) {
            if (player.getInventory().getItem(i).is(Items.FIREWORK_ROCKET)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = -1000)
    private void onSyncFirework(EventSync event) {
        if (!queuedFireworkUse || !isEnabled()) return;

        LocalPlayer player = mc.player;
        queuedFireworkUse = false;
        if (player == null || !player.isFallFlying()) return;

        if (FireworkUseController.INSTANCE.use(
                event.getYaw(),
                event.getPitch(),
                fireworkConsiderInventory.get(),
                true,
                true,
                0,
                true
        )) {
            fireworkSkipTicks = randomFireworkCooldown();
        }
    }

    @EventHandler(priority = 20)
    private void onSyncPacketMode(EventSync event) {
        if (!isEnabled() || mode.get() != Mode.PACKET) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || shouldNotOperate(player)) return;

        boolean started = false;
        if (packetAutoStart.get() && !player.isFallFlying() && canPacketStartGliding(player)) {
            player.startFallFlying();
            sendStartGlidingPacket(player);
            started = true;
        }

        if (packetSubMode.get() == PacketSubMode.MOTION
                && player.isFallFlying()
                && !started
                && player.tickCount % 3 != 0) {
            event.cancel();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || mode.get() != Mode.PACKET || !packetCancelCorrections.get()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;

        if (event.getPacket() instanceof ClientboundSetEntityDataPacket(
                int id, List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> packedItems
        )
                && id == player.getId()
                && !packedItems.isEmpty()) {
            event.cancel();
        }
    }

    @EventHandler
    private void onFirework(FireworkEvent event) {
        if (!isEnabled() || mode.get() != Mode.FIREWORK || !fireworkVelocityControl.get()) return;

        LocalPlayer player = mc.player;
        if (player == null || !player.isFallFlying()) return;

        ElytraTarget elytraTarget = Modules.get(ElytraTarget.class);
        if (elytraTarget != null && elytraTarget.isManagingFireworks()) {
            return;
        }

        Vec3 currentVelocity = event.getVector();
        Rotation moveRotation = RotationManager.INSTANCE.getMovementRotation();
        Vec3 lookVector = moveRotation.directionVector();
        Vec3 wantedFireworkVelocity = lookVector.scale(1.5);
        double factor = fireworkVelocityFactor.get();

        event.setVector(currentVelocity.add(
                lookVector.x * factor + (wantedFireworkVelocity.x - currentVelocity.x) * 0.5,
                lookVector.y * factor + (wantedFireworkVelocity.y - currentVelocity.y) * 0.5,
                lookVector.z * factor + (wantedFireworkVelocity.z - currentVelocity.z) * 0.5
        ));
    }

    private void suppressJumpInput(LocalPlayer player) {
        if (player == null || player.input == null) return;

        Input input = player.input.keyPresses;
        ((InputAccessor) player.input).setPlayerInput(new Input(
                input.forward(),
                input.backward(),
                input.left(),
                input.right(),
                false,
                input.shift(),
                input.sprint()
        ));
    }

    private void sendStartGlidingPacket(LocalPlayer player) {
        if (player == null || mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundPlayerCommandPacket(
                player,
                ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
        ));
    }

    private Vec3 applyStrafe(Vec3 currentVelocity, LocalPlayer player, double speed, double strength) {
        if (player == null || player.input == null) return currentVelocity;

        Vec2 input = player.input.getMoveVector();
        Vec3 movementInput = new Vec3(input.x, 0.0, input.y);
        return MovementUtil.withStrafe(currentVelocity, movementInput, resolveMovementYaw(player), speed, strength);
    }

    private float resolveMovementYaw(LocalPlayer player) {
        Rotation rotation = RotationManager.INSTANCE.getCurrentRotation();
        var rotationTarget = RotationManager.INSTANCE.getActiveRotationTarget();
        if (rotation != null && rotationTarget != null && rotationTarget.movementCorrection != MovementCorrection.OFF) {
            return rotation.yaw();
        }
        return player.getYRot();
    }

    private Vec3 resolveDirectionVector(LocalPlayer player) {
        Rotation rotation = RotationManager.INSTANCE.getCurrentRotation();
        var rotationTarget = RotationManager.INSTANCE.getActiveRotationTarget();
        if (rotation != null && rotationTarget != null && rotationTarget.movementCorrection != MovementCorrection.OFF) {
            return rotation.directionVector();
        }
        return player.getViewVector(1.0f);
    }

    private boolean isNearGround(LocalPlayer player) {
        if (!boostSmartGround.get()) return false;

        groundCheckCooldown--;
        if (groundCheckCooldown <= 0) {
            cachedGroundCheck = mc.level.getBlockCollisions(
                    player,
                    player.getBoundingBox().move(0.0, -boostGroundDistance.get(), 0.0)
            ).iterator().hasNext();
            groundCheckCooldown = 3;
        }

        return cachedGroundCheck;
    }

    private float handleDiveMechanics(LocalPlayer player) {
        if (!boostDiveMechanics.get()) {
            currentDiveSpeed = Math.max(0.0f, currentDiveSpeed - DIVE_SPEED_REDUCTION);
            return 0.0f;
        }

        float oldDiveSpeed = currentDiveSpeed;
        if (player.getXRot() > DIVE_THRESHOLD_ANGLE) {
            float diveFactor = Math.min(player.getXRot() / MAX_PITCH_ANGLE, 1.0f);
            currentDiveSpeed = Math.min(
                    currentDiveSpeed + boostDiveAcceleration.get() * diveFactor,
                    MAX_DIVE_SPEED_MULTIPLIER
            );
            return 0.0f;
        }

        currentDiveSpeed = Math.max(0.0f, currentDiveSpeed - DIVE_SPEED_REDUCTION);

        if (player.getXRot() < 0.0f && oldDiveSpeed > 0.0f) {
            return oldDiveSpeed * (-player.getXRot() / MAX_PITCH_ANGLE) * boostDiveEfficiency.get();
        }

        return 0.0f;
    }

    private void handleBoostAcceleration(boolean shouldBoost) {
        if (shouldBoost && currentAcceleration < boostSpeed.get()) {
            currentAcceleration = Math.min(
                    currentAcceleration + boostAcceleration.get() * (1.0f - currentAcceleration / boostSpeed.get()),
                    boostSpeed.get()
            );
            return;
        }

        if (!shouldBoost && currentAcceleration > 0.0f) {
            float accelerated = currentAcceleration * (DECELERATION_FACTOR - boostAcceleration.get());
            currentAcceleration = accelerated >= MIN_ACCELERATION_THRESHOLD ? accelerated : 0.0f;
        }
    }

    private double calculateEffectiveBoostSpeed(LocalPlayer player, boolean nearGround) {
        double baseSpeed = horizontalSpeed.get();
        double pitchReduction = player.getXRot() < 0.0f
                ? Math.abs(player.getXRot() / MAX_PITCH_ANGLE) * PITCH_REDUCTION_FACTOR
                : 0.0;
        double speedBonus = currentDiveSpeed + getSpeedEffectAmplifier(player) * SPEED_EFFECT_MULTIPLIER;
        double groundMultiplier = nearGround ? GROUND_PENALTY_FACTOR : 1.0;
        return baseSpeed * (1.0 - pitchReduction + speedBonus) * groundMultiplier;
    }

    private Vec3 calculateBoostVector(LocalPlayer player, boolean nearGround, double divePullUpBoost) {
        Vec3 look = resolveDirectionVector(player);
        double boostFactor = currentAcceleration
                + (player.getXRot() > 0.0f ? currentDiveSpeed : divePullUpBoost * DIVE_BOOST_MULTIPLIER);

        if (nearGround) {
            Vec3 scaled = new Vec3(
                    look.x * NEAR_GROUND_HORIZONTAL_BOOST,
                    look.y * NEAR_GROUND_VERTICAL_REDUCTION,
                    look.z * NEAR_GROUND_HORIZONTAL_BOOST
            );
            double len = scaled.length();
            return len > 1.0E-6 ? scaled.scale(boostFactor / len) : Vec3.ZERO;
        }

        return look.scale(boostFactor);
    }

    private double calculateBoostVerticalMovement(Vec3 movement, double divePullUpBoost) {
        double horizontal = Math.hypot(movement.x, movement.z);
        double naturalLift = horizontal * NATURAL_LIFT_FACTOR;
        double manualVertical = verticalSpeed.get() * boostVerticalControl.get();
        double baseY = movement.y - GRAVITY_COMPENSATION + naturalLift + divePullUpBoost;

        if (mc.options.keyJump.isDown() && !mc.options.keyShift.isDown()) {
            return movement.y + manualVertical + divePullUpBoost;
        }
        if (mc.options.keyShift.isDown() && !mc.options.keyJump.isDown()) {
            return movement.y - manualVertical;
        }
        return baseY;
    }

    private double getSpeedEffectAmplifier(LocalPlayer player) {
        var effect = player.getEffect(MobEffects.SPEED);
        return effect == null ? 0.0 : effect.getAmplifier() + 1.0;
    }

    private int randomFireworkCooldown() {
        int min = Math.min(fireworkCooldownMin.get(), fireworkCooldownMax.get());
        int max = Math.max(fireworkCooldownMin.get(), fireworkCooldownMax.get());
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private boolean nextDamageWillBreak(ItemStack stack) {
        return stack.isDamageableItem() && (stack.getMaxDamage() - stack.getDamageValue()) <= 1;
    }

    private boolean isInLiquid(LocalPlayer player) {
        return player != null && (player.isInWater() || player.isInLava());
    }

    private void tickFireworkCooldown() {
        if (fireworkSkipTicks > 0) {
            fireworkSkipTicks--;
        }
    }

    private void syncModeChange() {
        Mode current = mode.get();
        if (current == lastMode) {
            return;
        }

        resetModeState(lastMode);
        lastMode = current;
    }

    private void resetRuntimeState() {
        resetBoostState();
        fireworkSkipTicks = 0;
        queuedFireworkUse = false;
    }

    private void resetModeState(Mode currentMode) {
        if (currentMode == Mode.BOOST) {
            resetBoostState();
        }
        if (currentMode == Mode.FIREWORK) {
            fireworkSkipTicks = 0;
            queuedFireworkUse = false;
        }
    }

    private void resetBoostState() {
        currentAcceleration = 0.0f;
        currentDiveSpeed = 0.0f;
        cachedGroundCheck = false;
        groundCheckCooldown = 0;
    }

    public enum Mode {
        STATIC,
        VANILLA,
        BOOST,
        FIREWORK,
        PACKET
    }

    public enum PacketSubMode {
        NORMAL,
        MOTION
    }
}
