/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import combatant.client.util.aiming.RotationManager;

@Mixin(Item.class)
public class ItemMixin {

    @Redirect(
            method = "getPlayerPOVHitResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private static Vec3 combatant$raycastRotation(Player player, float pitch, float yaw) {
        return RotationManager.INSTANCE.getMovementRotation().directionVector();
    }
}
