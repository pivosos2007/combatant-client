/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.player;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Predicts wall impact damage using the same horizontal speed loss as
 * LivingEntity.handleFallFlyingCollisions in Minecraft 26.3. Merely touching a
 * floor or ceiling is NOT a wall crash. The entity's vanilla collision solver
 * is used to obtain the clipped displacement, including partial block shapes.
 */
public final class ElytraImpactDamageUtil {
    private ElytraImpactDamageUtil() {
    }

    public static float vanillaWallDamage(Vec3 before, Vec3 after) {
        if (before == null || after == null) return 0.0f;
        return (float) Math.max(0.0,
                10.0 * (before.horizontalDistance() - after.horizontalDistance()) - 3.0);
    }

    /** The collision solver's returned displacement is what Entity.move can perform. */
    public static Vec3 allowedMovement(Entity entity, AABB box, Vec3 requested) {
        return Entity.collideBoundingBox(entity, requested, box, entity.level(),
                entity.level().getEntityCollisions(entity, box.expandTowards(requested)));
    }

    /**
     * Entity.move zeroes the velocity component on a blocked horizontal axis.
     * Reproducing that distinction is essential: a glancing impact does not
     * lose the entire horizontal speed, and a floor-only hit loses none.
     */
    public static Vec3 postCollisionVelocity(Vec3 requested, Vec3 clipped) {
        return new Vec3(
                !Mth.equal(requested.x, clipped.x) ? 0.0 : requested.x,
                requested.y != clipped.y ? 0.0 : requested.y,
                !Mth.equal(requested.z, clipped.z) ? 0.0 : requested.z
        );
    }

    public static float projectedDamage(Vec3 requested, Vec3 clipped) {
        return vanillaWallDamage(requested, postCollisionVelocity(requested, clipped));
    }

    /**
     * Vanilla LivingEntity.travelFallFlying measures horizontal speed BEFORE its
     * airborne physics update and AFTER movement/collision resolution. Damage
     * only applies when the collision solver blocks a horizontal component.
     */
    public static float projectedFlightDamage(Vec3 previousVelocity, Vec3 attempted, Vec3 clipped) {
        if (previousVelocity == null || attempted == null || clipped == null) return 0.0f;
        if (Mth.equal(attempted.x, clipped.x) && Mth.equal(attempted.z, clipped.z)) return 0.0f;
        return vanillaWallDamage(previousVelocity, postCollisionVelocity(attempted, clipped));
    }

    /** Every chunk intersecting the collision solver's query must be loaded. */
    public static boolean hasLoadedBox(Entity entity, AABB box) {
        int minX = Mth.floor(box.minX) >> 4;
        int maxX = Mth.floor(box.maxX) >> 4;
        int minZ = Mth.floor(box.minZ) >> 4;
        int maxZ = Mth.floor(box.maxZ) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!entity.level().hasChunkAt(new BlockPos(x << 4, Mth.floor(box.minY), z << 4))) return false;
            }
        }
        return true;
    }

    public static boolean hasLoadedSweep(Entity entity, AABB start, Vec3 movement) {
        return hasLoadedBox(entity, start.expandTowards(movement));
    }
}
