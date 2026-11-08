/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BindMode;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.KeyBindValue;
import combatant.client.features.gui.clickgui.settings.FunctionBindSetting;
import combatant.client.features.gui.hud.gauges.GaugeConnect;
import combatant.client.features.gui.hud.gauges.GaugeRegistry;
import combatant.client.events.impl.PacketEvent;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.MountJumpChargeEvent;
import combatant.client.events.impl.VehicleMoveEvent;
import combatant.client.events.impl.VehicleGroundStateEvent;
import combatant.client.util.player.VehicleFallGroundController;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.player.VehicleMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "entitycontrol",
        displayName = "EntityControl",
        aliases = {"MountControl", "EntitySpeed", "MountSpeed"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.BASIC,
        description = "module.entitycontrol.description")
public final class EntityControl extends Module {

    public enum Mode { NORMAL, VULCAN }
    public enum BoatSteering { VANILLA, CAMERA, HEADING }
    public enum AnimalWater { OFF, SURFACE, BUOYANCY }

    private static final double VULCAN_BOAT_CRUISE = 0.235D;
    private static final double VULCAN_BOAT_BUFFER_LIMIT = 5.0D;
    private static final double VULCAN_BOAT_BURST_BUDGET = 4.0D;
    private static final double VULCAN_MOUNT_ACCELERATION = 0.16D;
    private static final double VULCAN_MOUNT_FALLBACK = 0.30D;
    private static final double WATER_JUMP_IMPULSE = 0.32D;
    private static final double JUMP_FORWARD_MULTIPLIER = 1.35D;

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode = enumCommon("entityControlMode", "mode",
            CommonSettingSchemas.MODE, Mode.NORMAL, Mode.values());
    private final BooleanValue rotate = bool("entityControlRotate", "rotate", true);
    private final BooleanValue airControl = bool("entityControlAirControl", "air_control", true);
    private final NumberValue<Double> jumpHeight = num("entityControlJumpHeight", "jump_height", 0.0D, 0.0D, 2.0D);
    private final NumberValue<Double> speed = num("entityControlSpeed", "speed", 0.0D, 0.0D, 5.0D);
    private final BooleanValue speedOnlyOnGround = visibleWhen(
            bool("entityControlSpeedOnlyOnGround", "speed_only_on_ground", false), () -> speed.get() > 0.0D);
    private final BooleanValue speedInWater = visibleWhen(
            bool("entityControlSpeedInWater", "speed_in_water", true), () -> speed.get() > 0.0D);

    private final EnumValue<BoatSteering> boatSteering = enumSetting("entityControlBoatSteering", "boat_steering",
            BoatSteering.CAMERA, BoatSteering.values());
    private final BooleanValue boatItems = bool("entityControlBoatItems", "boat_items", true);
    private final BooleanValue noFall = visibleWhen(
            bool("entityControlNoFall", "no_fall", false), () -> mode.get() == Mode.VULCAN);
    private final VehicleFallGroundController fallGroundController = new VehicleFallGroundController();
    private final NumberValue<Double> boatSpeed = visibleWhen(
            num("entityControlBoatSpeed", "boat_speed", 0.48D, 0.0D, 5.0D),
            () -> mode.get() == Mode.NORMAL && boatSteering.get() != BoatSteering.VANILLA);
    private final FunctionBindSetting boostKey = action("boost_key",
            new KeyBindValue("entityControlBoatBoostKey", "NONE"), BindMode.HOLD);
    private final EnumValue<AnimalWater> animalWater = enumSetting("entityControlAnimalWater", "animal_water",
            AnimalWater.SURFACE, AnimalWater.values());

    private Entity trackedBoat;
    private Vec3 previousBoatHorizontal;
    private int trackedBoatTick = Integer.MIN_VALUE;
    private double boatEntitySpeedBuffer;
    private double boatHorizontalBuffer;
    private double boatVerticalBuffer;
    private double boatHoverBuffer;
    private double previousBoatVertical = Double.NaN;
    private int trackedBoatMovements;
    private int lastFlightObservedBoatTick = Integer.MIN_VALUE;
    private Entity trackedMount;
    private int trackedMountTick = Integer.MIN_VALUE;
    private double mountSpeedBuffer;
    private int mountCorrectionTicks;
    private int boatCorrectionTicks;
    private Entity trackedAnimal;
    private boolean previousJumpDown;
    private boolean pendingWaterJump;
    private int waterJumpCooldown;
    private Entity chargedHorse;
    private int horseChargeTicks;
    private int horseChargeLastTick = Integer.MIN_VALUE;
    private boolean horseJumpHeld;
    private int horseReleaseTick = Integer.MIN_VALUE;

