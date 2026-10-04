/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.AutoCrystal;

/**
 * Feeds crystal spawns and removals to AutoCrystal the moment the client applies them, instead of
 * on the next tick.
 *
 * <p>{@code PacketEvent.ReceivePost} cannot do this: these handlers first run on the netty thread,
 * bail out with {@code RunningOnDifferentThreadException} and are replayed on the main thread by
 * {@code PacketProcessor} through {@code Packet.handle}, which never passes the hook in
 * {@code Connection.genericsFtw}. A TAIL inject here only completes on that main-thread replay, when
 * the entity is really in (or out of) the level. Kept apart from {@link ClientPacketListenerMixin}
 * so the upstream mixin stays untouched.</p>
 */
@Mixin(ClientPacketListener.class)
public class AutoCrystalPacketMixin {

    // Main thread only; set at HEAD because the entities are gone by TAIL.
    @Unique
    private static boolean combatant$removingCrystal;

    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void combatant$autoCrystalSpawn(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (packet.getType() != EntityTypes.END_CRYSTAL) return;
        ClientLevel level = Minecraft.getInstance().level;
        AutoCrystal autoCrystal = Modules.get(AutoCrystal.class);
        if (level == null || autoCrystal == null) return;
        if (level.getEntity(packet.getId()) instanceof EndCrystal crystal) autoCrystal.onCrystalSpawned(crystal);
    }

    @Inject(method = "handleRemoveEntities", at = @At("HEAD"))
    private void combatant$autoCrystalRemoveHead(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        // HEAD also runs on the netty thread before the handler reschedules itself; ignore that pass.
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) return;
        combatant$removingCrystal = false;
        ClientLevel level = mc.level;
        if (level == null) return;
        for (int id : packet.getEntityIds()) {
            if (level.getEntity(id) instanceof EndCrystal) {
                combatant$removingCrystal = true;
                return;
            }
        }
    }

    @Inject(method = "handleRemoveEntities", at = @At("TAIL"))
    private void combatant$autoCrystalRemoveTail(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        if (!combatant$removingCrystal) return;
        combatant$removingCrystal = false;
        AutoCrystal autoCrystal = Modules.get(AutoCrystal.class);
        if (autoCrystal != null) autoCrystal.onCrystalsRemoved();
    }
}
