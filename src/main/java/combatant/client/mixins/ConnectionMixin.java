/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.RunningOnDifferentThreadException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.events.Events;
import combatant.client.events.impl.PacketEvent;
import combatant.client.util.network.BlinkManager;
import combatant.client.util.proxy.ProxyNettyInstaller;

@Mixin(Connection.class)
public class ConnectionMixin {

    @Shadow
    private static <T extends PacketListener> void genericsFtw(Packet<T> packet, PacketListener listener) {
        throw new AssertionError();
    }

    @Inject(method = "genericsFtw", at = @At("HEAD"), cancellable = true)
    private static <T extends PacketListener> void combatant$onHandlePacket(Packet<T> packet, PacketListener listener, CallbackInfo ci) {
        if (BlinkManager.isSilentlyHandlingPackets()) return;
        if (!Events.BUS.hasListeners(PacketEvent.Receive.class)) return;
        if (packet instanceof ClientboundBundlePacket packs) {
            ci.cancel();
            for (Packet<?> p : packs.subPackets()) {
                try {
                    combatant$handlePacketUnchecked(p, listener);
                } catch (RunningOnDifferentThreadException ignored) {
                }
            }
        } else {
            PacketEvent.Receive event = new PacketEvent.Receive(packet);
            Events.BUS.post(event);
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @Unique
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void combatant$handlePacketUnchecked(Packet<?> packet, PacketListener listener) {
        ConnectionMixin.genericsFtw((Packet) packet, listener);
    }

    @Inject(method = "genericsFtw", at = @At("TAIL"))
    private static <T extends PacketListener> void combatant$onHandlePacketPost(Packet<T> packet, PacketListener listener, CallbackInfo ci) {
        if (BlinkManager.isSilentlyHandlingPackets()) return;
        if (!Events.BUS.hasListeners(PacketEvent.ReceivePost.class)) return;
        if (packet instanceof ClientboundBundlePacket packs) {
            for (Packet<?> p : packs.subPackets()) {
                PacketEvent.ReceivePost event = new PacketEvent.ReceivePost(p);
                Events.BUS.post(event);
            }
        } else {
            PacketEvent.ReceivePost event = new PacketEvent.ReceivePost(packet);
            Events.BUS.post(event);
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void combatant$onSendPacketPre(Packet<?> packet, CallbackInfo ci) {
        if (BlinkManager.isSilentlyHandlingPackets()) return;
        if (!Events.BUS.hasListeners(PacketEvent.Send.class)) return;
        PacketEvent.Send event = new PacketEvent.Send(packet);
        Events.BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("RETURN"))
    private void combatant$onSendPacketPost(Packet<?> packet, CallbackInfo ci) {
        if (BlinkManager.isSilentlyHandlingPackets()) return;
        if (!Events.BUS.hasListeners(PacketEvent.SendPost.class)) return;
        PacketEvent.SendPost event = new PacketEvent.SendPost(packet);
        Events.BUS.post(event);
    }

    @Inject(method = "configurePacketHandler", at = @At("HEAD"))
    private void combatant$installProxyHandler(ChannelPipeline pipeline, CallbackInfo ci) {
        ProxyNettyInstaller.install(pipeline);
    }


}
