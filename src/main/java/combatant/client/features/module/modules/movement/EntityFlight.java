/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.VehicleMoveEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.features.gui.hud.gauges.GaugeConnect;
import combatant.client.features.gui.hud.gauges.GaugeRegistry;
import combatant.client.util.player.VehicleMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "entityflight",
        displayName = "EntityFlight",
        aliases = {"VehicleFlight", "MountFly"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.RAGE,
        description = "module.entityflight.description")
public final class EntityFlight extends Module {

    public enum Mode { VULCAN, NORMAL }

    private static final double BOAT_ASCENT_LIMIT = 4.0D;
    private static final double MOUNT_ASCENT_LIMIT = 5.0D;
    private static final double HOVER_LIMIT = 5.0D;
    private static final double BUFFER_RESERVE = 1.0D;

    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<Mode> mode = enumCommon("entityFlightMode", "mode",
            CommonSettingSchemas.MODE, Mode.VULCAN, Mode.values());

    private Entity trackedVehicle;
    private int lastTick = Integer.MIN_VALUE;
    private int airborneTicks;
    private int unsupportedTicks;
    private int boatFloatingTicks;
    private int launchTicks;
    private int lastMountPulseTick = Integer.MIN_VALUE;
    private int mountVerticalInput;
    private int correctionTicks;
    private double ascentBuffer;
    private double hoverBuffer;
    private double lastVertical = Double.NaN;
    private double horizontalBuffer;
    private double holdAltitude = Double.NaN;
    private double lastVehicleHeight = Double.NaN;
    private double measuredVertical;
    private boolean ownsHorizontal;
    private boolean flightStarted;

    @Override
    public void onEnable() {
        resetState(null);
    }

    @Override
    public void onDisable() {
        GaugeRegistry.removeOwner("entityflight");
        resetState(null);
    }

    @Override
    public void onTick() {
        LocalPlayer player = mc.player;
        if (trackedVehicle != null && (player == null
                || player.getVehicle() != trackedVehicle
                || trackedVehicle.getControllingPassenger() != player)) resetState(null);
        if (player != null && player.getVehicle() != null
                && player.getVehicle().getControllingPassenger() == player
                && trackedVehicle != player.getVehicle()) resetState(player.getVehicle());
        GaugeRegistry.updateAnnotated("entityflight", this);
    }

    @GaugeConnect(id = "ascent", labelKey = "hud.gauges.entityflight.ascent",
            readiness = GaugeRegistry.Readiness.THRESHOLD, threshold = .2f, hideAfterMillis = 1100)
    private GaugeRegistry.Sample ascentGauge() {
        double limit = trackedVehicle instanceof AbstractBoat ? BOAT_ASCENT_LIMIT : MOUNT_ASCENT_LIMIT;
        return bufferSample(ascentBuffer, limit,
                activeFlight());
    }

    @GaugeConnect(id = "hover", labelKey = "hud.gauges.entityflight.hover",
            readiness = GaugeRegistry.Readiness.THRESHOLD, threshold = .2f, hideAfterMillis = 1100)
    private GaugeRegistry.Sample hoverGauge() {
        return bufferSample(hoverBuffer, HOVER_LIMIT,
                activeFlight());
    }

    @GaugeConnect(id = "horizontal", labelKey = "hud.gauges.entityflight.horizontal",
            readiness = GaugeRegistry.Readiness.THRESHOLD, threshold = .2f, hideAfterMillis = 1100)
    private GaugeRegistry.Sample horizontalGauge() {
        return bufferSample(horizontalBuffer, HOVER_LIMIT,
                activeFlight() && !sharedHorizontalMotion());
    }

    private GaugeRegistry.Sample bufferSample(double used, double limit, boolean visible) {
        float available = (float) (1.0D - Mth.clamp(used / limit, 0.0D, 1.0D));
        return new GaugeRegistry.Sample(available, used + BUFFER_RESERVE <= limit,
                visible, GaugeRegistry.Flow.DRAIN, "");
    }

    private boolean activeFlight() {
        LocalPlayer player = mc.player;
        return isEnabled() && mode.get() == Mode.VULCAN && player != null
                && player.getVehicle() != null
                && player.getVehicle().getControllingPassenger() == player;
    }

    private boolean sharedHorizontalMotion() {
        LocalPlayer player = mc.player;
        EntityControl control = Modules.get(EntityControl.class);
        return player != null && player.getVehicle() != null
                && control != null && control.providesVulcanMotion(player.getVehicle());
    }

    public boolean controlsBoat(LocalPlayer player, AbstractBoat boat) {
        return isEnabled() && player != null && player.getVehicle() == boat
                && boat.getControllingPassenger() == player;
    }

