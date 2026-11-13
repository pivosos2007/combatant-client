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
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import combatant.client.mixins.accessors.InputAccessor;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.KeyBindValue;
import combatant.client.config.values.BindMode;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.features.gui.clickgui.settings.FunctionBindSetting;
import combatant.client.features.gui.hud.gauges.GaugeConnect;
import combatant.client.features.gui.hud.gauges.GaugeRegistry;
import combatant.client.events.EventHandler;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.ElytraTarget;
import combatant.client.util.player.ElytraRecastUtil;
import combatant.client.util.player.ElytraImpactDamageUtil;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationUtil;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.player.MovementUtil;
import combatant.client.util.player.inventory.FireworkUseController;
import combatant.client.util.player.inventory.InventorySwap;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(id = "elytrafly", displayName = "ElytraFly", aliases = {"elytra", "efly", "elytraflight"}, category = ModuleCategory.MOVEMENT, subcategory = ModuleSubcategory.RAGE,
        description = "module.elytrafly.description")
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
    private static final double VANILLA_FIREWORK_BOOST = 0.1;
    private static final double VULCAN_HORIZONTAL_CAP = 4.13;
    private static final double VULCAN_ASCENT_CAP = 1.435;
    private static final double VULCAN_BURST_ACCELERATION = 0.16;
    private static final double VULCAN_CRUISE_ACCELERATION = 0.022;
    // Type G requires >0.031 horizontal gain; leave room for displacement rounding.
    private static final double VULCAN_G_ACCELERATION_CAP = 0.028;
    private static final double VULCAN_G_MIN_SPEED = 0.645;
    private static final int VULCAN_RECAST_STABILIZATION_TICKS = 6;
    private static final int CONTROL_FIREWORK_COOLDOWN = 5;
    public final BooleanValue noCrash = bool("elytrafly_no_crash", false);
    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<Mode> mode = enumCommon("elytrafly_mode", CommonSettingSchemas.MODE, Mode.FIREWORK, Mode.values());
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
    private final FunctionBindSetting vulcanBoostKey = action("vulcan_boost_key",
            new KeyBindValue("elytrafly_vulcan_boost_key", "NONE"), BindMode.HOLD);
    private final BooleanValue waterDragReduction = visibleWhen(
            bool("elytrafly_water_drag_reduction", false), () -> mode.get() == Mode.VULCAN_297);
    private final NumberValue<Float> boostSpeed =
            visibleWhen(num("elytrafly_boost_speed", 0.018f, 0.001f, 2.0f), () -> mode.get() == Mode.BOOST);
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
            visibleWhen(bool("elytrafly_firework_consider_inventory", false), () -> mode.get() == Mode.FIREWORK || mode.get() == Mode.CONTROL);
    private final NumberValue<Float> controlSpeedTarget =
            visibleWhen(num("elytrafly_control_speed_target", 30.0f, 10.0f, 80.0f), () -> mode.get() == Mode.CONTROL);
    private final NumberValue<Float> controlClimbPitch =
            visibleWhen(num("elytrafly_control_climb_pitch", 40.0f, 5.0f, 80.0f), () -> mode.get() == Mode.CONTROL);
    private final NumberValue<Float> controlDivePitch =
            visibleWhen(num("elytrafly_control_dive_pitch", 35.0f, 5.0f, 80.0f), () -> mode.get() == Mode.CONTROL);
    private final NumberValue<Float> controlTurnSpeed =
            visibleWhen(num("elytrafly_control_turn_speed", 12.0f, 1.0f, 60.0f), () -> mode.get() == Mode.CONTROL);
    private final BooleanValue controlHoldAltitude =
            visibleWhen(bool("elytrafly_control_hold_altitude", true), () -> mode.get() == Mode.CONTROL);
    private final NumberValue<Float> controlMinSpeed =
            visibleWhen(num("elytrafly_control_min_speed", 18.0f, 5.0f, 60.0f), () -> mode.get() == Mode.CONTROL);
    private final BooleanValue controlAutoFirework =
            visibleWhen(bool("elytrafly_control_auto_firework", true), () -> mode.get() == Mode.CONTROL);
    private final NumberValue<Integer> fireworkCooldownMin =
            visibleWhen(num("elytrafly_firework_cooldown_min", 20, 0, 300), () -> mode.get() == Mode.FIREWORK);
    private final NumberValue<Integer> fireworkCooldownMax =
            visibleWhen(num("elytrafly_firework_cooldown_max", 20, 0, 300), () -> mode.get() == Mode.FIREWORK);
    private final BooleanValue fireworkSilentRotation =
            visibleWhen(bool("elytrafly_firework_silent_rotation", true), () -> mode.get() == Mode.FIREWORK);
    private final NumberValue<Float> fireworkPitch =
            visibleWhen(num("elytrafly_firework_pitch", 0.0f, -90.0f, 90.0f), () -> mode.get() == Mode.FIREWORK && fireworkSilentRotation.get());
    private final BooleanValue fireworkVelocityControl =
            visibleWhen(bool("elytrafly_firework_velocity_control", false), () -> mode.get() == Mode.FIREWORK);
    private final NumberValue<Float> fireworkVelocityFactor =
            visibleWhen(num("elytrafly_firework_velocity_factor", 1.0f, 0.0f, 20.0f), () -> mode.get() == Mode.FIREWORK && fireworkVelocityControl.get());
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
    private boolean controlActive;
    private boolean controlHolding;
    private float controlHeading;
    private double controlHoldY;
    private final VulcanBuffer vulcanLimit = new VulcanBuffer(1.0, 0.25);
    private final VulcanBuffer vulcanAcceleration = new VulcanBuffer(6.0, 0.125);
    private final VulcanBuffer vulcanRapidAcceleration = new VulcanBuffer(3.0, 0.1);
    private final VulcanBuffer vulcanAscendingAcceleration = new VulcanBuffer(4.0, 0.1);
    private final VulcanBuffer vulcanAscent = new VulcanBuffer(4.0, 0.1);
    private final VulcanBuffer vulcanStall = new VulcanBuffer(8.0, 0.125);
    private final VulcanBuffer vulcanHorizontalPattern = new VulcanBuffer(4.0, 0.1);
    private final VulcanBuffer vulcanVerticalPattern = new VulcanBuffer(4.0, 0.1);
    private double vulcanLastX;
    private double vulcanLastY;
    private double vulcanLastZ;
    private double vulcanLastSpeed = Double.NaN;
    private double vulcanLastVertical = Double.NaN;
    private int vulcanLastTick = Integer.MIN_VALUE;
    private int vulcanFlyingTicks;
    private double vulcanProjectedLimit;
    private double vulcanProjectedAcceleration;
    private double vulcanProjectedAscent;
    private double vulcanProjectedMotion;
    private double vulcanProjectedRapid;
    private double vulcanProjectedAscending;
    private int vulcanCorrectionCooldown;
    private int vulcanLastGlideTick = Integer.MIN_VALUE;

    {
        setDefaultBind("LEFT_ALT+L");
    }

    @Override
    public void onEnable() {
        resetRuntimeState();
        lastMode = mode.get();
    }

    @Override
    public void onDisable() {
        vulcanLastGlideTick = Integer.MIN_VALUE;
        GaugeRegistry.removeOwner("elytrafly");
        RotationManager.INSTANCE.clear(this);
        InventorySwap.INSTANCE.releaseHotbar(this);
        resetRuntimeState();
    }

    @Override
    public void onTick() {
        GaugeRegistry.updateAnnotated("elytrafly", this);
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            resetRuntimeState();
            return;
        }

        syncModeChange();
        tickFireworkCooldown();
        if (!vulcanMotionActive(player) && vulcanLastTick != Integer.MIN_VALUE) {
            resetVulcanState();
        }

        if (shouldNotOperate(player)) {
            ElytraRecastUtil.INSTANCE.reset(this);
            needsToRestart = false;
            resetModeState(mode.get());
            return;
        }

        if (shouldStopOperating(player) && player.isFallFlying()) {
            player.stopFallFlying();
            sendStartGlidingPacket(player);
            needsToRestart = false;
            return;
        }

        if (player.isFallFlying()) {
            ElytraRecastUtil.INSTANCE.reset(this);
            applyTickMode(player);

            if (durabilityExploit.get() && shouldRunDurabilityExploit()) {
                sendStartGlidingPacket(player);
                needsToRestart = true;
            }
        } else if (needsToRestart && durabilityExploit.get()) {
            // Blatant (durability exploit only): forged start packet.
            suppressJumpInput(player);
            player.startFallFlying();
            sendStartGlidingPacket(player);
        } else if (mode.get() != Mode.PACKET && instantStart.get() && mc.options.keyJump.isDown()) {
            // Legal start: release jump for a tick, then press it so vanilla sends START_FALL_FLYING itself.
            needsToRestart = false;
            ElytraRecastUtil.INSTANCE.request(this, player, ElytraRecastUtil.Mode.GRIM, false, true, false, 0);
            ElytraRecastUtil.INSTANCE.tick(this, player);
        } else {
            needsToRestart = false;
            ElytraRecastUtil.INSTANCE.reset(this);
        }
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (event.getType() != MoverType.SELF) return;
        if (!isEnabled() || !player.isFallFlying() || shouldNotOperate(player)) return;

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

        if (mode.get() == Mode.CONTROL) {
            if (player.isFallFlying()) {
                applyControl(player);
                return;
            }
            controlActive = false;
        }

        if (mode.get() == Mode.FIREWORK && fireworkSilentRotation.get() && queuedFireworkUse && player.isFallFlying()) {
            queueSilentRotation(player.getYRot(), fireworkPitch.get(), FIREWORK_ROTATION_PRIORITY, 3);
            return;
        }

        RotationManager.INSTANCE.clear(this);
    }

    private void queueSilentRotation(float yaw, float pitch, int priority, int resetTicks) {
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

    @GaugeConnect(id = "speed", labelKey = "hud.gauges.elytrafly.speed",
            readiness = GaugeRegistry.Readiness.CONTINUOUS, hideAfterMillis = 1100)
    private GaugeRegistry.Sample vulcanSpeedGauge() {
        return vulcanGauge(Math.max(vulcanLimit.used, vulcanProjectedLimit), vulcanLimit.limit);
    }

    @GaugeConnect(id = "acceleration", labelKey = "hud.gauges.elytrafly.acceleration",
            readiness = GaugeRegistry.Readiness.CONTINUOUS, hideAfterMillis = 1100)
    private GaugeRegistry.Sample vulcanAccelerationGauge() {
        return vulcanGauge(Math.max(Math.max(
                        Math.max(vulcanAcceleration.used, vulcanProjectedAcceleration) / vulcanAcceleration.limit,
                        Math.max(vulcanRapidAcceleration.used, vulcanProjectedRapid) / vulcanRapidAcceleration.limit),
                        Math.max(vulcanAscendingAcceleration.used, vulcanProjectedAscending) / vulcanAscendingAcceleration.limit), 1.0D);
    }

    @GaugeConnect(id = "vertical", labelKey = "hud.gauges.elytrafly.vertical",
            readiness = GaugeRegistry.Readiness.CONTINUOUS, hideAfterMillis = 1100)
    private GaugeRegistry.Sample vulcanVerticalGauge() {
        return vulcanGauge(Math.max(Math.max(vulcanAscent.used, vulcanProjectedAscent) / vulcanAscent.limit,
                Math.max(vulcanVerticalPattern.used / vulcanVerticalPattern.limit,
                        vulcanStall.used / vulcanStall.limit)), 1.0D);
    }

    private GaugeRegistry.Sample vulcanGauge(double used, double max) {
        boolean active = isEnabled() && mode.get() == Mode.VULCAN_297
                && mc.player != null && vulcanMotionActive(mc.player);
        float fill = (float) (1.0 - Mth.clamp(used / max, 0.0, 1.0));
        return new GaugeRegistry.Sample(fill, true,
                active, GaugeRegistry.Flow.DRAIN, "");
    }

    private static boolean vulcanMotionActive(LocalPlayer player) {
        return player.isFallFlying() && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    public Vec3 predictTargetingPos(LocalPlayer player) {
        if (player == null) {
            return Vec3.ZERO;
        }

        return player.position().add(player.getDeltaMovement());
    }

    private boolean shouldNotOperate(LocalPlayer player) {
        if (player == null) return true;
        ElytraTarget target = Modules.get(ElytraTarget.class);
        if (target != null && target.isControllingFlight()) return true;
        if (player.isPassenger()) return true;
        if (player.hasEffect(MobEffects.LEVITATION)) return true;
        if (player.getAbilities().instabuild && mode.get() != Mode.VULCAN_297) return true;

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        return !chest.is(Items.ELYTRA) || nextDamageWillBreak(chest);
    }

    private void applyTickMode(LocalPlayer player) {
        switch (mode.get()) {
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

    /**
     * CONTROL mode: the module never touches velocity. It only picks a smooth silent rotation; vanilla elytra
     * physics (client and the server/Grim simulation) turn that rotation into travel.
     * Heading = camera yaw + WASD offset (held when no keys); pitch = climb / dive / hold-altitude or speed glide.
     */
    private void applyControl(LocalPlayer player) {
        Rotation server = RotationManager.INSTANCE.getServerRotation();
        Rotation base = server != null && server != Rotation.ZERO
                ? server
                : new Rotation(player.getYRot(), player.getXRot(), true);

        Vec3 velocity = player.getDeltaMovement();
        double speed = velocity.length();
        double targetSpeed = controlSpeedTarget.get() / 20.0;
        double minSpeed = controlMinSpeed.get() / 20.0;
        boolean climb = mc.options.keyJump.isDown() && !mc.options.keyShift.isDown();
        boolean dive = mc.options.keyShift.isDown() && !mc.options.keyJump.isDown();

        if (!controlActive) {
            controlActive = true;
            controlHolding = false;
            controlHeading = base.yaw();
        }

        float forward = (mc.options.keyUp.isDown() ? 1.0f : 0.0f) - (mc.options.keyDown.isDown() ? 1.0f : 0.0f);
        float strafe = (mc.options.keyRight.isDown() ? 1.0f : 0.0f) - (mc.options.keyLeft.isDown() ? 1.0f : 0.0f);
        if (forward != 0.0f || strafe != 0.0f) {
            controlHeading = player.getYRot() + (float) Math.toDegrees(Math.atan2(strafe, forward));
        }

        float up = controlClimbPitch.get();
        float down = controlDivePitch.get();
        // Climbing trades speed for height: fade the climb out near stall speed so we never hang below flying speed.
        float climbGuard = (float) Mth.clamp((speed - 0.5 * minSpeed) / (0.5 * minSpeed), 0.25, 1.0);
        float maxUp = up * climbGuard;

        float desiredPitch;
        boolean belowHoldAltitude = false;
        if (climb) {
            controlHolding = false;
            desiredPitch = -maxUp;
        } else if (dive) {
            controlHolding = false;
            desiredPitch = down;
        } else if (controlHoldAltitude.get()) {
            if (!controlHolding) {
                controlHolding = true;
                controlHoldY = player.getY();
            }
            double altitudeError = controlHoldY - player.getY();
            belowHoldAltitude = altitudeError > 1.0;
            desiredPitch = Mth.clamp((float) (velocity.y * 25.0 - altitudeError * 4.0), -maxUp, down);
        } else {
            controlHolding = false;
            desiredPitch = Mth.clamp((float) ((targetSpeed - speed) / targetSpeed * 60.0), -maxUp, down);
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        // Randomised per-tick step: never an exactly repeated delta (AimDuplicateLook), never a snap.
        float step = controlTurnSpeed.get() * (0.85f + 0.3f * random.nextFloat());
        float yawDiff = RotationUtil.angleDifference(controlHeading, base.yaw());
        float pitchDiff = desiredPitch - base.pitch();
        float yawStep = Mth.clamp(yawDiff, -step, step);
        float pitchStep = Mth.clamp(pitchDiff, -step, step);
        float yawRemaining = yawDiff - yawStep;
        float pitchRemaining = pitchDiff - pitchStep;

        float newYaw = base.yaw() + yawStep;
        float newPitch = base.pitch() + pitchStep;
        if (Math.abs(yawRemaining) < 1.0f && Math.abs(pitchRemaining) < 1.0f) {
            // Settled: keep a tiny human-like drift so consecutive rotation packets are not identical.
            newYaw += (random.nextFloat() - 0.5f) * 0.5f;
            newPitch += (random.nextFloat() - 0.5f) * 0.4f;
        }
        newPitch = Mth.clamp(newPitch, -90.0f, 90.0f);

        queueSilentRotation(newYaw, newPitch, FIREWORK_ROTATION_PRIORITY, 2);

        boolean settled = Math.abs(yawRemaining) <= 10.0f && Math.abs(pitchRemaining) <= 8.0f;
        // Gliding alone always loses height, so climbing and holding an altitude from below both need rockets.
        boolean needBoost = speed < minSpeed || ((climb || belowHoldAltitude) && speed < targetSpeed);
        if (controlAutoFirework.get() && settled && needBoost && shouldUseFirework(player)) {
            queuedFireworkUse = true;
        }
    }

    private boolean serverRotationIsControlRotation(EventSync event) {
        Rotation current = RotationManager.INSTANCE.getCurrentRotation();
        return RotationManager.INSTANCE.ownsActiveRotation(this)
                && Math.abs(RotationUtil.angleDifference(event.getYaw(), current.yaw())) < 1.0E-3f
                && Math.abs(event.getPitch() - current.pitch()) < 1.0E-3f;
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
        if (player == null || !player.isFallFlying() || shouldNotOperate(player)) return;
        if (mode.get() == Mode.CONTROL && !serverRotationIsControlRotation(event)) return;

        if (FireworkUseController.INSTANCE.use(
                event.getYaw(),
                event.getPitch(),
                fireworkConsiderInventory.get(),
                true,
                true,
                0,
                true
        )) {
            fireworkSkipTicks = mode.get() == Mode.CONTROL ? CONTROL_FIREWORK_COOLDOWN : randomFireworkCooldown();
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
        if (isEnabled() && mode.get() == Mode.VULCAN_297
                && event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            vulcanCorrectionCooldown = 30;
            vulcanLastTick = Integer.MIN_VALUE;
        }
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

    /**
     * Reproduces the vanilla rocket boost ({@code v += look * 0.1 + (look * 1.5 - v) * 0.5}) but with the
     * rotation the server actually knows (silent rotation), so Grim's prediction sees the same look vector.
     */
    @EventHandler
    private void onFirework(FireworkEvent event) {
        if (!isEnabled() || mode.get() != Mode.FIREWORK) return;

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

        double boost = fireworkVelocityControl.get()
                ? VANILLA_FIREWORK_BOOST * fireworkVelocityFactor.get()
                : VANILLA_FIREWORK_BOOST;

        event.setVector(currentVelocity.add(
                lookVector.x * boost + (wantedFireworkVelocity.x - currentVelocity.x) * 0.5,
                lookVector.y * boost + (wantedFireworkVelocity.y - currentVelocity.y) * 0.5,
                lookVector.z * boost + (wantedFireworkVelocity.z - currentVelocity.z) * 0.5
        ));
    }

    @EventHandler(priority = -1000)
    private void onMovementInput(MovementInputEvent event) {
        if (!isEnabled()) return;
        ElytraRecastUtil.INSTANCE.handleMovementInput(this, event);
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

    @EventHandler
    private void onWaterTravel(PlayerWaterTravelEvent event) {
        LocalPlayer player = event.getPlayer();
        if (!isEnabled() || mode.get() != Mode.VULCAN_297 || !waterDragReduction.get()
                || mc.player != player || !player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return;
        if (!player.isFallFlying() && (vulcanLastGlideTick == Integer.MIN_VALUE
                || player.tickCount - vulcanLastGlideTick > 2)) return;
        Vec3 incoming = event.getIncomingVelocity();
        Vec3 current = event.getVelocity();
        if (incoming == null || current == null) return;
        double before = Math.hypot(incoming.x, incoming.z);
        double after = Math.hypot(current.x, current.z);
        if (before <= 0.02D || after <= 1.0E-6D || !Double.isFinite(before) || !Double.isFinite(after)) return;
        if (incoming.x * current.x + incoming.z * current.z <= 0.0D) return;
        double target = Math.min(VULCAN_HORIZONTAL_CAP, Math.max(after, before * 0.99D));
        if (target <= after) return;
        double recovered = Math.min(target, after + 0.13D);
        double factor = recovered / after;
        event.setVelocity(new Vec3(current.x * factor, current.y, current.z * factor));
    }

    private Vec3 resolveDirectionVector(LocalPlayer player) {
        Rotation rotation = RotationManager.INSTANCE.getCurrentRotation();
        var rotationTarget = RotationManager.INSTANCE.getActiveRotationTarget();
        if (rotation != null && rotationTarget != null && rotationTarget.movementCorrection != MovementCorrection.OFF) {
            return rotation.directionVector();
        }
        return player.getViewVector(1.0f);
    }

    @EventHandler
    private void onElytraTravel(ElytraTravelEvent event) {
        LocalPlayer player = event.getPlayer();
        if (!isEnabled() || mode.get() != Mode.VULCAN_297 || mc.player != player
                || !vulcanMotionActive(player) || shouldNotOperate(player)) return;

        int tick = player.tickCount;
        boolean alreadyProcessed = vulcanLastTick == tick;
        if (!alreadyProcessed) {
            if (vulcanLastTick != Integer.MIN_VALUE && tick != vulcanLastTick + 1)
                resetVulcanState();
            sampleVulcanTravel(player, tick);
        }

        Vec3 velocity = event.getVelocity();
        if (velocity == null || !Double.isFinite(velocity.x)
                || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z)) return;

        vulcanLastGlideTick = tick;
        // The vanilla elytra physics runs before this event. A dive can push an already
        // boosted velocity over Vulcan's X/Z limit even when our own boost was capped.
        double incomingHorizontal = Math.hypot(velocity.x, velocity.z);
        if (incomingHorizontal > VULCAN_HORIZONTAL_CAP) {
            double factor = VULCAN_HORIZONTAL_CAP / incomingHorizontal;
            velocity = new Vec3(velocity.x * factor, velocity.y, velocity.z * factor);
        }
        // Do not quantize Y to hundredths: Elytra M treats that as a snapped motion pattern.
        if (velocity.y > VULCAN_ASCENT_CAP && player.getXRot() > -20.0F) {
            velocity = new Vec3(velocity.x, VULCAN_ASCENT_CAP, velocity.z);
        }
        // On the first gliding tick no previous movement sample exists. Do not issue
        // a full 0.16/t boost on top of the transition from grounded movement.
        // During the next ticks also constrain vanilla's horizontal gain: Type G
        // reads the final displacement, not just ElytraFly's added acceleration.
        if (vulcanFlyingTicks > 0 && vulcanFlyingTicks <= VULCAN_RECAST_STABILIZATION_TICKS
                && Double.isFinite(vulcanLastSpeed) && velocity.y > -0.5D) {
            double horizontal = Math.hypot(velocity.x, velocity.z);
            double safeHorizontal = Math.max(VULCAN_G_MIN_SPEED,
                    vulcanLastSpeed + VULCAN_G_ACCELERATION_CAP);
            if (horizontal > safeHorizontal && horizontal > 0.0D) {
                double factor = safeHorizontal / horizontal;
                velocity = new Vec3(velocity.x * factor, velocity.y, velocity.z * factor);
            }
        }
        event.setVelocity(velocity);
        if (alreadyProcessed || player.onGround() || vulcanFlyingTicks == 0
                || !Double.isFinite(vulcanLastSpeed)) return;

        // Server corrections suspend additional acceleration, not the safety limits.
        if (vulcanCorrectionCooldown > 0) {
            vulcanCorrectionCooldown--;
            return;
        }
        boolean bound = !"NONE".equalsIgnoreCase(vulcanBoostKey.get());
        boolean pressed = bound && isActionHeld("vulcan_boost_key");
        Vec3 direction = resolveVulcanDirection(player);
        if (direction.lengthSqr() <= 1.0E-6D && pressed) {
            Vec3 look = resolveDirectionVector(player);
            double length = Math.hypot(look.x, look.z);
            if (length > 1.0E-6D) direction = new Vec3(look.x / length, 0.0D, look.z / length);
        }
        if (direction.lengthSqr() <= 1.0E-6D) {
            double length = Math.hypot(velocity.x, velocity.z);
            if (length > 0.1D) direction = new Vec3(velocity.x / length, 0.0D, velocity.z / length);
        }
        double speed = Math.hypot(velocity.x, velocity.z);
        double previous = vulcanLastSpeed;
        if (direction.lengthSqr() > 1.0E-6D && speed < VULCAN_HORIZONTAL_CAP) {
            boolean ascending = velocity.y > 0.0D;
            boolean burst = !ascending && (!bound || pressed);
            double amount = ascending ? 0.0D
                    : burst ? VULCAN_BURST_ACCELERATION : VULCAN_CRUISE_ACCELERATION;
            if (amount > 0.0D) {
                double low = 0.0D;
                double high = amount;
                for (int i = 0; i < 15; i++) {
                    double mid = (low + high) * 0.5D;
                    Vec3 candidate = velocity.add(direction.scale(mid));
                    double projected = Math.hypot(candidate.x, candidate.z);
                    double gain = projected - previous;
                    boolean c = vulcanFlyingTicks > 3 && gain > 0.035D && velocity.y > -0.6D;
                    boolean g = gain > VULCAN_G_ACCELERATION_CAP
                            && velocity.y > -0.5D && projected > 0.65D;
                    boolean k = vulcanFlyingTicks > 5 && velocity.y > 0.0D
                            && gain > 0.005D && projected > 0.3D;
                    boolean allowed = projected <= VULCAN_HORIZONTAL_CAP
                            && !k
                            && !g
                            && (!c || vulcanAcceleration.used + 1.0D <= vulcanAcceleration.limit - 0.35D);
                    if (allowed) low = mid;
                    else high = mid;
                }
                if (low > 1.0E-5D) {
                    Vec3 boosted = velocity.add(direction.scale(low));
                    double projected = Math.hypot(boosted.x, boosted.z);
                    if (projected > speed + 1.0E-6D) {
                        double gain = projected - previous;
                        boolean c = vulcanFlyingTicks > 3 && gain > 0.035D && velocity.y > -0.6D;
                        boolean g = gain > VULCAN_G_ACCELERATION_CAP
                                && velocity.y > -0.5D && projected > 0.65D;
                        velocity = boosted;
                        vulcanProjectedLimit = vulcanLimit.used;
                        vulcanProjectedAcceleration = Math.min(vulcanAcceleration.limit,
                                vulcanAcceleration.used + (c ? 1.0D : 0.0D));
                        vulcanProjectedRapid = Math.min(vulcanRapidAcceleration.limit,
                                vulcanRapidAcceleration.used + (g ? 1.0D : 0.0D));
                        vulcanProjectedAscending = vulcanAscendingAcceleration.used;
                        vulcanProjectedAscent = vulcanAscent.used;
                        vulcanProjectedMotion = vulcanHorizontalPattern.used;
                    }
                }
            }
        }
        event.setVelocity(velocity);
    }

    /** Only cancel the blocked velocity components when vanilla predicts actual wall damage.
     * The event runs after other ElytraFly velocity modifiers; this is not mixin policy.
     */
    @EventHandler(priority = -1000)
    private void onCrashProtection(ElytraTravelEvent event) {
        LocalPlayer player = event.getPlayer();
        if (!noCrash.get() || mc.player != player || !player.isFallFlying()) return;
        Vec3 movement = event.getVelocity();
        if (movement == null || movement.horizontalDistanceSqr() <= 0.09) return;
        Vec3 clipped = ElytraImpactDamageUtil.allowedMovement(player, player.getBoundingBox(), movement);
        if (ElytraImpactDamageUtil.projectedDamage(movement, clipped) <= 0.0f) return;

        Vec3 safe = ElytraImpactDamageUtil.postCollisionVelocity(movement, clipped);
        event.setVelocity(new Vec3(safe.x, movement.y, safe.z));
    }

    private void sampleVulcanTravel(LocalPlayer player, int tick) {
        vulcanProjectedLimit = 0.0D;
        vulcanProjectedAcceleration = 0.0D;
        vulcanProjectedAscent = 0.0D;
        vulcanProjectedMotion = 0.0D;
        vulcanProjectedRapid = 0.0D;
        vulcanProjectedAscending = 0.0D;
        if (vulcanLastTick != Integer.MIN_VALUE && tick == vulcanLastTick + 1) {
            double speed = Math.hypot(player.getX() - vulcanLastX, player.getZ() - vulcanLastZ);
            double vertical = player.getY() - vulcanLastY;
            if (Double.isFinite(speed) && Double.isFinite(vertical)) {
                double acceleration = Double.isNaN(vulcanLastSpeed) ? 0.0 : speed - vulcanLastSpeed;
                double verticalChange = Double.isNaN(vulcanLastVertical)
                        ? 1.0 : Math.abs(vertical - vulcanLastVertical);
                vulcanFlyingTicks++;
                vulcanLimit.record(speed > 4.25);
                vulcanAcceleration.record(vulcanFlyingTicks > 3 && acceleration > 0.035 && vertical > -0.6);
                vulcanRapidAcceleration.record(acceleration > 0.031 && vertical > -0.5 && speed > 0.65);
                vulcanAscendingAcceleration.record(vulcanFlyingTicks > 5
                        && vertical > 0.0 && acceleration > 0.005 && speed > 0.3);
                vulcanAscent.record(vertical > 1.45 && player.getXRot() > -20.0f);
                vulcanStall.record(vulcanFlyingTicks > 5 && vulcanFlyingTicks < 400
                        && speed > 1.0 && Math.abs(acceleration) < 1.0E-10 && verticalChange < 1.0E-10);
                vulcanHorizontalPattern.record(speed > 0.1 && speed % 0.1 != 0.0
                        && (0.1 % (speed % 0.1)) < 1.0E-8);
                vulcanVerticalPattern.record(Math.abs(vertical) > 0.01
                        && vertical % 0.01 != 0.0 && (0.01 % (vertical % 0.01)) < 1.0E-8);
                vulcanLastSpeed = speed;
                vulcanLastVertical = vertical;
            }
        } else {
            vulcanFlyingTicks = 0;
            vulcanLastSpeed = Double.NaN;
            vulcanLastVertical = Double.NaN;
        }
        vulcanLastX = player.getX();
        vulcanLastY = player.getY();
        vulcanLastZ = player.getZ();
        vulcanLastTick = tick;
    }

    private Vec3 resolveVulcanDirection(LocalPlayer player) {
        if (player.input == null) return Vec3.ZERO;
        Vec2 movement = player.input.getMoveVector();
        double forward = movement.y;
        double sideways = movement.x;
        double length = Math.hypot(sideways, forward);
        if (length < 1.0E-4) return Vec3.ZERO;
        double yaw = Math.toRadians(resolveMovementYaw(player));
        double x = -Math.sin(yaw) * forward + Math.cos(yaw) * sideways;
        double z = Math.cos(yaw) * forward + Math.sin(yaw) * sideways;
        return new Vec3(x / length, 0.0, z / length);
    }

    private void resetVulcanState() {
        vulcanLimit.reset();
        vulcanAcceleration.reset();
        vulcanRapidAcceleration.reset();
        vulcanAscendingAcceleration.reset();
        vulcanAscent.reset();
        vulcanStall.reset();
        vulcanHorizontalPattern.reset();
        vulcanVerticalPattern.reset();
        vulcanLastTick = Integer.MIN_VALUE;
        vulcanLastSpeed = Double.NaN;
        vulcanLastVertical = Double.NaN;
        vulcanFlyingTicks = 0;
        vulcanProjectedLimit = 0.0D;
        vulcanProjectedAcceleration = 0.0D;
        vulcanProjectedAscent = 0.0D;
        vulcanProjectedMotion = 0.0D;
        vulcanProjectedRapid = 0.0D;
        vulcanProjectedAscending = 0.0D;
        vulcanCorrectionCooldown = 0;
    }

    private static final class VulcanBuffer {
        private final double limit;
        private final double decay;
        private double used;

        private VulcanBuffer(double limit, double decay) {
            this.limit = limit;
            this.decay = decay;
        }

        private void record(boolean violation) {
            used = violation ? Math.min(limit + 1.0, used + 1.0)
                    : Math.max(0.0, used - decay);
        }

        private void reset() {
            used = 0.0;
        }
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
        float maxSpeed = boostSpeed.get();
        // Guard against zero or negative boost speed to prevent division by zero resulting in NaN/Infinity motion.
        if (maxSpeed <= 0.0f) {
            currentAcceleration = 0.0f;
            return;
        }

        if (shouldBoost && currentAcceleration < maxSpeed) {
            currentAcceleration = Math.min(
                    currentAcceleration + boostAcceleration.get() * (1.0f - currentAcceleration / maxSpeed),
                    maxSpeed
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

    private boolean shouldStopOperating(LocalPlayer player) {
        if (player == null) return false;
        return instantStop.get() && mc.options.keyShift.isDown() && player.onGround()
                || notInFluid.get() && isInLiquid(player);
    }

    private boolean shouldRunDurabilityExploit() {
        if (mode.get() == Mode.VULCAN_297) return false;
        if (mode.get() != Mode.STATIC) return true;
        return !staticDurabilityExploitNotWhileNoMove.get() || !MovementUtil.isMoving();
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
        needsToRestart = false;
        resetBoostState();
        fireworkSkipTicks = 0;
        controlActive = false;
        controlHolding = false;
        queuedFireworkUse = false;
        ElytraRecastUtil.INSTANCE.reset(this);
        resetVulcanState();
    }

    private void resetModeState(Mode currentMode) {
        if (currentMode == Mode.VULCAN_297) {
            resetVulcanState();
            GaugeRegistry.removeOwner("elytrafly");
        }
        if (currentMode == Mode.BOOST) {
            resetBoostState();
        }
        if (currentMode == Mode.FIREWORK || currentMode == Mode.CONTROL) {
            fireworkSkipTicks = 0;
            queuedFireworkUse = false;
        }
        if (currentMode == Mode.CONTROL) {
            controlActive = false;
            controlHolding = false;
        }
    }

    private void resetBoostState() {
        currentAcceleration = 0.0f;
        currentDiveSpeed = 0.0f;
        cachedGroundCheck = false;
        groundCheckCooldown = 0;
    }

    public enum Mode implements EnumValue.AliasProvider {
        STATIC,
        VULCAN_297 {
            @Override
            public List<String> aliases() {
                return List.of("vanilla", "vulcan");
            }
        },
        BOOST,
        FIREWORK,
        CONTROL,
        PACKET
    }

    public enum PacketSubMode {
        NORMAL,
        MOTION
    }
}
