/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.FireworkEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;

//todo Description
@ModuleInfo(
        id = "superfirework",
        displayName = "SuperFirework",
        category = ModuleCategory.MOVEMENT
)
public class SuperFirework extends Module {

    private final EnumValue<FireworkMode> mode =
            enumSetting("superfirework_mode", "mode", FireworkMode.GRIM, FireworkMode.GRIM, FireworkMode.CUSTOM);
    private final NumberValue<Float> speed =
            visibleWhen(num("superfirework_speed", "speed", 20.0f, 1.0f, 100.0f), this::isCustomMode);

    private static Rotation resolveRotation(LocalPlayer player) {
        Rotation rotation = RotationManager.INSTANCE.getCurrentRotation();
        if (rotation != null) {
            return rotation;
        }
        return new Rotation(player.getYRot(), player.getXRot(), true);
    }

    private static Rotation resolveMoveRotation(LocalPlayer player) {
        return RotationManager.INSTANCE.getMovementRotation();
    }

    @EventHandler
    public void onFirework(FireworkEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        Rotation rotation = resolveRotation(player);
        Rotation moveRotation = resolveMoveRotation(player);
        float yaw = rotation.yaw();
        float movePitch = moveRotation.pitch();

        if (mode.get() == FireworkMode.GRIM) {
            int ff = yaw > 0.0f ? 45 : -45;
            double acceleration = Math.abs((yaw + ff) % 90.0 - ff) / 45.0;
            double boost = 1.0 + (0.3 * acceleration * acceleration);
            boolean yAcceleration = Math.abs(movePitch) > 60.0f;

            Vec3 vector = event.getVector();
            event.setVector(new Vec3(
                    vector.x * boost,
                    yAcceleration ? vector.y * boost : vector.y,
                    vector.z * boost
            ));
            return;
        }

        if (mode.get() == FireworkMode.CUSTOM) {
            int ff = yaw > 0.0f ? 45 : -45;
            double acceleration = Math.abs((yaw + ff) % 90.0 - ff) / 45.0;
            double rotationBoost = 1.0 + (0.3 * acceleration * acceleration);
            boolean yAcceleration = Math.abs(movePitch) > 60.0f;

            Vec3 direction = moveRotation.directionVector();
            float customSpeed = speed.get() / 20.0f;
            double finalSpeed = customSpeed * rotationBoost;

            event.setVector(new Vec3(
                    direction.x * finalSpeed,
                    yAcceleration ? direction.y * finalSpeed : direction.y * customSpeed,
                    direction.z * finalSpeed
            ));
        }
    }

    private boolean isCustomMode() {
        return mode.get() == FireworkMode.CUSTOM;
    }

    @Getter
    @RequiredArgsConstructor
    private enum FireworkMode implements EnumValue.IdProvider {
        GRIM("Grim"),
        CUSTOM("Custom");

        private final String id;
    }
}