    public void applyBoatRotation(LocalPlayer player, AbstractBoat boat) {
        if (!controlsBoat(player, boat)) return;
        EntityControl control = Modules.get(EntityControl.class);
        if (control == null || !control.providesVulcanMotion(boat))
            boat.setYRot(player.getYRot());
    }

    private void resetState(Entity vehicle) {
        trackedVehicle = vehicle;
        lastTick = Integer.MIN_VALUE;
        airborneTicks = 0;
        unsupportedTicks = 0;
        boatFloatingTicks = 0;
        launchTicks = 0;
        lastMountPulseTick = Integer.MIN_VALUE;
        mountVerticalInput = 0;
        correctionTicks = 0;
        ascentBuffer = 0.0D;
        hoverBuffer = 0.0D;
        horizontalBuffer = 0.0D;
        holdAltitude = Double.NaN;
        lastVertical = Double.NaN;
        lastVehicleHeight = Double.NaN;
        measuredVertical = 0.0D;
        ownsHorizontal = false;
        flightStarted = false;
    }

    @EventHandler
    private void onCorrection(PacketEvent.Receive event) {
        if (mode.get() != Mode.VULCAN || mc.player == null
                || mc.player.getVehicle() == null) return;
        if (event.getPacket() instanceof ClientboundMoveVehiclePacket
                || event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            correctionTicks = 16;
            ascentBuffer = Math.max(ascentBuffer, MOUNT_ASCENT_LIMIT - BUFFER_RESERVE);
            hoverBuffer = Math.max(hoverBuffer, HOVER_LIMIT - BUFFER_RESERVE);
            horizontalBuffer = Math.max(horizontalBuffer, HOVER_LIMIT - BUFFER_RESERVE);
            holdAltitude = Double.NaN;
            lastMountPulseTick = Integer.MIN_VALUE;
            mountVerticalInput = 0;
            launchTicks = 0;
            lastVehicleHeight = Double.NaN;
        }
    }

