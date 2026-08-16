/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.target;

import combatant.client.config.values.EnumValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
            if (!isValidCombatTarget(living)) continue;
            if (settings.playersOnly() && !(living instanceof Player)) continue;
            if (settings.visibleOnly() && !mc.player.hasLineOfSight(living)) continue;

            if (settings.ignoreEntities()) {
                var id = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
                if (id != null && EntityFilters.get().isIgnoredEntity(id.toString())) {
                    continue;
                }
            }

            if (living instanceof Player p) {
                CategoryType type = CategoryRules.determine(p.getGameProfile().name());
                if (type == CategoryType.BEDWARS_SELF) continue;
                if (settings.ignoreFriends() && type == CategoryType.FRIEND) continue;
                if (settings.ignoreStaff() && type == CategoryType.STAFF) continue;
                if (settings.ignoreEnemies() && (type == CategoryType.ENEMY || type == CategoryType.BEDWARS_ENEMY))
                    continue;
                if (settings.ignoreNaked() && isNaked(p)) continue;
            }

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
            boolean playersOnly,
            boolean ignoreFriends,
            boolean ignoreStaff,
            boolean ignoreEnemies,
            boolean ignoreNaked,
            boolean ignoreEntities,
            boolean visibleOnly,
            TargetPriority priority
    ) {
    }
}
