/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.misc.ServerRPSpoofer;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ResourcePackSpoofMixin {

    @Final
    @Shadow
    protected Connection connection;

    @Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
    private void combatant$spoofResourcePack(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        ServerRPSpoofer module = Modules.get(ServerRPSpoofer.class);
        if (module != null && module.intercept(packet, connection)) {
            ci.cancel();
        }
    }
}