    @EventHandler(priority = -2000)
    private void onMovementInput(MovementInputEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || player.getVehicle() == null
                || player.getVehicle().getControllingPassenger() != player) return;
        if (mc.options.keyShift.isDown() && !mc.options.keySprint.isDown())
            event.setSneak(false);
    }

    @EventHandler(priority = -50)
    private void onVehicleMove(VehicleMoveEvent event) {
        LocalPlayer player = mc.player;
        Entity vehicle = event.getVehicle();
        if (player == null || player.getVehicle() != vehicle
                || vehicle.getControllingPassenger() != player) return;

        if (trackedVehicle != vehicle) resetState(vehicle);
        Vec3 original = event.getMovement();
        if (original == null || vehicle.isInLava()) return;

        EntityControl control = Modules.get(EntityControl.class);
        boolean sharedHorizontal = control != null && control.providesVulcanMotion(vehicle)
                && mode.get() == Mode.VULCAN;
        ownsHorizontal = !sharedHorizontal;
        boolean boat = vehicle instanceof AbstractBoat;
        boolean inWater = vehicle.isInWater();
        boolean onGround = vehicle.onGround();
        double surfaceDistance = VehicleMotion.waterSurfaceDistance(vehicle);
        boolean atSurface = !Double.isNaN(surfaceDistance);
        boolean airborne = !onGround && !inWater && !atSurface;
        boolean newTick = lastTick != vehicle.tickCount;
        if (newTick) {
            measuredVertical = Double.isFinite(lastVehicleHeight)
                    ? Mth.clamp(vehicle.getY() - lastVehicleHeight, -1.2D, 1.2D)
                    : original.y;
            lastVehicleHeight = vehicle.getY();
            lastTick = vehicle.tickCount;
            if (correctionTicks > 0) correctionTicks--;
            airborneTicks = airborne ? airborneTicks + 1 : 0;
            unsupportedTicks = onGround || atSurface ? 0 : unsupportedTicks + 1;
        }

        double x = original.x;
        double y = original.y;
        double z = original.z;
        boolean upward = mc.options.keyJump.isDown();
        boolean downward = mc.options.keyShift.isDown() && !upward;
        boolean horizontalInput = VehicleMotion.hasHorizontalInput(mc);

        if (!sharedHorizontal) {
            if (horizontalInput) {
                double cruise = boat ? 0.235D : EntityControl.mountSpeedCap(vehicle);
                double boost = boat ? (inWater ? 10.0D : onGround ? 28.0D : 9.0D)
                        : Math.min(8.0D, Math.max(2.0D, cruise * 4.0D));
                double target = mode.get() == Mode.VULCAN
                        ? (horizontalBuffer + BUFFER_RESERVE <= HOVER_LIMIT
                                && correctionTicks == 0 ? boost : cruise)
                        : 1.5D;
                Vec3 desired = VehicleMotion.horizontal(mc, player, target);
                x = desired.x;
                z = desired.z;
            } else if (mode.get() == Mode.VULCAN) {
                double friction = boat ? 0.985D : 0.90D;
                x *= friction;
                z *= friction;
            } else {
                x = 0.0D;
                z = 0.0D;
            }
        }

        if (mode.get() == Mode.NORMAL) {
            y = upward ? 0.5D : downward ? -0.5D : -0.04D;
        } else if (correctionTicks == 0) {
            if (boat) {
                flightStarted = true;
                y = vulcanVertical(vehicle, y, surfaceDistance, true, upward, downward, newTick);
            } else {
                if (onGround && launchTicks == 0) {
                    flightStarted = false;
                    holdAltitude = Double.NaN;
                    lastMountPulseTick = Integer.MIN_VALUE;
                    mountVerticalInput = 0;
                }
                if (!flightStarted && y > 0.13D && unsupportedTicks <= 3) {
                    flightStarted = true;
                    launchTicks = 12;
                    holdAltitude = Double.NaN;
                }
                if (flightStarted && launchTicks > 0) {
                    if (newTick) launchTicks--;
                    if (downward || launchTicks == 0 || y <= 0.03D && launchTicks <= 8) {
                        holdAltitude = vehicle.getY() + (downward ? -0.15D : Math.max(0.0D, y));
                        launchTicks = 0;
                    }
                }
                if (flightStarted && launchTicks == 0 || atSurface) {
                    int verticalInput = upward ? 1 : downward ? -1 : 0;
                    if (verticalInput == 0 && mountVerticalInput != 0 && flightStarted)
                        holdAltitude = vehicle.getY();
                    mountVerticalInput = verticalInput;
                    y = vulcanVertical(vehicle, y, surfaceDistance, false, upward, downward, newTick);
                    if (flightStarted && airborne && unsupportedTicks >= 26)
                        y = sustainedMountMotion(vehicle, y, upward, downward);
                }
            }
        } else if (atSurface && !upward && !downward) {
            y = surfaceMotion(y, surfaceDistance, boat);
        }

        if (mode.get() == Mode.VULCAN && airborne) {
            if (boat) {
                if (boatFloatingTicks >= 12 || hoverBuffer >= 2.5D
                        && y > -0.05D && Double.isFinite(lastVertical)
                        && Math.abs(y - lastVertical) < 0.025D) {
                    y = Math.min(y, -0.085D);
                    boatFloatingTicks = 0;
                }
                if (upward && ascentBuffer + BUFFER_RESERVE >= BOAT_ASCENT_LIMIT)
                    y = Math.min(y, -0.06D);
            }
        }
        if (boat && newTick && mode.get() == Mode.VULCAN) {
            if (!airborne || y < -0.03125D) boatFloatingTicks = 0;
            else boatFloatingTicks++;
        }

        Vec3 changed = new Vec3(x, y, z);
        if (newTick && mode.get() == Mode.VULCAN) {
            boolean ascentCandidate = airborne && y > (boat ? 0.01D : 0.0D)
                    && (boat || airborneTicks > 30);
            ascentBuffer = updateBuffer(ascentBuffer, ascentCandidate, boat ? 0.125D : 0.25D);
            boolean hoverCandidate = airborne && !Double.isNaN(lastVertical)
                    && (boat ? y > -0.05D : y > -1.15D)
                    && Math.abs(y - lastVertical) < (boat ? 0.025D : 0.05D)
                    && (boat || airborneTicks > 30);
            hoverBuffer = updateBuffer(hoverBuffer, hoverCandidate, boat ? 0.125D : 0.25D);
            if (!sharedHorizontal) {
                horizontalBuffer = updateBuffer(horizontalBuffer,
                        Math.hypot(x, z) > (boat ? 0.25D : EntityControl.mountSpeedCap(vehicle)), 0.25D);
            }
            lastVertical = y;
        }

        event.setMovement(changed);
        if (control != null && control.providesVulcanMotion(vehicle))
            control.observeFlightMovement(vehicle, changed);
    }

    private double vulcanVertical(Entity vehicle, double originalY, double surfaceDistance,
                                  boolean boat, boolean upward, boolean downward, boolean newTick) {
        if (!Double.isNaN(surfaceDistance) && !upward && !downward) {
            holdAltitude = Double.NaN;
            return surfaceMotion(originalY, surfaceDistance, boat);
        }
        if (vehicle.onGround() && !upward) {
            holdAltitude = Double.NaN;
            flightStarted = false;
            return Math.min(0.0D, originalY);
        }
        boolean landing = downward && !vehicle.onGround()
                && !vehicle.level().noCollision(vehicle, vehicle.getBoundingBox().move(0.0D, -0.20D, 0.0D));
        if (landing) {
            holdAltitude = Double.NaN;
            flightStarted = false;
            return Math.min(-0.065D, originalY);
        }

        if (Double.isNaN(holdAltitude)) holdAltitude = vehicle.getY();
        if (newTick) {
            if (upward && (!boat || ascentBuffer < 2.0D))
                holdAltitude = Math.max(holdAltitude + (boat ? 0.55D : 0.12D),
                        vehicle.getY() + (boat ? 0.55D : 0.10D));
            else if (downward) holdAltitude = Math.min(holdAltitude - (boat ? 0.20D : 0.18D),
                    vehicle.getY() - 0.14D);
        }
        double error = holdAltitude - vehicle.getY();
        double velocity = Double.isFinite(measuredVertical) ? measuredVertical : originalY;
        double desired = Mth.clamp(error * 0.24D - velocity * 0.55D,
                -0.27D, boat ? 0.27D : 0.23D);
        if (!upward && !downward && Math.abs(error) < 0.025D
                && Math.abs(velocity) < 0.025D) desired = 0.0D;
        if (downward) desired = Math.min(desired, -0.10D);
        if (upward && vehicle.onGround()) desired = Math.max(desired, 0.16D);
        if (!Double.isNaN(surfaceDistance) && upward && surfaceDistance > -0.08D)
            desired = Math.max(desired, originalY);

        boolean airborne = !vehicle.onGround() && !vehicle.isInWater()
                && Double.isNaN(surfaceDistance);
        if (airborne && boat) {
            if (downward) {
                desired = Math.min(desired, -0.18D);
            } else if (upward) {
                if (ascentBuffer <= 2.0D && boatFloatingTicks < 9) {
                    desired = 0.88D - 0.055D * Math.min(2.0D, ascentBuffer);
                } else {
                    desired = -0.09D;
                }
            } else if (ascentBuffer < 0.9D && holdAltitude - vehicle.getY() > 0.7D
                    && boatFloatingTicks < 6) {
                desired = 0.76D;
            } else {
                desired = -0.075D;
            }
        } else if (airborne && upward && airborneTicks > 30
                && ascentBuffer + BUFFER_RESERVE >= MOUNT_ASCENT_LIMIT) {
            holdAltitude = Math.min(holdAltitude, vehicle.getY() + 0.14D);
            desired = Math.min(desired, 0.0D);
        }
        if (!(airborne && boat) && Double.isFinite(lastVertical) && Math.abs(lastVertical) <= 0.5D)
            desired = Mth.clamp(desired, lastVertical - 0.085D, lastVertical + 0.085D);
        return Mth.clamp(desired, -0.27D, boat && airborne ? 0.9D : 0.65D);
    }

    private double sustainedMountMotion(Entity vehicle, double requestedY, boolean upward, boolean downward) {
        if (downward) {
            lastMountPulseTick = vehicle.tickCount;
            double falling = Double.isFinite(measuredVertical) ? measuredVertical - 0.08D : -0.14D;
            return Math.min(requestedY, Mth.clamp(falling, -1.2D, -0.14D));
        }
        if (upward && Double.isFinite(holdAltitude))
            holdAltitude = Math.min(holdAltitude, vehicle.getY() + 0.75D);
        double error = Double.isFinite(holdAltitude) ? holdAltitude - vehicle.getY() : 0.0D;
        boolean canPulse = (lastMountPulseTick == Integer.MIN_VALUE
                || vehicle.tickCount - lastMountPulseTick >= 5)
                && ascentBuffer < 2.0D && hoverBuffer < 2.0D;
        if (canPulse && (error > 0.12D || upward && error > -0.16D)) {
            lastMountPulseTick = vehicle.tickCount;
            return Mth.clamp(0.37D + error * 0.28D, 0.37D, 0.68D);
        }
        return (unsupportedTicks & 1) == 0 ? -0.065D : -0.145D;
    }

    private double surfaceMotion(double originalY, double distance, boolean boat) {
        double error = distance - (boat ? 0.07D : -0.02D);
        double velocity = Double.isFinite(measuredVertical) ? measuredVertical : originalY;
        double target = Mth.clamp(error * (boat ? 0.24D : 0.30D) - velocity * 0.55D,
                -0.12D, boat ? 0.12D : 0.16D);
        if (Math.abs(error) < 0.02D && Math.abs(velocity) < 0.02D) target = 0.0D;
        if (Double.isFinite(lastVertical) && Math.abs(lastVertical) < 0.5D)
            target = Mth.clamp(target, lastVertical - 0.065D, lastVertical + 0.065D);
        return target;
    }

    private static double updateBuffer(double current, boolean violation, double decay) {
        return violation ? Math.min(5.0D, current + 1.0D)
                : Math.max(0.0D, current - decay);
    }
}
