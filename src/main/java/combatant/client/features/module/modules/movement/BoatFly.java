/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.VehicleMoveEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.player.VehicleMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "boatfly",
        displayName = "BoatFly",
        aliases = {"VehicleFly"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.RAGE,
        description = "module.boatfly.description")
public final class BoatFly extends Module {

    private static final double ANTI_KICK_DROP = -0.04D;

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> speed = num("boatFlySpeed", "speed", 1.0D, 0.1D, 5.0D);
    private final NumberValue<Double> verticalSpeed = num("boatFlyVerticalSpeed", "vertical_speed", 0.5D, 0.0D, 3.0D);
    private final NumberValue<Double> glide = num("boatFlyGlide", "glide", 0.0D, 0.0D, 0.5D);
    private final BooleanValue noclip = bool("boatFlyNoclip", "noclip", false);
    private final BooleanValue antiKick = bool("boatFlyAntiKick", "anti_kick", true);
    private final NumberValue<Integer> antiKickInterval =
            visibleWhen(num("boatFlyAntiKickInterval", "anti_kick_interval", 20, 5, 80), antiKick::get);

    private int airTicks;
    private Entity noclipVehicle;

    @Override
    public void onEnable() {
        airTicks = 0;
    }

    @Override
    public void onDisable() {
        airTicks = 0;
        restoreNoclip();
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        Entity vehicle = player == null ? null : player.getVehicle();
        if (vehicle == null || vehicle.getControllingPassenger() != player) {
            airTicks = 0;
            restoreNoclip();
            return;
        }

        vehicle.setYRot(player.getYRot());
        if (noclip.get()) {
            vehicle.noPhysics = true;
            noclipVehicle = vehicle;
        } else {
            restoreNoclip();
        }
    }

    @EventHandler
    private void onVehicleMove(VehicleMoveEvent event) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        Vec3 horizontal = VehicleMotion.horizontal(mc, player, speed.get());
        double vertical;
        if (mc.options.keyJump.isDown()) {
            vertical = verticalSpeed.get();
        } else if (mc.options.keyShift.isDown()) {
            vertical = -verticalSpeed.get();
        } else {
            vertical = -glide.get();
        }

        if (antiKick.get()) {
            airTicks++;
            if (airTicks >= antiKickInterval.get()) {
                airTicks = 0;
                vertical = Math.min(vertical, ANTI_KICK_DROP);
            }
        }

        event.setMovement(new Vec3(horizontal.x, vertical, horizontal.z));
    }

    private void restoreNoclip() {
        if (noclipVehicle != null) {
            noclipVehicle.noPhysics = false;
            noclipVehicle = null;
        }
    }
}