    @Override
    public void onTick() {
        GaugeRegistry.updateAnnotated("entitycontrol", this);
        LocalPlayer player = mc.player;
        Entity vehicle = player == null ? null : player.getVehicle();
        if (mode.get() != Mode.VULCAN || !(vehicle instanceof AbstractHorse)
                || vehicle.getControllingPassenger() != player) {
            chargedHorse = null;
            horseChargeTicks = 0;
            horseChargeLastTick = Integer.MIN_VALUE;
            horseJumpHeld = false;
            horseReleaseTick = Integer.MIN_VALUE;
            return;
        }
        if (chargedHorse != vehicle) {
            chargedHorse = vehicle;
            horseChargeTicks = 0;
            horseChargeLastTick = Integer.MIN_VALUE;
            horseJumpHeld = false;
            horseReleaseTick = Integer.MIN_VALUE;
        }
        boolean down = mc.options.keyJump.isDown();
        if (down && horseChargeLastTick != player.tickCount) {
            horseChargeTicks = horseJumpHeld ? Math.min(20, horseChargeTicks + 1) : 1;
            horseChargeLastTick = player.tickCount;
        }
        if (!down && horseJumpHeld) horseReleaseTick = player.tickCount;
        else if (!down && horseReleaseTick != player.tickCount) horseChargeTicks = 0;
        horseJumpHeld = down;
    }

    @EventHandler
    private void onMountJumpCharge(MountJumpChargeEvent event) {
        LocalPlayer player = event.getPlayer();
        if (mode.get() != Mode.VULCAN || mc.player != player
                || !(player.getVehicle() instanceof AbstractHorse horse)
                || horse.getControllingPassenger() != player || chargedHorse != horse) return;
        if (horseChargeTicks == 0 || event.getCharge() <= 0.0f) return;
        float charge = (float) (0.12D + 0.88D * (1.0D - Math.exp(-horseChargeTicks / 2.6D)));
        event.setCharge(Math.max(event.getCharge(), charge));
    }

    @Override
    public void onDisable() {
        GaugeRegistry.removeOwner("entitycontrol");
        trackedBoat = null;
        previousBoatHorizontal = null;
        trackedAnimal = null;
        trackedMount = null;
        trackedMountTick = Integer.MIN_VALUE;
        mountSpeedBuffer = 0.0D;
        mountCorrectionTicks = 0;
        resetBoatHeuristics();
        fallGroundController.reset();
        trackedBoatTick = Integer.MIN_VALUE;
        previousJumpDown = false;
        pendingWaterJump = false;
        waterJumpCooldown = 0;
        chargedHorse = null;
        horseChargeTicks = 0;
        horseChargeLastTick = Integer.MIN_VALUE;
        horseJumpHeld = false;
        horseReleaseTick = Integer.MIN_VALUE;
    }

    public boolean useBoatStrafing(LocalPlayer player, AbstractBoat boat) {
        return isEnabled() && boatMotionEnabled()
                && player != null && player.getVehicle() == boat
                && boat.getControllingPassenger() == player;
    }

    public boolean allowBoatItemUse(LocalPlayer player) {
        return isEnabled() && boatItems.get() && player != null
                && player.getVehicle() instanceof AbstractBoat boat
                && boat.getControllingPassenger() == player;
    }

    public boolean providesVulcanMotion(Entity vehicle) {
        return isEnabled() && mode.get() == Mode.VULCAN
                && (!(vehicle instanceof AbstractBoat) || boatMotionEnabled());
    }

    @GaugeConnect(id = "entity_speed", labelKey = "hud.gauges.entitycontrol.entity_speed",
            readiness = GaugeRegistry.Readiness.THRESHOLD, threshold = 0.2f, hideAfterMillis = 1100)
    private GaugeRegistry.Sample entitySpeedGauge() {
        return bufferSample(boatEntitySpeedBuffer, activeBoat());
    }

