/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.movement.Flight;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {

    @Final
    @Shadow
    protected Connection connection;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void combatant$flightSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (!((Object) this instanceof ClientPacketListener)) return;
        if (!(packet instanceof ServerboundMovePlayerPacket move)) return;
        Flight flight = Modules.get(Flight.class);
        if (flight == null || !flight.isEnabled()) return;

        ServerboundMovePlayerPacket replacement = flight.onSendMovePacket(move);
        if (replacement != null && replacement != move) {
            ci.cancel();
            connection.send(replacement);
        }
    }
}
