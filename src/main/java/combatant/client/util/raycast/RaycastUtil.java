/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.raycast;

import combatant.client.util.aiming.data.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.function.Predicate;

/**
 * Centralized raycast helpers for entities.
 */
public enum RaycastUtil {
    ;

    public static EntityHitResult findEntityInCrosshair(Entity viewer,
                                                        double range,
                                                        float yaw,
                                                        float pitch,
                                                        Predicate<Entity> predicate) {
        if (viewer == null) return null;

        Vec3 eyes = viewer.getEyePosition();
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw).normalize();
        Vec3 end = eyes.add(dir.scale(range));

        AABB box = viewer.getBoundingBox()
                .inflate(dir.x * range, dir.y * range, dir.z * range)
                .inflate(1.0, 1.0, 1.0);

        Predicate<Entity> base = EntitySelector.CAN_BE_PICKED;
        Predicate<Entity> filter = predicate != null ? base.and(predicate) : base;

        return ProjectileUtil.getEntityHitResult(viewer, eyes, end, box, filter, range * range);
    }

    public static EntityHitResult findEntityInCrosshair(Entity viewer,
                                                        double range,
                                                        float yaw,
                                                        float pitch) {
        return findEntityInCrosshair(viewer, range, yaw, pitch, null);
    }

    public static EntityHitResult isLookingAtEntity(Entity from,
                                                    Entity to,
                                                    float yaw,
                                                    float pitch,
                                                    double range,
                                                    double throughWallsRange) {
        if (from == null || to == null) return null;
        EntityHitResult hit = findEntityInCrosshair(from, range, yaw, pitch, e -> e == to);
        if (hit == null || hit.getEntity() != to) return null;

        Vec3 eyes = from.getEyePosition();
        double distSq = eyes.distanceToSqr(hit.getLocation());

        if (distSq <= throughWallsRange * throughWallsRange) {
            return hit;
        }

        if (distSq <= range * range && hasLineOfSight(from, hit.getLocation())) {
            return hit;
        }

        return null;
    }

    public static EntityHitResult isLookingAtEntity(Entity from,
                                                    Entity to,
                                                    Rotation rotation,
                                                    double range,
                                                    double throughWallsRange) {
        if (rotation == null) return null;
        return isLookingAtEntity(from, to, rotation.yaw(), rotation.pitch(), range, throughWallsRange);
    }

    public static boolean hasLineOfSight(Entity viewer, Vec3 target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || viewer == null) return false;

        Vec3 eyes = viewer.getEyePosition();
        HitResult res = mc.level.clip(new ClipContext(
                eyes,
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                viewer
        ));
        return res.getType() == HitResult.Type.MISS;
    }

    public static boolean hasLineOfSightPoint(Vec3 from, Vec3 to) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || from == null || to == null) return false;

        CollisionContext shapeContext = mc.player != null ? CollisionContext.of(mc.player) : CollisionContext.empty();
        HitResult res = mc.level.clip(new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                shapeContext
        ));
        return res.getType() == HitResult.Type.MISS;
    }
}
