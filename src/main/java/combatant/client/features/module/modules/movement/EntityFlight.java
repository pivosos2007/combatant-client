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
    private int launchTicks;
    private int correctionTicks;
    private int cycle;
    private double ascentBuffer;
    private double hoverBuffer;
    private double lastVertical = Double.NaN;
    private double horizontalBuffer;
    private double holdAltitude = Double.NaN;
    private boolean flightStarted;

    @Override
    public void onEnable() {
        resetState(null);
    }

    @Override
    public void onDisable() {
        resetState(null);
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
        launchTicks = 0;
        correctionTicks = 0;
        cycle = 0;
        ascentBuffer = 0.0D;
        hoverBuffer = 0.0D;
        horizontalBuffer = 0.0D;
        holdAltitude = Double.NaN;
        lastVertical = Double.NaN;
        flightStarted = false;
    }

    @EventHandler
    private void onCorrection(PacketEvent.Receive event) {
        if (mode.get() != Mode.VULCAN || mc.player == null
                || mc.player.getVehicle() == null) return;
        if (event.getPacket() instanceof ClientboundMoveVehiclePacket
                || event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            correctionTicks = 60;
            ascentBuffer = Math.max(ascentBuffer, MOUNT_ASCENT_LIMIT - BUFFER_RESERVE);
            hoverBuffer = Math.max(hoverBuffer, HOVER_LIMIT - BUFFER_RESERVE);
            horizontalBuffer = Math.max(horizontalBuffer, HOVER_LIMIT - BUFFER_RESERVE);
            holdAltitude = Double.NaN;
            flightStarted = false;
            launchTicks = 0;
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
        boolean boat = vehicle instanceof AbstractBoat;
        boolean inWater = vehicle.isInWater();
        boolean onGround = vehicle.onGround();
        double surfaceDistance = VehicleMotion.waterSurfaceDistance(vehicle);
        boolean atSurface = !Double.isNaN(surfaceDistance);
        boolean airborne = !onGround && !inWater && !atSurface;
        boolean newTick = lastTick != vehicle.tickCount;
        if (newTick) {
            lastTick = vehicle.tickCount;
            cycle++;
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
                }
                if (!flightStarted && y > 0.13D && unsupportedTicks <= 3) {
                    flightStarted = true;
                    launchTicks = 12;
                    holdAltitude = Double.NaN;
                }
                if (flightStarted && launchTicks > 0) {
                    if (newTick) launchTicks--;
                    if (launchTicks == 0 || y <= 0.03D && launchTicks <= 8) {
                        holdAltitude = vehicle.getY() + Math.max(0.0D, y);
                        launchTicks = 0;
                    }
                } else if (flightStarted || atSurface) {
                    y = vulcanVertical(vehicle, y, surfaceDistance, false, upward, downward, newTick);
                }
            }
        } else if (atSurface && !upward && !downward) {
            y = surfaceMotion(y, surfaceDistance, boat);
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

        if (Double.isNaN(holdAltitude)) holdAltitude = vehicle.getY();
        if (newTick) {
            if (upward) holdAltitude += boat ? 0.15D : 0.11D;
            if (downward) holdAltitude -= boat ? 0.16D : 0.12D;
        }
        double altitudeError = holdAltitude - vehicle.getY();
        boolean airborne = !vehicle.onGround() && !vehicle.isInWater()
                && Double.isNaN(surfaceDistance);
        double desired;
        if (airborne && airborneTicks > 30) {
            int phase = cycle % (boat ? 10 : 6);
            if (phase == 0) {
                desired = (boat ? 0.29D : 0.33D)
                        + Mth.clamp(altitudeError * 0.30D, -0.14D, 0.34D);
            } else if (boat) {
                desired = (phase & 1) == 0 ? -0.06D : -0.01D;
            } else {
                desired = switch (phase) {
                    case 1 -> -0.015D;
                    case 2 -> -0.10D;
                    case 3 -> -0.03D;
                    case 4 -> -0.12D;
                    default -> -0.065D;
                };
            }
            if (downward) desired -= 0.035D;
        } else {
            desired = Mth.clamp(altitudeError * 0.28D - originalY * 0.34D,
                    -0.25D, boat ? 0.28D : 0.22D);
            if (!upward && !downward && Math.abs(altitudeError) < 0.04D) {
                desired = Mth.clamp(desired, -0.10D, 0.10D);
            }
            if (downward) desired = Math.min(desired, -0.08D);
        }
        if (!Double.isNaN(surfaceDistance) && upward && surfaceDistance > -0.08D) {
            desired = Math.max(desired, originalY);
        }

        boolean ascentRisk = airborne && (boat || airborneTicks > 30) && desired > 0.0D;
        boolean hoverRisk = airborne && (boat || airborneTicks > 30)
                && !Double.isNaN(lastVertical)
                && desired > (boat ? -0.05D : -1.15D)
                && Math.abs(desired - lastVertical) < (boat ? 0.025D : 0.05D);
        if (ascentRisk && ascentBuffer + BUFFER_RESERVE > (boat ? BOAT_ASCENT_LIMIT : MOUNT_ASCENT_LIMIT)) {
            desired = Math.min(desired, -0.055D);
        }
        if (hoverRisk && hoverBuffer + BUFFER_RESERVE > HOVER_LIMIT) {
            desired = boat ? Math.min(desired, -0.055D)
                    : (cycle & 1) == 0 ? -0.055D : -0.115D;
        }
        return Mth.clamp(desired, -0.30D, 0.65D);
    }

    private static double surfaceMotion(double originalY, double distance, boolean boat) {
        double error = distance - (boat ? 0.07D : -0.02D);
        double target = error * (boat ? 0.24D : 0.30D) - originalY * 0.35D;
        return Mth.clamp(target, -0.12D, boat ? 0.12D : 0.16D);
    }

    private static double updateBuffer(double current, boolean violation, double decay) {
        return violation ? Math.min(5.0D, current + 1.0D)
                : Math.max(0.0D, current - decay);
    }
}
