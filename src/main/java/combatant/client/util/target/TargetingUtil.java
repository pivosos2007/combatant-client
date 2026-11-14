/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.target;

import combatant.client.config.values.EnumValue;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.common.impl.TargetFilters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.features.module.modules.combat.AntiBot;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.features.relations.EntityFilters;
import combatant.client.util.aiming.RotationUtil;
import combatant.client.util.player.PlayerHealthResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Centralized target selection helpers.
 */
public enum TargetingUtil {
    ;

    public static LivingEntity findBestTarget(Minecraft mc, TargetingSettings settings) {
        List<LivingEntity> list = findTargets(mc, settings);
        if (list.isEmpty()) return null;
        return list.get(0);
    }

    /**
     * Resolves the shared TargetManager target for target-oriented UI/render features.
     * Source selection and the optional player-only filter live here so consumers do not
     * drift into slightly different target semantics.
     */
    public static LivingEntity resolveManagedTarget(boolean includeCrosshair, boolean playersOnly) {
        LivingEntity target = TargetManager.getTarget(includeCrosshair);
        if (target == null) return null;
        if (playersOnly && !(target instanceof Player)) return null;
        return target;
    }

    public static List<LivingEntity> findTargets(Minecraft mc, TargetingSettings settings) {
        if (mc == null || mc.level == null || mc.player == null || mc.getConnection() == null || settings == null) return List.of();
        if (!mc.player.isAlive() || mc.player.isRemoved() || mc.player.isSpectator()) return List.of();

        double range = settings.range();
        Vec3 center = mc.player.position();
        AABB search = new AABB(
                center.x - range, center.y - range, center.z - range,
                center.x + range, center.y + range, center.z + range
        );

        List<Entity> entities = mc.level.getEntities(mc.player, search, e -> e instanceof LivingEntity);
        List<LivingEntity> out = new ArrayList<>();

        for (Entity e : entities) {
            LivingEntity living = (LivingEntity) e;
            if (!matchesFilters(mc, living, settings.filters())) continue;
            if (settings.visibleOnly() && !mc.player.hasLineOfSight(living)) continue;

            double distSq = distanceToEntityBoxSq(mc.player.getEyePosition(), living);
            if (distSq > range * range) continue;

            float fov = settings.fov();
            if (fov < 180f) {
                float angle = RotationUtil.directionAngleTo(mc.player, living);
                if (angle > fov * 0.5f) continue;
            }

            out.add(living);
        }

        out.sort(buildComparator(settings.priority(), mc));
        return out;
    }

    public static boolean matchesFilters(Minecraft mc, LivingEntity living, BooleanMapValue filters) {
        if (mc == null || mc.player == null || filters == null || living == mc.player
                || !isValidCombatTarget(living) || AntiBot.shouldIgnore(living)) return false;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
        boolean marked = id != null && EntityFilters.get().isMarkedEntity(id.toString());
        Player player = living instanceof Player p ? p : null;
        return TargetFilters.accepts(filters::get, player != null,
                player == null ? CategoryType.DEFAULT : CategoryRules.determine(player.getGameProfile().name()),
                player != null && isNaked(player),
                player == null ? disposition(living, mc.player) : TargetFilters.Disposition.FRIENDLY,
                marked, living.isInvisible());
    }

    public static TargetFilters.Disposition disposition(LivingEntity entity, Player observer) {
        if (entity.isAlliedTo(observer)) {
            return TargetFilters.Disposition.FRIENDLY;
        }
        if (entity instanceof Mob mob && (mob.getTarget() == observer || mob.isAggressive())) {
            return TargetFilters.Disposition.HOSTILE;
        }
        if (entity instanceof NeutralMob neutral && neutral.isAngry()) return TargetFilters.Disposition.HOSTILE;
        if (entity instanceof TamableAnimal tame && tame.isTame()) return TargetFilters.Disposition.FRIENDLY;
        if (entity instanceof NeutralMob) return TargetFilters.Disposition.NEUTRAL;
        return entity instanceof Enemy ? TargetFilters.Disposition.HOSTILE : TargetFilters.Disposition.FRIENDLY;
    }