    @GaugeConnect(id = "boat_fly_horizontal", labelKey = "hud.gauges.entitycontrol.boat_horizontal",
            readiness = GaugeRegistry.Readiness.THRESHOLD, threshold = 0.2f, hideAfterMillis = 1100)
    private GaugeRegistry.Sample boatHorizontalGauge() {
        return bufferSample(boatHorizontalBuffer, activeBoat());
    }

    @GaugeConnect(id = "boat_fly_ascent", labelKey = "hud.gauges.entitycontrol.boat_ascent",
            readiness = GaugeRegistry.Readiness.THRESHOLD, threshold = 0.2f, hideAfterMillis = 1100)
    private GaugeRegistry.Sample boatAscentGauge() {
        return bufferSample(boatVerticalBuffer, activeBoat());
    }

    @GaugeConnect(id = "boat_fly_hover", labelKey = "hud.gauges.entitycontrol.boat_hover",
            readiness = GaugeRegistry.Readiness.THRESHOLD, threshold = 0.2f, hideAfterMillis = 1100)
    private GaugeRegistry.Sample boatHoverGauge() {
        return bufferSample(boatHoverBuffer, activeBoat());
    }

    @GaugeConnect(id = "mount_speed", labelKey = "hud.gauges.entitycontrol.mount_speed",
            readiness = GaugeRegistry.Readiness.THRESHOLD, threshold = 0.2f, hideAfterMillis = 1100)
    private GaugeRegistry.Sample mountSpeedGauge() {
        return bufferSample(mountSpeedBuffer, activeMount());
    }

    private GaugeRegistry.Sample bufferSample(double used, boolean visible) {
        float available = (float) (1.0D - Mth.clamp(used / VULCAN_BOAT_BUFFER_LIMIT, 0.0D, 1.0D));
        return new GaugeRegistry.Sample(available, used + 1.0D <= VULCAN_BOAT_BURST_BUDGET,
                visible, GaugeRegistry.Flow.DRAIN, "");
    }

    private boolean activeBoat() {
        LocalPlayer player = mc.player;
        return isEnabled() && mode.get() == Mode.VULCAN && boatMotionEnabled() && player != null
                && player.getVehicle() instanceof AbstractBoat boat
                && boat.getControllingPassenger() == player;
    }

    private boolean activeMount() {
        LocalPlayer player = mc.player;
        return isEnabled() && mode.get() == Mode.VULCAN && player != null
                && player.getVehicle() != null
                && !(player.getVehicle() instanceof AbstractBoat)
                && player.getVehicle().getControllingPassenger() == player;
    }

    private boolean boostRequested() {
        return "NONE".equalsIgnoreCase(boostKey.get()) || isActionHeld("boost_key");
    }

    private boolean boatMotionEnabled() {
        return boatSteering.get() != BoatSteering.VANILLA
                && (mode.get() == Mode.VULCAN || boatSpeed.get() > 0.0D);
    }

    private void resetBoatHeuristics() {
        boatEntitySpeedBuffer = 0.0D;
        boatHorizontalBuffer = 0.0D;
        boatVerticalBuffer = 0.0D;
        boatHoverBuffer = 0.0D;
        previousBoatVertical = Double.NaN;
        trackedBoatMovements = 0;
        lastFlightObservedBoatTick = Integer.MIN_VALUE;
        boatCorrectionTicks = 0;
    }

    @EventHandler
    private void onVehicleCorrection(PacketEvent.Receive event) {
        LocalPlayer player = mc.player;
        if (mode.get() != Mode.VULCAN || player == null || player.getVehicle() == null) return;
        if (event.getPacket() instanceof ClientboundMoveVehiclePacket
                || event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            fallGroundController.reset();
            if (player.getVehicle() instanceof AbstractBoat) {
                boatEntitySpeedBuffer = Math.max(boatEntitySpeedBuffer, VULCAN_BOAT_BURST_BUDGET);
                boatHorizontalBuffer = Math.max(boatHorizontalBuffer, VULCAN_BOAT_BURST_BUDGET);
                boatCorrectionTicks = 60;
            } else {
                mountSpeedBuffer = VULCAN_BOAT_BURST_BUDGET;
                mountCorrectionTicks = 60;
            }
        }
    }

