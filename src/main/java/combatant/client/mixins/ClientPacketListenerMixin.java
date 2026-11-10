/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.events.impl.ClientEntityAddedEvent;
import combatant.client.events.impl.ClientEntitiesRemovedEvent;
import combatant.client.events.impl.ClientPlayerInfoUpdatedEvent;
import combatant.client.events.impl.ClientPlayerInfoRemovedEvent;

import combatant.client.events.impl.SoundFilterEvent;
import combatant.client.events.impl.SoundObservedEvent;
import combatant.client.events.impl.TotemActivationEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.events.Events;
import combatant.client.events.impl.ClientPlayerAbilitiesEvent;
import combatant.client.events.impl.PvpChatEvent;
import combatant.client.events.impl.PvpTabEvent;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.player.NoRotate;
import combatant.client.features.module.modules.visuals.WorldTweaks;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.logging.ServerDumpUtil;
import combatant.client.util.player.NetworkStatsUtil;
import combatant.client.util.player.effect.StatusEffectHeuristics;
import combatant.client.util.pvp.client.CooldownsState;
import combatant.client.util.pvp.opponents.OpponentCooldownManager;
import combatant.client.util.pvp.opponents.TotemPopCounter;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Unique
    private static boolean combatant$removingCrystal;

    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void combatant$autoCrystalSpawn(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (packet.getType() != EntityTypes.END_CRYSTAL) return;
        ClientLevel level = Minecraft.getInstance().level;
        var autoCrystal = Modules.get(combatant.client.features.module.modules.combat.AutoCrystal.class);
        if (level == null || autoCrystal == null) return;
        if (level.getEntity(packet.getId()) instanceof EndCrystal crystal) {
            try {
                autoCrystal.onCrystalSpawned(crystal);
            } catch (RuntimeException e) {
                combatant.client.runtime.error.FailureIsolation.reportModule(autoCrystal, "onCrystalSpawned", e);
            }
        }
    }

    /**
     * Captures crystal removals before vanilla removes the corresponding entities;
     * the TAIL hook notifies AutoCrystal after the removal has been applied.
     */
    @Inject(method = "handleRemoveEntities", at = @At("HEAD"))
    private void combatant$autoCrystalRemoveHead(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) return;
        combatant$removingCrystal = false;
        ClientLevel level = minecraft.level;
        if (level == null) return;
        for (int id : packet.entityIds()) {
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
        var autoCrystal = Modules.get(combatant.client.features.module.modules.combat.AutoCrystal.class);
        if (autoCrystal == null) return;
        try {
            autoCrystal.onCrystalsRemoved();
        } catch (RuntimeException e) {
            combatant.client.runtime.error.FailureIsolation.reportModule(autoCrystal, "onCrystalsRemoved", e);
        }
    }

    @Unique
    private boolean combatant$restoreServerRotation;
    @Unique
    private float combatant$restoreYRot;
    @Unique
    private float combatant$restoreXRot;
    @Unique
    private float combatant$restoreYRotO;
    @Unique
    private float combatant$restoreXRotO;

    @Inject(method = {"handleMovePlayer", "handleRotatePlayer"}, at = @At("HEAD"))
    private void combatant$captureRotationBeforeServerCorrection(CallbackInfo ci) {
        NoRotate noRotate = Modules.get(NoRotate.class);
        Player player = Minecraft.getInstance().player;
        if (noRotate == null || !noRotate.isEnabled() || player == null) {
            combatant$restoreServerRotation = false;
            return;
        }
        combatant$restoreServerRotation = true;
        combatant$restoreYRot = player.getYRot();
        combatant$restoreXRot = player.getXRot();
        combatant$restoreYRotO = player.yRotO;
        combatant$restoreXRotO = player.xRotO;
    }

    @Inject(method = {"handleMovePlayer", "handleRotatePlayer"}, at = @At("TAIL"))
    private void combatant$restoreRotationAfterServerCorrection(CallbackInfo ci) {
        if (!combatant$restoreServerRotation) return;
        combatant$restoreServerRotation = false;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        player.setYRot(combatant$restoreYRot);
        player.setXRot(combatant$restoreXRot);
        player.yRotO = combatant$restoreYRotO;
        player.xRotO = combatant$restoreXRotO;
    }

    // "TPS: 20.0" or "TPS: *20.0"
    @Unique
    private static final Pattern TPS_PATTERN =
            Pattern.compile("TPS:\\s*\\*?(\\d+(?:\\.\\d+)?)");
    // "Пинг: 57"
    @Unique
    private static final Pattern PING_PATTERN =
            Pattern.compile("Пинг:\\s*(\\d+)");

    @Inject(method = "handlePlayerAbilities", at = @At("HEAD"), cancellable = true)
    private void combatant$flightAbilities(ClientboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        if (Events.BUS.post(new ClientPlayerAbilitiesEvent(packet)).isCancelled()) ci.cancel();
    }

    @Inject(method = "handleTabListCustomisation", at = @At("TAIL"))
    private void combatant$captureTab(ClientboundTabListPacket packet, CallbackInfo ci) {
        String header = packet.header() == null ? "" : packet.header().getString();
        String footer = packet.footer() == null ? "" : packet.footer().getString();
        ServerDumpUtil.dumpTabText("HEADER", packet.header());
        ServerDumpUtil.dumpTabText("FOOTER", packet.footer());
        if (Events.BUS.hasListeners(PvpTabEvent.class)) {
            Events.BUS.post(new PvpTabEvent(header, footer));
        }
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void onChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        String msg = packet.content().getString();

        if (msg != null && !msg.isBlank() && Events.BUS.hasListeners(PvpChatEvent.class)) {
            Events.BUS.post(new PvpChatEvent(msg, PvpChatEvent.Source.GAME_MESSAGE));
        }
    }


    @Inject(method = "handlePlayerChat", at = @At("HEAD"))
    private void combatant$pvpChatMessage(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        String msg = packet == null ? null : packet.body().content();
        if (msg == null || msg.isBlank()) return;
        if (Events.BUS.hasListeners(PvpChatEvent.class)) {
            Events.BUS.post(new PvpChatEvent(msg, PvpChatEvent.Source.CHAT_MESSAGE));
        }
    }

    @Inject(method = "handleRemoveEntities", at = @At("TAIL"))
    private void combatant$onEntitiesDestroy(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        Events.BUS.post(new ClientEntitiesRemovedEvent(packet));
    }

    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void combatant$onEntitySpawn(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        Events.BUS.post(new ClientEntityAddedEvent(packet));
    }

    @Inject(method = "handlePlayerInfoUpdate", at = @At("TAIL"))
    private void combatant$onPlayerList(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        Events.BUS.post(new ClientPlayerInfoUpdatedEvent(packet));
    }

    @Inject(method = "handlePlayerInfoRemove", at = @At("TAIL"))
    private void combatant$onPlayerRemove(ClientboundPlayerInfoRemovePacket packet, CallbackInfo ci) {
        Events.BUS.post(new ClientPlayerInfoRemovedEvent(packet));
    }

    @Inject(method = "handleSetTime", at = @At("HEAD"))
    private void combatant$captureServerTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        WorldTweaks.setServerTimeOfDay(packet.gameTime());
    }

    @Inject(method = "handleTabListCustomisation", at = @At("TAIL"))
    private void combatant$onTab(ClientboundTabListPacket packet, CallbackInfo ci) {
        parse(packet.header());
    }

    @Unique
    private void parse(Component text) {
        if (text == null) return;

        String raw = text.getString();
        if (raw.isEmpty()) return;

        Matcher tps = TPS_PATTERN.matcher(raw);
        if (tps.find()) {
            try {
                NetworkStatsUtil.updateTabTps(Float.parseFloat(tps.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }

        Matcher ping = PING_PATTERN.matcher(raw);
        if (ping.find()) {
            try {
                NetworkStatsUtil.updateTabPing(Integer.parseInt(ping.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
    }



    @Inject(method = "handleSoundEvent", at = @At("HEAD"), cancellable = true)
    private void combatant$filterSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (packet == null || packet.getSound() == null) return;
        if (!Events.BUS.hasListeners(SoundFilterEvent.class)) return;
        if (Events.BUS.post(new SoundFilterEvent(packet.getSound().value().location())).isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSoundEntityEvent", at = @At("HEAD"), cancellable = true)
    private void combatant$filterSoundFromEntity(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (packet == null || packet.getSound() == null) return;
        if (!Events.BUS.hasListeners(SoundFilterEvent.class)) return;
        if (Events.BUS.post(new SoundFilterEvent(packet.getSound().value().location())).isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleParticleEvent", at = @At("HEAD"))
    private void combatant$heuristicParticle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        var p = packet.particle();
        // Log raw particles once per hook for diagnostics
        DebugLog.info("Particle packet type=%s pos=%.2f %.2f %.2f count=%d",
                p.getType().toString(),
                packet.x(), packet.y(), packet.z(),
                packet.count());
        StatusEffectHeuristics.handle(p, packet.x(), packet.y(), packet.z());
    }


    @Inject(method = "handleSoundEvent", at = @At("HEAD"))
    private void combatant$onPlaySound(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (packet == null || packet.getSound() == null) return;
        if (!Events.BUS.hasListeners(SoundObservedEvent.class)) return;
        Events.BUS.post(new SoundObservedEvent(packet.getSound().value().location(),
                new Vec3(packet.getX(), packet.getY(), packet.getZ())));
    }

    @Inject(method = "handleSoundEntityEvent", at = @At("HEAD"))
    private void combatant$onPlaySoundFromEntity(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (packet == null || packet.getSound() == null) return;
        if (!Events.BUS.hasListeners(SoundObservedEvent.class)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(packet.getId());
        if (entity == null) return;
        Events.BUS.post(new SoundObservedEvent(packet.getSound().value().location(), entity.position()));
    }

    @Inject(method = "handleUpdateMobEffect", at = @At("HEAD"))
    private void combatant$logStatusEffect(ClientboundUpdateMobEffectPacket packet, CallbackInfo ci) {
        DebugLog.server("Packet: add/refresh effect eid=%d effect=%s amp=%d dur=%d particles=%s icon=%s ambient=%s keepFading=%s",
                packet.getEntityId(),
                packet.getEffect().unwrapKey().map(k -> k.identifier().toString()).orElse("unknown"),
                packet.getEffectAmplifier(),
                packet.getEffectDurationTicks(),
                packet.isEffectVisible(),
                packet.effectShowsIcon(),
                packet.isEffectAmbient(),
                packet.shouldBlend()
        );
    }

    @Inject(method = "handleRemoveMobEffect", at = @At("HEAD"))
    private void combatant$logRemove(ClientboundRemoveMobEffectPacket packet, CallbackInfo ci) {
        DebugLog.server("Packet: remove effect eid=%d (%s) effect=%s",
                packet.entityId(),
                resolveName(packet.entityId()),
                packet.effect().unwrapKey().map(k -> k.identifier().toString()).orElse("unknown"));
    }

    @Unique
    private String resolveName(int entityId) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return "unknown";
        Entity e = mc.level.getEntity(entityId);
        if (e == null) return "unknown";
        if (e instanceof Player p) return p.getName().getString();
        return e.getName().getString();
    }


    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void combatant$onTotemStatus(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (packet.getEventId() == 35) { // Event byte 35 denotes totem activation.
            ClientPacketListener handler = (ClientPacketListener) (Object) this;
            Minecraft client = Minecraft.getInstance();
            // The handler runs once on the network thread and again after it is rescheduled to the main thread,
            // so an unguarded HEAD hook would count every pop twice.
            if (!client.isSameThread()) return;

            Entity entity = packet.getEntity(handler.getLevel());
            if (entity == null) return;
            if (entity instanceof Player player) {
                TotemPopCounter.recordPop(player);
            }
            if (entity.equals(client.player)) {
                Events.BUS.post(new TotemActivationEvent(client.player, true));
                return;
            }
            if (entity instanceof Player player) {
                Events.BUS.post(new TotemActivationEvent(player, false));
                if (CooldownsState.shouldTrackOpponents()) {
                    OpponentCooldownManager.recordUse(player.getUUID(), Items.TOTEM_OF_UNDYING);
                }
            }
        }
    }
}
