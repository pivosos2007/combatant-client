package combatant.client.mixins;

import combatant.client.render.engine.deferred.DeferredHistoryResetReason;
import combatant.client.render.engine.deferred.DevDeferredRuntime;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class DevDeferredClientPacketListenerMixin {
    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void combatant$devDeferredTeleportReset(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        DevDeferredRuntime.world().requestHistoryReset(DeferredHistoryResetReason.TELEPORT);
    }
}