    @EventHandler
    private void onVehicleGroundState(VehicleGroundStateEvent event) {
        if (mode.get() != Mode.VULCAN || !noFall.get()) {
            fallGroundController.reset();
            return;
        }
        LocalPlayer player = mc.player;
        Entity mount = event.getVehicle();
        if (player == null || player.getVehicle() != mount
                || mount.getControllingPassenger() != player
                || !(mount instanceof LivingEntity)
                || mount.isInWater() || mount.isInLava()) {
            fallGroundController.reset();
            return;
        }
        if (fallGroundController.shouldSpoof(mount, mount.getY(), mount.fallDistance,
                event.isOnGround())) {
            event.setOnGround(true);
        }
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        Entity vehicle = player == null ? null : player.getVehicle();
        if (vehicle == null || vehicle.getControllingPassenger() != player) {
            trackedAnimal = null;
            trackedMount = null;
            trackedMountTick = Integer.MIN_VALUE;
            mountSpeedBuffer = 0.0D;
            mountCorrectionTicks = 0;
            previousJumpDown = false;
            pendingWaterJump = false;
            waterJumpCooldown = 0;
            return;
        }

        boolean jumpDown = mc.options.keyJump.isDown();
        if (vehicle != trackedAnimal) {
            trackedAnimal = vehicle;
            pendingWaterJump = false;
            waterJumpCooldown = 0;
            previousJumpDown = jumpDown;
        } else {
            if (jumpDown && !previousJumpDown) pendingWaterJump = true;
            if (!jumpDown) pendingWaterJump = false;
            previousJumpDown = jumpDown;
        }
        if (waterJumpCooldown > 0) waterJumpCooldown--;

        if (!(vehicle instanceof AbstractBoat) && rotate.get()) {
            vehicle.setYRot(player.getYRot());
            vehicle.setXRot(player.getXRot() * 0.5F);
        }
    }

