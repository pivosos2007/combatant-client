/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autocrystal;

import combatant.client.util.player.NetworkStatsUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoCrystalTracker {
    private final Map<Integer, Long> deadCrystals = new ConcurrentHashMap<>();
    private final Map<Integer, AutoCrystalAttempt> attackedCrystals = new ConcurrentHashMap<>();
    private final Map<BlockPos, AutoCrystalAttempt> awaitingPositions = new ConcurrentHashMap<>();

    public void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        long deadTimeout = Math.max(50L, Math.max(0, NetworkStatsUtil.getPing(mc)) * 2L);
        deadCrystals.entrySet().removeIf(entry -> now - entry.getValue() > deadTimeout);
        attackedCrystals.entrySet().removeIf(entry -> entry.getValue().shouldRemove(mc.player));
        awaitingPositions.entrySet().removeIf(entry -> entry.getValue().shouldRemove(mc.player));
    }

    public void clear() {
        deadCrystals.clear();
        attackedCrystals.clear();
        awaitingPositions.clear();
    }

    public void removeAwaitingPosition(BlockPos pos) {
        if (pos != null) {
            awaitingPositions.remove(pos);
        }
    }

    public void removeAwaitingPositionsNear(EndCrystal crystal) {
        if (crystal == null) {
            return;
        }
        awaitingPositions.entrySet().removeIf(entry -> crystal.distanceToSqr(Vec3.atCenterOf(entry.getKey())) < 0.3);
    }

    public void onCrystalAttack(Minecraft mc, EndCrystal crystal) {
        if (crystal == null) {
            return;
        }
        setDeadCrystal(crystal.getId());
        attackedCrystals.compute(crystal.getId(), (id, attempt) ->
                attempt == null
                        ? new AutoCrystalAttempt(mc.player, System.currentTimeMillis(), 1, crystal.position())
                        : attempt.incremented(mc.player));
    }

    public void addAwaitingPosition(Minecraft mc, ClientLevel level, BlockPos pos) {
        if (pos == null) {
            return;
        }

        boolean blocked = isPositionBlockedByCrystal(level, pos);
        awaitingPositions.compute(pos, (blockPos, attempt) ->
                attempt == null
                        ? new AutoCrystalAttempt(mc.player, System.currentTimeMillis(), 1, Vec3.atCenterOf(pos))
                        : (!blocked ? attempt.incremented(mc.player) : attempt));
    }

    public boolean isAwaitingPositionBlocked(Minecraft mc, BlockPos pos) {
        AutoCrystalAttempt attempt = awaitingPositions.get(pos);
        return attempt != null && attempt.canSetBlocked(Math.max(0, NetworkStatsUtil.getPing(mc)));
    }

    public boolean isCrystalBlocked(Minecraft mc, int id) {
        AutoCrystalAttempt attempt = attackedCrystals.get(id);
        return attempt != null && attempt.canSetBlocked(Math.max(0, NetworkStatsUtil.getPing(mc)));
    }

    public boolean isDeadCrystal(int id) {
        return deadCrystals.containsKey(id);
    }

    public void setDeadCrystal(int id) {
        deadCrystals.putIfAbsent(id, System.currentTimeMillis());
    }

    public void markNearbyCrystalsDead(ClientLevel level, EndCrystal sourceCrystal) {
        if (level == null || sourceCrystal == null) {
            return;
        }

        double x = sourceCrystal.getX();
        double y = sourceCrystal.getY();
        double z = sourceCrystal.getZ();
        long now = System.currentTimeMillis();

        AABB searchBox = new AABB(x - 12.0, y - 12.0, z - 12.0, x + 12.0, y + 12.0, z + 12.0);
        for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, searchBox)) {
            if (!crystal.isAlive() || crystal.isRemoved() || isDeadCrystal(crystal.getId())) {
                continue;
            }

            if (crystal.distanceToSqr(x, y, z) <= 144.0) {
                setDeadCrystal(crystal.getId());
                deadCrystals.put(crystal.getId(), now);
            }
        }
    }

    public boolean isPositionBlockedByCrystal(ClientLevel level, BlockPos base) {
        if (level == null || base == null) {
            return false;
        }

        AABB box = new AABB(base.above()).inflate(0.0, 1.0, 0.0);
        for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, box)) {
            if (crystal.isAlive() && !crystal.isRemoved()) {
                return true;
            }
        }

        return false;
    }
}