    public static boolean isValidCombatTarget(LivingEntity living) {
        if (living == null) return false;
        if (living instanceof AbstractClientPlayer) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getConnection() == null) return false;
        }
        if (!living.isAlive() || living.isRemoved()) return false;
        if (living.isSpectator()) return false;
        if (!living.isPickable()) return false;
        if (!living.isAttackable() || living.isInvulnerable()) return false;
        return !(living instanceof Player player) || !player.isSleeping();
    }

    public static boolean isNaked(Player player) {
        if (player == null) return false;
        return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                && player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                && player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }

    private static Comparator<LivingEntity> buildComparator(TargetPriority priority, Minecraft mc) {
        if (priority == null) priority = TargetPriority.DISTANCE;
        switch (priority) {
            case HEALTH:
                return Comparator.comparingDouble(PlayerHealthResolver::totalHealth);
            case HURT_TIME:
                return Comparator.comparingInt(e -> e.hurtTime);
            case ANGLE:
                return Comparator.comparingDouble(e -> RotationUtil.directionAngleTo(mc.player, e));
            case AGE:
                return Comparator.comparingInt(e -> -e.tickCount);
            case DISTANCE:
            default:
                return Comparator.comparingDouble(e -> distanceToEntityBoxSq(mc.player.getEyePosition(), e));
        }
    }

    public static double distanceToEntityBoxSq(Vec3 from, Entity e) {
        AABB box = e.getBoundingBox();
        return boxDistanceSq(box, from);
    }

    public static double distanceToBoxSq(Vec3 from, AABB box) {
        return boxDistanceSq(box, from);
    }

    private static double boxDistanceSq(AABB box, Vec3 p) {
        double dx = 0.0;
        if (p.x < box.minX) dx = box.minX - p.x;
        else if (p.x > box.maxX) dx = p.x - box.maxX;

        double dy = 0.0;
        if (p.y < box.minY) dy = box.minY - p.y;
        else if (p.y > box.maxY) dy = p.y - box.maxY;

        double dz = 0.0;
        if (p.z < box.minZ) dz = box.minZ - p.z;
        else if (p.z > box.maxZ) dz = p.z - box.maxZ;

        return dx * dx + dy * dy + dz * dz;
    }

    public enum TargetPriority implements EnumValue.IdProvider {
        DISTANCE("distance"),
        HEALTH("health"),
        HURT_TIME("hurt_time"),
        ANGLE("angle"),
        AGE("age");

        private final String id;

        TargetPriority(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public record TargetingSettings(
            double range,
            float fov,
            BooleanMapValue filters,
            boolean visibleOnly,
            TargetPriority priority
    ) {
        public TargetingSettings(double range, float fov, BooleanMapValue filters, TargetPriority priority) {
            this(range, fov, filters, filters.get(TargetFilters.VISIBLE_ONLY), priority);
        }

        public TargetingSettings(double range, float fov, boolean playersOnly, boolean ignoreFriends,
                                 boolean ignoreStaff, boolean ignoreEnemies, boolean ignoreNaked,
                                 boolean ignoreEntities, boolean visibleOnly, TargetPriority priority) {
            this(range, fov, legacyFilters(playersOnly, ignoreFriends, ignoreStaff, ignoreEnemies,
                    ignoreNaked, ignoreEntities), visibleOnly, priority);
        }

        private static BooleanMapValue legacyFilters(boolean playersOnly, boolean ignoreFriends,
                                                    boolean ignoreStaff, boolean ignoreEnemies,
                                                    boolean ignoreNaked, boolean ignoreEntities) {
            BooleanMapValue filters = new BooleanMapValue("targets", new TargetFilters().defaults());
            filters.set(TargetFilters.PLAYERS_ONLY, playersOnly);
            filters.set(TargetFilters.IGNORE_FRIENDS, ignoreFriends);
            filters.set(TargetFilters.IGNORE_STAFF, ignoreStaff);
            filters.set(TargetFilters.IGNORE_ENEMIES, ignoreEnemies);
            filters.set(TargetFilters.IGNORE_NAKED, ignoreNaked);
            filters.set(TargetFilters.IGNORE_MARKED, ignoreEntities);
            return filters;
        }
    }
}
