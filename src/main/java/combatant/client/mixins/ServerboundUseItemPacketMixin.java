/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;

@Mixin(ServerboundUseItemPacket.class)
public class ServerboundUseItemPacketMixin {

    @Mutable
    @Shadow
    @Final
    private float yRot;

    @Mutable
    @Shadow
    @Final
    private float xRot;

    @Inject(method = "<init>(Lnet/minecraft/world/InteractionHand;IFF)V", at = @At("RETURN"))
    private void combatant$applyRotation(InteractionHand hand, int sequence, float yaw, float pitch, CallbackInfo ci) {
        Rotation rotation = RotationManager.INSTANCE.getCurrentRotation();
        if (rotation == null) {
            return;
        }

        this.yRot = rotation.yaw();
        this.xRot = rotation.pitch();
    }
}
