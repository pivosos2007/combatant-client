/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.features.module.modules.visuals.Freecam;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerFreecamMixin {

    @Unique
    private boolean combatant$freecamFreezeApplied;
    @Unique
    private boolean combatant$previousNoGravity;
    @Unique
    private boolean combatant$previousNoPhysics;

    @Inject(method = "tick", at = @At("HEAD"))
    private void combatant$stabilizeFreecamFreeze(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (Freecam.shouldFreezeIntegratedPlayer(player.getUUID())) {
            if (!combatant$freecamFreezeApplied) {
                combatant$previousNoGravity = player.isNoGravity();
                combatant$previousNoPhysics = player.noPhysics;
                combatant$freecamFreezeApplied = true;
            }
            player.setNoGravity(true);
            player.noPhysics = true;
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0f;
            player.setOnGround(true);
            return;
        }

        if (combatant$freecamFreezeApplied) {
            player.setNoGravity(combatant$previousNoGravity);
            player.noPhysics = combatant$previousNoPhysics;
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0f;
            player.setOnGround(false);
            combatant$freecamFreezeApplied = false;
        }

        if (Freecam.shouldUseSafeLanding(player.getUUID())) {
            if (hasSupport(player)) {
                player.setDeltaMovement(Vec3.ZERO);
                player.fallDistance = 0.0f;
                player.setOnGround(true);
                Freecam.finishSafeLanding(player.getUUID());
            } else {
                double nextY = Math.max(-0.35, player.getDeltaMovement().y - 0.08);
                player.setDeltaMovement(0.0, nextY, 0.0);
                player.fallDistance = 0.0f;
                player.setOnGround(false);
            }
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void combatant$clearFreecamFallDistance(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (Freecam.shouldFreezeIntegratedPlayer(player.getUUID())) {
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0f;
            player.setOnGround(true);
        } else if (Freecam.shouldUseSafeLanding(player.getUUID()) && hasSupport(player)) {
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0f;
            player.setOnGround(true);
            Freecam.finishSafeLanding(player.getUUID());
        }
    }

    @Unique
    private static boolean hasSupport(ServerPlayer player) {
        return player.level().getBlockCollisions(
                player, player.getBoundingBox().move(0.0, -0.06, 0.0)).iterator().hasNext();
    }
}