    @EventHandler(priority = 50)
    private void onVehicleMove(VehicleMoveEvent event) {
        LocalPlayer player = mc.player;
        Entity vehicle = event.getVehicle();
        if (player == null || vehicle.getControllingPassenger() != player) return;

        Vec3 movement = event.getMovement();
        double x = movement.x;
        double y = movement.y;
        double z = movement.z;
        boolean modified = false;
        boolean input = VehicleMotion.hasHorizontalInput(mc);
        boolean vulcan = mode.get() == Mode.VULCAN;

        if (vehicle instanceof AbstractBoat boat) {
            if (boatMotionEnabled()) {
                if (boat != trackedBoat) {
                    trackedBoat = boat;
                    previousBoatHorizontal = null;
                    trackedBoatTick = Integer.MIN_VALUE;
                    resetBoatHeuristics();
                }
                BoatTerrain terrain = boatTerrain(boat);
                boolean newTick = trackedBoatTick != boat.tickCount;
                if (newTick) {
                    trackedBoatTick = boat.tickCount;
                    if (boatCorrectionTicks > 0) boatCorrectionTicks--;
                }

                if (input) {
                    float yaw = boatSteering.get() == BoatSteering.HEADING
                            ? boat.getYRot() : player.getYRot();
                    double targetSpeed = boatSpeed.get();
                    double acceleration = Math.max(0.18D, targetSpeed * 0.75D);
                    if (vulcan) {
                        boolean requested = boostRequested();
                        boolean burst = requested && (terrain != BoatTerrain.AIR
                                || Modules.enabled(EntityFlight.class))
                                && boatCorrectionTicks == 0
                                && boatEntitySpeedBuffer + 1.0D <= VULCAN_BOAT_BURST_BUDGET
                                && boatHorizontalBuffer + 1.0D <= VULCAN_BOAT_BURST_BUDGET
                                && boatVerticalBuffer < VULCAN_BOAT_BURST_BUDGET
                                && boatHoverBuffer < VULCAN_BOAT_BURST_BUDGET;
                        if (burst) {
                            targetSpeed = switch (terrain) {
                                case BLUE_ICE -> 45.0D;
                                case ICE -> 32.0D;
                                case LAND -> 14.0D;
                                case WATERLOGGED -> 12.0D;
                                case WATER -> 10.0D;
                                case AIR -> 9.0D;
                            };
                            targetSpeed = Math.min(targetSpeed,
                                    Math.sqrt(81.0D + boat.getDeltaMovement().lengthSqr()));
                            acceleration = targetSpeed + Math.hypot(x, z);
                        } else {
                            targetSpeed = VULCAN_BOAT_CRUISE;
                            acceleration = Math.max(Math.hypot(x, z) + targetSpeed, 0.35D);
                        }
                    }
                    Vec3 desired = VehicleMotion.horizontal(mc, yaw, targetSpeed);
                    Vec3 changed = accelerate(x, z, desired.x, desired.z, acceleration, targetSpeed);
                    x = changed.x;
                    z = changed.z;
                    modified = true;
                } else if (vulcan) {
                    if (terrain.ice() && previousBoatHorizontal != null) {
                        double friction = terrain == BoatTerrain.BLUE_ICE ? 0.995D : 0.985D;
                        x = previousBoatHorizontal.x * friction;
                        z = previousBoatHorizontal.z * friction;
                        modified = true;
                    }
                    if (boatEntitySpeedBuffer >= VULCAN_BOAT_BURST_BUDGET
                            || boatHorizontalBuffer >= VULCAN_BOAT_BURST_BUDGET) {
                        double magnitude = Math.hypot(x, z);
                        if (magnitude > VULCAN_BOAT_CRUISE) {
                            x *= VULCAN_BOAT_CRUISE / magnitude;
                            z *= VULCAN_BOAT_CRUISE / magnitude;
                            modified = true;
                        }
                    }
                }
                previousBoatHorizontal = new Vec3(x, 0.0D, z);
                if (newTick && vulcan && !Modules.enabled(EntityFlight.class))
                    updateBoatHeuristics(boat, terrain, x, y, z);
            }
        } else {
            double mountCap = vulcan ? mountSpeedCap(vehicle) : Double.MAX_VALUE;
            boolean mountBurst = false;
            boolean mountNewTick = false;
            if (vulcan) {
                if (trackedMount != vehicle) {
                    trackedMount = vehicle;
                    trackedMountTick = Integer.MIN_VALUE;
                    mountSpeedBuffer = 0.0D;
                    mountCorrectionTicks = 0;
                }
                mountNewTick = trackedMountTick != vehicle.tickCount;
                if (mountNewTick) {
                    trackedMountTick = vehicle.tickCount;
                    if (mountCorrectionTicks > 0) mountCorrectionTicks--;
                }
                mountBurst = input && boostRequested() && mountCorrectionTicks == 0
                        && mountSpeedBuffer + 1.0D <= VULCAN_BOAT_BURST_BUDGET;
            }
            if (airControl.get() && !vehicle.onGround() && input) {
                Vec3 steer = VehicleMotion.horizontal(mc, player, Math.hypot(x, z));
                if (vulcan) {
                    Vec3 adjusted = accelerate(x, z, steer.x, steer.z,
                            VULCAN_MOUNT_ACCELERATION, Math.max(Math.hypot(x, z), mountCap));
                    x = adjusted.x;
                    z = adjusted.z;
                } else {
                    x = steer.x;
                    z = steer.z;
                }
                modified = true;
            }

            if (speed.get() > 0.0D && input
                    && (!speedOnlyOnGround.get() || vehicle.onGround())
                    && (speedInWater.get() || !vehicle.isInWater())) {
                double target = vulcan ? Math.min(speed.get(), mountCap) : speed.get();
                Vec3 desired = VehicleMotion.horizontal(mc, player, target);
                if (vulcan) {
                    Vec3 adjusted = accelerate(x, z, desired.x, desired.z,
                            VULCAN_MOUNT_ACCELERATION, Math.max(Math.hypot(x, z), mountCap));
                    x = adjusted.x;
                    z = adjusted.z;
                } else {
                    x = desired.x;
                    z = desired.z;
                }
                modified = true;
            }

            if (mountBurst) {
                double base = Math.max(mountCap, 0.30D);
                double target = Math.min(8.0D, Math.max(2.0D, base * 4.0D));
                Vec3 desired = VehicleMotion.horizontal(mc, player, target);
                Vec3 boosted = accelerate(x, z, desired.x, desired.z,
                        target + Math.hypot(x, z), target);
                x = boosted.x;
                z = boosted.z;
                modified = true;
            } else if (vulcan && mountSpeedBuffer >= VULCAN_BOAT_BURST_BUDGET) {
                double horizontal = Math.hypot(x, z);
                if (horizontal > mountCap && horizontal > 1.0E-8D) {
                    x *= mountCap / horizontal;
                    z *= mountCap / horizontal;
                    modified = true;
                }
            }
            if (vulcan && mountNewTick) {
                mountSpeedBuffer = advanceBoatBuffer(mountSpeedBuffer,
                        Math.hypot(x, z) > mountCap + 0.01D, 0.25D);
            }

            boolean launched = false;
            if (jumpHeight.get() > 0.0D && vehicle.onGround()
                    && mc.options.keyJump.isDown() && y <= 0.05D) {
                y = jumpHeight.get();
                launched = true;
                modified = true;
            }

            if (vehicle instanceof Animal && animalWater.get() != AnimalWater.OFF) {
                double distance = VehicleMotion.waterSurfaceDistance(vehicle);
                if (!Double.isNaN(distance)) {
                    boolean jumpDown = mc.options.keyJump.isDown();
                    if (pendingWaterJump && jumpDown && waterJumpCooldown == 0
                            && distance >= -0.12D && distance <= 0.18D) {
                        y = WATER_JUMP_IMPULSE;
                        pendingWaterJump = false;
                        waterJumpCooldown = 9;
                        launched = true;
                        modified = true;
                    } else if (waterJumpCooldown == 0) {
                        double assisted = waterSurfaceMotion(vehicle, y, vulcan, jumpDown, distance);
                        if (!Double.isNaN(assisted)) {
                            y = assisted;
                            modified = true;
                        }
                    }
                }
            }

            if (launched && vulcan && input) {
                double horizontalSpeed = Math.hypot(x, z);
                double boostedSpeed = Math.min(Math.max(horizontalSpeed, 0.14D)
                        * JUMP_FORWARD_MULTIPLIER, mountCap);
                if (boostedSpeed > horizontalSpeed) {
                    Vec3 boosted = VehicleMotion.horizontal(mc, player, boostedSpeed);
                    x = boosted.x;
                    z = boosted.z;
                    modified = true;
                }
            }
        }

        if (modified) event.setMovement(new Vec3(x, y, z));
    }

