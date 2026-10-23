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
        id = "entitycontrol",
        displayName = "EntityControl",
        aliases = {"MountControl", "EntitySpeed", "MountSpeed"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.BASIC,
        description = "module.entitycontrol.description")
public final class EntityControl extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue rotate = bool("entityControlRotate", "rotate", true);
    private final BooleanValue airControl = bool("entityControlAirControl", "air_control", true);
    private final NumberValue<Double> jumpHeight = num("entityControlJumpHeight", "jump_height", 0.0D, 0.0D, 2.0D);
    private final NumberValue<Double> speed = num("entityControlSpeed", "speed", 0.0D, 0.0D, 5.0D);
    private final BooleanValue speedOnlyOnGround = visibleWhen(
            bool("entityControlSpeedOnlyOnGround", "speed_only_on_ground", false), () -> speed.get() > 0.0D);
    private final BooleanValue speedInWater = visibleWhen(
            bool("entityControlSpeedInWater", "speed_in_water", true), () -> speed.get() > 0.0D);

    @EventHandler
    private void onVehicleMove(VehicleMoveEvent event) {
        LocalPlayer player = mc.player;
        Entity vehicle = event.getVehicle();
        if (player == null) return;

        if (rotate.get()) {
            vehicle.setYRot(player.getYRot());
            vehicle.setXRot(player.getXRot() * 0.5F);
        }

        Vec3 movement = event.getMovement();
        double x = movement.x;
        double y = movement.y;
        double z = movement.z;
        boolean changed = false;

        if (airControl.get() && !vehicle.onGround() && VehicleMotion.hasHorizontalInput(mc)) {
            double carried = Math.hypot(x, z);
            Vec3 steer = VehicleMotion.horizontal(mc, player, carried);
            x = steer.x;
            z = steer.z;
            changed = true;
        }

        if (speed.get() > 0.0D && VehicleMotion.hasHorizontalInput(mc)
                && (!speedOnlyOnGround.get() || vehicle.onGround())
                && (speedInWater.get() || !vehicle.isInWater())) {
            Vec3 fixed = VehicleMotion.horizontal(mc, player, speed.get());
            x = fixed.x;
            z = fixed.z;
            changed = true;
        }

        if (jumpHeight.get() > 0.0D && vehicle.onGround() && mc.options.keyJump.isDown() && y <= 0.05D) {
            y = jumpHeight.get();
            changed = true;
        }

        if (changed) {
            event.setMovement(new Vec3(x, y, z));
        }
    }
}
