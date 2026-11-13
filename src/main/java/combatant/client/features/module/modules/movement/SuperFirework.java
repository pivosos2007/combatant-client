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
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.ElytraTarget;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.player.ElytraFlightPhysics;

@ModuleInfo(
        id = "superfirework",
        displayName = "SuperFirework",
        category = ModuleCategory.MOVEMENT, subcategory = ModuleSubcategory.RAGE,
        description = "module.superfirework.description")
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

    /**
     * Transform the velocity in exactly the same way for independent flight and
     * ElytraTarget steering. All speed/mode settings belong to SuperFirework.
     */
    private Vec3 applyMode(Vec3 velocity, float yaw, float pitch, Vec3 movementDirection) {
        int quadrant = yaw > 0.0f ? 45 : -45;
        double acceleration = Math.abs((yaw + quadrant) % 90.0 - quadrant) / 45.0;
        double rotationBoost = 1.0 + 0.3 * acceleration * acceleration;
        boolean accelerateY = Math.abs(pitch) > 60.0f;

        if (mode.get() == FireworkMode.GRIM) {
            return new Vec3(
                    velocity.x * rotationBoost,
                    accelerateY ? velocity.y * rotationBoost : velocity.y,
                    velocity.z * rotationBoost
            );
        }

        double customSpeed = speed.get() / 20.0;
        return new Vec3(
                movementDirection.x * customSpeed * rotationBoost,
                movementDirection.y * customSpeed * (accelerateY ? rotationBoost : 1.0),
                movementDirection.z * customSpeed * rotationBoost
        );
    }

    /**
     * Vanilla 26.3 attached rocket update: 0.5 * inputVelocity + 0.85 * look.
     * Used by ElytraTarget only to predict the result of *this module's* settings.
     */
    public Vec3 estimateBoostVelocity(Vec3 currentVelocity, Rotation steering) {
        Vec3 direction = steering.directionVector();
        Vec3 substitute = applyMode(currentVelocity, steering.yaw(), steering.pitch(), direction);
        return ElytraFlightPhysics.rocket(substitute, steering);
    }

    @EventHandler
    public void onFirework(FireworkEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        ElytraTarget elytraTarget = Modules.get(ElytraTarget.class);
        if (elytraTarget != null && elytraTarget.isManagingFireworks()) {
            Rotation pursuit = elytraTarget.getFireworkSteeringRotation();
            if (pursuit != null) {
                // Steering is managed by ElytraTarget, mode and speed remain ours.
                event.setVector(applyMode(event.getVector(), pursuit.yaw(), pursuit.pitch(),
                        pursuit.directionVector()));
            }
            // Never substitute camera rotation when pursuit direction is unavailable.
            return;
        }

        Rotation rotation = resolveRotation(player);
        Rotation movement = resolveMoveRotation(player);
        event.setVector(applyMode(event.getVector(), rotation.yaw(), movement.pitch(),
                movement.directionVector()));
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