    public void observeFlightMovement(Entity vehicle, Vec3 movement) {
        if (mode.get() != Mode.VULCAN || !(vehicle instanceof AbstractBoat boat)
                || boat != trackedBoat || boat.tickCount == lastFlightObservedBoatTick) return;
        lastFlightObservedBoatTick = boat.tickCount;
        updateBoatHeuristics(boat, boatTerrain(boat), movement.x, movement.y, movement.z);
    }

    public void applyBoatRotation(LocalPlayer player, AbstractBoat boat) {
        if (!isEnabled() || player == null || boat.getControllingPassenger() != player
                || boatSteering.get() != BoatSteering.CAMERA || !boatMotionEnabled()) return;
        float yaw = player.getYRot();
        boat.setYRot(yaw);
    }

    private void updateBoatHeuristics(AbstractBoat boat, BoatTerrain terrain,
                                      double x, double y, double z) {
        trackedBoatMovements++;
        double horizontal = Math.hypot(x, z);
        double boatFlyLimit = terrain == BoatTerrain.WATERLOGGED ? 0.45D : 0.25D;
        boolean active = trackedBoatMovements > 0;
        boatEntitySpeedBuffer = advanceBoatBuffer(boatEntitySpeedBuffer,
                active && horizontal > 0.65D, 0.25D);
        boatHorizontalBuffer = advanceBoatBuffer(boatHorizontalBuffer,
                active && horizontal > boatFlyLimit, 0.25D);
        boolean airborne = !boat.isInWater() && !boat.onGround();
        boatVerticalBuffer = advanceBoatBuffer(boatVerticalBuffer,
                active && airborne && y > 0.01D, 0.125D);
        boatHoverBuffer = advanceBoatBuffer(boatHoverBuffer,
                active && airborne && !Double.isNaN(previousBoatVertical)
                        && y > -0.05D && Math.abs(y - previousBoatVertical) < 0.025D,
                0.125D);
        previousBoatVertical = y;
    }

