/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.offhand;

import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.AutoCrystal;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.util.pvp.client.CooldownsState;
import combatant.client.util.target.TargetingUtil;
import combatant.client.util.world.ExplosionDamageUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Decides whether the player is in enough danger that the offhand must hold a totem. */
public final class OffhandThreats {
    // After a totem was needed, stay on it this long before going back to a crystal, so a fight that
    // hovers around the threshold does not bounce the offhand every tick.
    private static final int CRYSTAL_SAFE_HOLD_TICKS = 20;
    // Explosion damage (power 6) reaches 12 blocks.
    private static final double CRYSTAL_DAMAGE_RANGE = 12.0;

    private final Minecraft mc = Minecraft.getInstance();
    private final OffhandSettings settings;

    private int forcedTotemUntilTick = -1;
    private int crystalUnsafeUntilTick = -1;

    public OffhandThreats(OffhandSettings settings) {
        this.settings = settings;
    }

    public static float effectiveHealth(LocalPlayer player) {
        return player.getHealth() + player.getAbsorptionAmount();
    }

    /** An external module asked for a totem; hold it until {@code tick}. */
    public void forceTotemUntil(int tick) {
        forcedTotemUntilTick = Math.max(forcedTotemUntilTick, tick);
    }

    public void resetForcedTotem() {
        forcedTotemUntilTick = -1;
    }

    public boolean shouldHoldTotem(LocalPlayer player) {
        return shouldHoldTotem(player, effectiveHealth(player));
    }

    public boolean shouldHoldTotem(LocalPlayer player, float health) {
        if (player.tickCount <= forcedTotemUntilTick) return true;
        if (player.isFallFlying() && health <= settings.elytraHealth().get()) return true;
        if (health <= settings.healthThreshold().get()) return true;
        if (settings.fallCheck().get() && player.fallDistance > 10.0f) return true;
        if (getClosestCrystalDistance(player) <= settings.crystalDistance().get()) return true;
        if (settings.threats().get(OffhandSettings.THREAT_BROKEN_ARMOR) && hasBrokenArmor(player)
                && hasNearbyCombatPlayer(player, 10.0)) return true;
        if (settings.threats().get(OffhandSettings.THREAT_MACE) && hasMaceThreat(player)) return true;
        return settings.threats().get(OffhandSettings.THREAT_PROJECTILES) && hasProjectileThreat(player);
    }

    /**
     * Safe enough to trade the totem for a crystal. The normal danger check treats any crystal within
     * a few blocks as a threat, which is every tick of a crystal fight, so this asks how much the
     * crystals actually there would deal instead. Everything else that forces a totem still does.
     */
    public boolean isSafeForCrystal(LocalPlayer player) {
        if (!isSafeForCrystalNow(player)) {
            crystalUnsafeUntilTick = player.tickCount + CRYSTAL_SAFE_HOLD_TICKS;
            return false;
        }
        return player.tickCount > crystalUnsafeUntilTick;
    }

    public boolean isPvpContext(LocalPlayer player) {
        return CooldownsState.MANAGER.isInPvp() || hasNearbyCombatPlayer(player, 8.0);
    }

    public boolean isCrystalContext() {
        AutoCrystal crystal = Modules.get(AutoCrystal.class);
        return crystal != null && crystal.isEnabled() && CooldownsState.MANAGER.isInPvp();
    }

    private boolean isSafeForCrystalNow(LocalPlayer player) {
        if (player.tickCount <= forcedTotemUntilTick) return false;
        float health = effectiveHealth(player);
        if (health <= Math.max(settings.healthThreshold().get(), settings.crystalSafeHealth().get())) return false;
        if (player.isFallFlying() && health <= settings.elytraHealth().get()) return false;
        if (settings.fallCheck().get() && player.fallDistance > 3.0f) return false;
        if (settings.threats().get(OffhandSettings.THREAT_BROKEN_ARMOR) && hasBrokenArmor(player)
                && hasNearbyCombatPlayer(player, 10.0)) return false;
        if (settings.threats().get(OffhandSettings.THREAT_MACE) && hasMaceThreat(player)) return false;
        if (settings.threats().get(OffhandSettings.THREAT_PROJECTILES) && hasProjectileThreat(player)) return false;
        return health - worstCrystalDamage(player) > settings.crystalSafeHealth().get();
    }

    private float worstCrystalDamage(LocalPlayer player) {
        float worst = 0.0f;
        for (EndCrystal crystal : mc.level.getEntitiesOfClass(
                EndCrystal.class,
                player.getBoundingBox().inflate(CRYSTAL_DAMAGE_RANGE),
                crystal -> crystal != null && !crystal.isRemoved()
        )) {
            worst = Math.max(worst, ExplosionDamageUtil.getCrystalDamage(player, crystal.position(), 0, false));
        }
        return worst;
    }

    private boolean hasBrokenArmor(LocalPlayer player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                || player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                || player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                || player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }

    private boolean hasNearbyCombatPlayer(LocalPlayer player, double range) {
        double rangeSq = range * range;
        for (Player other : mc.level.players()) {
            if (!isCombatThreatPlayer(player, other)) continue;
            if (other.distanceToSqr(player) <= rangeSq) return true;
        }
        return false;
    }

    private boolean hasMaceThreat(LocalPlayer player) {
        for (Player other : mc.level.players()) {
            if (!isCombatThreatPlayer(player, other)) continue;
            if (!other.getMainHandItem().is(Items.MACE) && !other.getOffhandItem().is(Items.MACE)) continue;
            if (other.getY() <= player.getY()) continue;
            if (other.distanceToSqr(player) > 49.0) continue;
            if (other.getDeltaMovement().y < 0.0 && other.fallDistance > 3.0f) return true;
        }
        return false;
    }

    private boolean isCombatThreatPlayer(LocalPlayer self, Player other) {
        if (other == null || other == self || !other.isAlive() || other.isSpectator()) return false;
        CategoryType type = CategoryRules.determine(other.getGameProfile().name());
        return type != CategoryType.FRIEND && type != CategoryType.STAFF && type != CategoryType.BEDWARS_SELF;
    }

    private boolean hasProjectileThreat(LocalPlayer player) {
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Projectile projectile)) continue;
            if (projectile.getOwner() == player) continue;
            Vec3 velocity = projectile.getDeltaMovement();
            if (velocity.lengthSqr() < 1.0E-7) continue;
            Vec3 from = projectile.position();
            Vec3 toPlayer = player.getBoundingBox().getCenter().subtract(from);
            if (velocity.dot(toPlayer) <= 0.0) continue;
            Vec3 direction = velocity.normalize();
            double along = toPlayer.dot(direction);
            if (along < 0.0 || along > 16.0) continue;
            Vec3 closest = from.add(direction.scale(along));
            if (TargetingUtil.distanceToBoxSq(closest, player.getBoundingBox().inflate(0.35)) <= 0.09) return true;
        }
        return false;
    }

    private double getClosestCrystalDistance(LocalPlayer player) {
        double minDist = Double.MAX_VALUE;
        double range = settings.crystalDistance().get();
        for (EndCrystal crystal : mc.level.getEntitiesOfClass(
                EndCrystal.class,
                player.getBoundingBox().inflate(range),
                crystal -> crystal != null && !crystal.isRemoved()
        )) {
            double dist = player.position().distanceTo(crystal.position());
            if (dist < minDist) minDist = dist;
        }
        return minDist;
    }
}
