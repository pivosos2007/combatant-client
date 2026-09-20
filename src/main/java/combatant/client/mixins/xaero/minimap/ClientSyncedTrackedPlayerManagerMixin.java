/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.xaero.minimap;

import combatant.client.config.subsystem.MapUiConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.server.radar.tracker.SyncedTrackedPlayer;
import xaero.hud.minimap.player.tracker.synced.ClientSyncedTrackedPlayerManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Pseudo
@Mixin(ClientSyncedTrackedPlayerManager.class)
public abstract class ClientSyncedTrackedPlayerManagerMixin {

    @Shadow
    @Final
    private Map<UUID, SyncedTrackedPlayer> trackedPlayers;

    @Inject(
            method = "update(Ljava/util/UUID;DDDLnet/minecraft/resources/ResourceKey;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void combatant$denyServerPlayerData(UUID playerId,
                                                 double x,
                                                 double y,
                                                 double z,
                                                 ResourceKey<Level> dimension,
                                                 CallbackInfo ci) {
        if (!MapUiConfig.get().allowServerPlayerData()) {
            trackedPlayers.clear();
            ci.cancel();
        }
    }

    @Inject(method = "getPlayers()Ljava/lang/Iterable;", at = @At("HEAD"), cancellable = true)
    private void combatant$hideDeniedServerPlayerData(CallbackInfoReturnable<Iterable<SyncedTrackedPlayer>> cir) {
        if (!MapUiConfig.get().allowServerPlayerData()) {
            trackedPlayers.clear();
            cir.setReturnValue(List.of());
        }
    }
}
