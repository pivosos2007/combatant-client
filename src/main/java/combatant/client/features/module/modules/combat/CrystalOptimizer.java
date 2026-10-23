/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.AttackEntityEvent;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import combatant.client.util.player.NetworkStatsUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Takes a crystal out of the client world as soon as a hit on it was sent, instead of waiting for the server's
 * remove packet. The spot is free for the next placement a full round trip earlier, and an
 * {@link AutoCrystal} with instant place is told right away.
 *
 * <p>The removal is a bet: the server may refuse the hit (out of reach, no damage, anticheat). Every removed crystal
 * is remembered as a {@link Ghost}; when no remove packet arrived after the confirm window the crystal is put back
 * and never optimised again, so a refused hit cannot leave the client believing in an empty cell.
 */
@ModuleInfo(
        id = "crystaloptimizer",
        displayName = "CrystalOptimizer",
        aliases = {"CrystalRemover", "InstantCrystalRemove", "FastCrystal"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.ATTACK,
        description = "module.crystaloptimizer.description")
public final class CrystalOptimizer extends Module {

    private static final int MIN_CONFIRM_TICKS = 2;
    private static final int PING_MARGIN_TICKS = 2;

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue checkRange = bool("crystalOptimizerCheckRange", "check_range", true);
    private final BooleanValue requireDamage = bool("crystalOptimizerRequireDamage", "require_damage", true);
    private final BooleanValue restoreUnconfirmed = bool("crystalOptimizerRestore", "restore_unconfirmed", true);
    private final BooleanValue pingAdaptive = visibleWhen(
            bool("crystalOptimizerPingAdaptive", "ping_adaptive", true), restoreUnconfirmed::get);
    private final NumberValue<Integer> confirmTicks = visibleWhen(
            num("crystalOptimizerConfirmTicks", "confirm_ticks", 4, MIN_CONFIRM_TICKS, 20), restoreUnconfirmed::get);
    private final BooleanValue triggerAutoCrystal = bool("crystalOptimizerTriggerAutoCrystal", "trigger_autocrystal", true);

    /** Crystals whose hit went out this tick and that still have to leave the world, by entity id. */
    private final Map<Integer, EndCrystal> pending = new ConcurrentHashMap<>();
    /** Removed on our own account, waiting for the server to agree. */
    private final Map<Integer, Ghost> ghosts = new ConcurrentHashMap<>();
    /** Ids the server did not remove after we did; they are left to the normal remove packet from now on. */
    private final Set<Integer> stubborn = ConcurrentHashMap.newKeySet();

    private ClientLevel trackedLevel;
    private int tickCounter;

    @Override
    public void onEnable() {
        forget();
    }

    @Override
    public void onDisable() {
        ClientLevel level = mc.level;
        if (level != null && level == trackedLevel) {
            for (Ghost ghost : ghosts.values()) revive(level, ghost);
        }
        forget();
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!isEnabled() || !(event.getTarget() instanceof EndCrystal crystal)) return;
        LocalPlayer player = mc.player;
        if (player == null || event.getPlayer() != player || mc.level == null || crystal.level() != mc.level) return;
        if (stubborn.contains(crystal.getId()) || ghosts.containsKey(crystal.getId())) return;
        if (!servedByAttack(player, crystal)) return;
        pending.put(crystal.getId(), crystal);
    }

    /** Start of the client tick: carry out the removals queued by the attacks of the previous tick. */
    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled()) return;
        ClientLevel level = mc.level;
        if (mc.player == null || level == null) {
            forget();
            return;
        }
        if (level != trackedLevel) {
            forget();
            trackedLevel = level;
        }
        tickCounter++;

        boolean removed = false;
        if (!pending.isEmpty()) {
            List<EndCrystal> batch = new ArrayList<>(pending.values());
            pending.clear();
            for (EndCrystal crystal : batch) {
                if (crystal.isRemoved() || level.getEntity(crystal.getId()) != crystal) continue;
                level.removeEntity(crystal.getId(), Entity.RemovalReason.DISCARDED);
                if (restoreUnconfirmed.get()) ghosts.put(crystal.getId(), Ghost.of(crystal, tickCounter));
                removed = true;
            }
        }
        restoreExpired(level);
        if (removed && triggerAutoCrystal.get()) {
            AutoCrystal autoCrystal = Modules.get(AutoCrystal.class);
            if (autoCrystal != null) autoCrystal.onCrystalsRemoved();
        }
    }

    /** Network thread: the server confirmed a removal, so the ghost was right and there is nothing to restore. */
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || !(event.getPacket() instanceof ClientboundRemoveEntitiesPacket packet)) return;
        for (int id : packet.entityIds()) {
            ghosts.remove(id);
            pending.remove(id);
            stubborn.remove(id);
        }
    }

    /** Whether the hit that was just sent can really break the crystal on the server. */
    private boolean servedByAttack(LocalPlayer player, EndCrystal crystal) {
        if (crystal.isRemoved() || !crystal.isAlive()) return false;
        if (player.isSpectator() || player.isDeadOrDying()) return false;
        if (mc.level == null || !mc.level.getWorldBorder().isWithinBounds(crystal.blockPosition())) return false;
        if (checkRange.get()
                && !player.isWithinAttackRange(player.getMainHandItem(), crystal.getBoundingBox(), 0.0)) {
            return false;
        }
        // A hit that deals no damage (weakness on bare hands, a stripped weapon) does not break the crystal.
        if (!requireDamage.get()) return true;
        double dmg = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        var weakness = player.getEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
        if (weakness != null && dmg <= 4.0 * (weakness.getAmplifier() + 1)
                && player.getMainHandItem().isEmpty()) {
            return false;
        }
        return dmg > 0.0;
    }

    private void restoreExpired(ClientLevel level) {
        if (ghosts.isEmpty()) return;
        int window = confirmWindow();
        Iterator<Ghost> iterator = ghosts.values().iterator();
        while (iterator.hasNext()) {
            Ghost ghost = iterator.next();
            if (tickCounter - ghost.removedAt() <= window) continue;
            iterator.remove();
            if (revive(level, ghost)) stubborn.add(ghost.id());
        }
    }

    private int confirmWindow() {
        int base = confirmTicks.get();
        if (!pingAdaptive.get()) return base;
        int pingTicks = (Math.max(0, NetworkStatsUtil.getPing(mc)) + 49) / 50;
        return Math.max(base, pingTicks + PING_MARGIN_TICKS);
    }

    private static boolean revive(ClientLevel level, Ghost ghost) {
        if (level.getEntity(ghost.id()) != null) return false;
        EndCrystal copy = new EndCrystal(level, ghost.x(), ghost.y(), ghost.z());
        copy.setId(ghost.id());
        copy.setUUID(ghost.uuid());
        copy.setShowBottom(ghost.showBottom());
        level.addEntity(copy);
        return true;
    }

    private void forget() {
        pending.clear();
        ghosts.clear();
        stubborn.clear();
        trackedLevel = null;
        tickCounter = 0;
    }

    private record Ghost(int id, UUID uuid, double x, double y, double z, boolean showBottom, int removedAt) {
        static Ghost of(EndCrystal crystal, int removedAt) {
            return new Ghost(crystal.getId(), crystal.getUUID(), crystal.getX(), crystal.getY(), crystal.getZ(),
                    crystal.showsBottom(), removedAt);
        }
    }
}
