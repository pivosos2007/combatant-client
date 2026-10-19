/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.automation.placement.PlacementTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports block-change acknowledgements to {@link PlacementTracker}. Hooked at the tail of the
 * handler itself: it runs on the main thread after vanilla applied the server's block states, which
 * the packet event hooks never see for replayed packets.
 */
@Mixin(ClientPacketListener.class)
public abstract class PlacementAckMixin {

    @Inject(method = "handleBlockChangedAck", at = @At("TAIL"))
    private void combatant$onBlockChangedAck(ClientboundBlockChangedAckPacket packet, CallbackInfo ci) {
        PlacementTracker.INSTANCE.onAcknowledged(packet.sequence());
    }
}