    private static double advanceBoatBuffer(double current, boolean violation, double decay) {
        return violation ? Math.min(VULCAN_BOAT_BUFFER_LIMIT, current + 1.0D)
                : Math.max(0.0D, current - decay);
    }

    private enum BoatTerrain {
        WATER, WATERLOGGED, LAND, AIR, ICE, BLUE_ICE;
        boolean ice() {
            return this == ICE || this == BLUE_ICE;
        }
    }

    private BoatTerrain boatTerrain(AbstractBoat boat) {
        if (mc.level == null) return BoatTerrain.AIR;
        AABB box = boat.getBoundingBox();
        double y = box.minY - 0.08D;
        double inset = 0.08D;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int ice = 0;
        int blueIce = 0;
        boolean waterlogged = false;
        for (int i = 0; i < 5; i++) {
            double x = i == 4 ? boat.getX() : (i & 1) == 0 ? box.minX + inset : box.maxX - inset;
            double z = i == 4 ? boat.getZ() : (i & 2) == 0 ? box.minZ + inset : box.maxZ - inset;
            BlockState state = mc.level.getBlockState(pos.set(x, y, z));
            if (state.is(Blocks.BLUE_ICE)) blueIce++;
            else if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)
                    || state.is(Blocks.FROSTED_ICE)) ice++;
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && state.getValue(BlockStateProperties.WATERLOGGED)) waterlogged = true;
        }
        if (!boat.isInWater() && blueIce >= 2) return BoatTerrain.BLUE_ICE;
        if (!boat.isInWater() && ice + blueIce >= 2) return BoatTerrain.ICE;
        if (boat.isInWater()) return waterlogged ? BoatTerrain.WATERLOGGED : BoatTerrain.WATER;
        return boat.onGround() ? BoatTerrain.LAND : BoatTerrain.AIR;
    }

    private static Vec3 accelerate(double x, double z, double targetX, double targetZ,
                                   double maxChange, double maxSpeed) {
        double dx = targetX - x;
        double dz = targetZ - z;
        double length = Math.hypot(dx, dz);
        if (length > maxChange && length > 1.0E-8D) {
            dx *= maxChange / length;
            dz *= maxChange / length;
        }
        double nextX = x + dx;
        double nextZ = z + dz;
        double speed = Math.hypot(nextX, nextZ);
        if (speed > maxSpeed && speed > 1.0E-8D) {
            return new Vec3(nextX * maxSpeed / speed, 0.0D, nextZ * maxSpeed / speed);
        }
        return new Vec3(nextX, 0.0D, nextZ);
    }

    static double mountSpeedCap(Entity mount) {
        if (mount instanceof LivingEntity living) {
            AttributeInstance attribute = living.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attribute != null) {
                return Mth.clamp(attribute.getValue() * 2.25D + 0.1D,
                        VULCAN_MOUNT_FALLBACK, 1.4D);
            }
        }
        return VULCAN_MOUNT_FALLBACK;
    }

    private double waterSurfaceMotion(Entity animal, double velocityY, boolean vulcan,
                                      boolean lifting, double distance) {
        if (animal.isInLava() || animal.onGround() || animal.fallDistance > 1.0F)
            return Double.NaN;
        if (velocityY > 0.15D || velocityY < -0.23D) return Double.NaN;
        if (distance < -0.13D || distance > (lifting ? 1.0D : 0.38D)) return Double.NaN;
        double strength = animalWater.get() == AnimalWater.SURFACE ? 0.30D : 0.18D;
        double verticalCap = lifting ? (vulcan ? 0.09D : 0.15D) : (vulcan ? 0.045D : 0.075D);
        double target = Mth.clamp(distance * strength - (lifting ? 0.0D : 0.008D),
                -0.055D, verticalCap);
        return Mth.clamp(velocityY * 0.3D + target * 0.7D,
                -0.07D, verticalCap);
    }

}
