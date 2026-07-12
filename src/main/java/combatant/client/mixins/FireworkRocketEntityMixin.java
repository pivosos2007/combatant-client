/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import combatant.client.events.Events;
import combatant.client.events.impl.FireworkEvent;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;

@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {

    @Shadow
    @Nullable
    private LivingEntity attachedToEntity;

    @Unique
    private static Rotation resolveMoveRotation(LocalPlayer player) {
        return RotationManager.INSTANCE.getMovementRotation();
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 combatant$fireworkMoveRotation(LivingEntity instance, Operation<Vec3> original) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || attachedToEntity != mc.player) {
            return original.call(instance);
        }

        return resolveMoveRotation(mc.player).directionVector();
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;",
                    ordinal = 0
            )
    )
    private Vec3 combatant$fireworkVelocityHook(LivingEntity instance, Operation<Vec3> original) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 velocity = original.call(instance);
        if (mc == null || attachedToEntity != mc.player || !Events.BUS.hasListeners(FireworkEvent.class)) {
            return velocity;
        }

        FireworkEvent event = new FireworkEvent(velocity);
        Events.BUS.post(event);
        return event.getVector();
    }
}
