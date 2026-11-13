/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.player;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Checks a bounded, block-clear flight corridor before admitting a pursuit target. */
public final class ElytraTargetReachability {
    private static final double APPROACH_RADIUS = 2.4;
    private static final double[] OVERPASS_HEIGHTS = {4.0, 9.0, 16.0, 24.0};

    private ElytraTargetReachability() {
    }

    /** Waypoints exclude the target; an empty route means that it is directly visible. */
    public record Route(List<Vec3> waypoints) {
    }

    public static Route findRoute(LocalPlayer player, LivingEntity target) {
        if (player == null || target == null || isSubmerged(target)) return null;
        Vec3 start = player.getEyePosition();
        AABB bounds = target.getBoundingBox();
        Vec3 center = bounds.getCenter();
        double insetX = Math.min(0.22, bounds.getXsize() * 0.25);
        double insetZ = Math.min(0.22, bounds.getZsize() * 0.25);
        Vec3 top = new Vec3(center.x, bounds.maxY - 0.12, center.z);
        Vec3[] contacts = {
                top, center,
                center.add(bounds.getXsize() * 0.5 - insetX, 0, 0),
                center.add(-bounds.getXsize() * 0.5 + insetX, 0, 0),
                center.add(0, 0, bounds.getZsize() * 0.5 - insetZ),
                center.add(0, 0, -bounds.getZsize() * 0.5 + insetZ)
        };
        for (Vec3 contact : contacts) {
            if (!isWaterAt(player.level(), contact) && flightClear(player, start, contact)) {
                return new Route(List.of());
            }
        }

        Vec3[] approaches = {
                new Vec3(center.x, bounds.maxY + 1.1, center.z),
                new Vec3(center.x + APPROACH_RADIUS, center.y + 0.55, center.z),
                new Vec3(center.x - APPROACH_RADIUS, center.y + 0.55, center.z),
                new Vec3(center.x, center.y + 0.55, center.z + APPROACH_RADIUS),
                new Vec3(center.x, center.y + 0.55, center.z - APPROACH_RADIUS)
        };
        Vec3[] approachContacts = {top, center, center, center, center};
        for (int i = 0; i < approaches.length; i++) {
            Vec3 approach = approaches[i];
            if (!freeWaypoint(player, approach) || isWaterAt(player.level(), approach)
                    || isWaterAt(player.level(), approachContacts[i])
                    || !clear(player, approach, approachContacts[i])) continue;
            if (flightClear(player, start, approach)) return new Route(List.of(approach));
        }

        // A short climb and overpass can reach a ground target behind a wall or a ridge,
        // but never a target sealed below a solid roof.
        double topAltitude = Math.max(start.y, bounds.maxY);
        for (double lift : OVERPASS_HEIGHTS) {
            double y = topAltitude + lift;
            Vec3 departure = new Vec3(start.x, y, start.z);
            if (!freeWaypoint(player, departure) || !flightClear(player, start, departure)) continue;
            for (int i = 0; i < approaches.length; i++) {
                Vec3 arrival = new Vec3(approaches[i].x, y, approaches[i].z);
                if (!freeWaypoint(player, arrival)
                        || !freeWaypoint(player, approaches[i])
                        || !flightClear(player, departure, arrival)
                        || !flightClear(player, arrival, approaches[i])
                        || isWaterAt(player.level(), approaches[i])
                        || isWaterAt(player.level(), approachContacts[i])
                        || !clear(player, approaches[i], approachContacts[i])) continue;
                double length = start.distanceTo(departure) + departure.distanceTo(arrival)
                        + arrival.distanceTo(approaches[i]);
                if (length > start.distanceTo(center) * 1.9 + 24.0) continue;
                return new Route(List.of(departure, arrival, approaches[i]));
            }
        }
        return null;
    }

    public static boolean hasClearSegment(LocalPlayer player, Vec3 from, Vec3 to) {
        return player != null && flightClear(player, from, to);
    }

    public static boolean hasDirectContact(LocalPlayer player, LivingEntity target) {
        if (player == null || target == null || isSubmerged(target)) return false;
        AABB box = target.getBoundingBox();
        Vec3 center = box.getCenter();
        Vec3 upper = new Vec3(center.x, box.maxY - 0.12, center.z);
        return (!isWaterAt(player.level(), upper) && flightClear(player, player.getEyePosition(), upper))
                || (!isWaterAt(player.level(), center) && flightClear(player, player.getEyePosition(), center));
    }

    public static boolean isSubmerged(LivingEntity target) {
        if (target == null) return true;
        AABB bounds = target.getBoundingBox();
        Vec3 center = bounds.getCenter();
        return isWaterAt(target.level(), new Vec3(center.x, bounds.maxY + 0.04, center.z));
    }

    /** A shallow-water target is hittable from the air if its upper hitbox is exposed.
     * Do not remove elytra when only a submerged head (or no body) can be contacted.
     */
    public static boolean isSafeMaceTarget(LivingEntity target) {
        if (target == null || target.isInLava() || isSubmerged(target)) return false;
        AABB box = target.getBoundingBox();
        Vec3 center = box.getCenter();
        double exposed = Math.min(0.45, box.getYsize() * 0.30);
        // Check an actual attack contact below the top, not isInWater(): that
        // flag is true even when a player is only ankle-deep.
        return !isWaterAt(target.level(), new Vec3(center.x, box.maxY - exposed, center.z));
    }

    private static boolean isWaterAt(Level level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        if (!level.hasChunkAt(pos)) return true;
        var state = level.getFluidState(pos);
        return state.is(FluidTags.WATER)
                && point.y < pos.getY() + state.getHeight(level, pos) - 0.001;
    }

    private static boolean flightClear(LocalPlayer player, Vec3 start, Vec3 end) {
        if (!clear(player, start, end)) return false;
        double distance = start.distanceTo(end);
        for (double step = 0.5; step < distance - 1.0; step += 0.75) {
            Vec3 probe = start.lerp(end, step / distance);
            if (!freeFlightSpace(player, probe)
                    || isWaterAt(player.level(), probe.add(0.0, -0.30, 0.0))) return false;
        }
        return true;
    }

    private static boolean freeFlightSpace(LocalPlayer player, Vec3 at) {
        if (!player.level().hasChunkAt(BlockPos.containing(at))) return false;
        return player.level().noCollision(player, new AABB(
                at.x - 0.32, at.y - 0.32, at.z - 0.32,
                at.x + 0.32, at.y + 0.32, at.z + 0.32));
    }

    private static boolean freeWaypoint(LocalPlayer player, Vec3 at) {
        if (!player.level().hasChunkAt(BlockPos.containing(at))) return false;
        AABB volume = new AABB(at.x - 0.34, at.y - 0.56, at.z - 0.34,
                at.x + 0.34, at.y + 0.56, at.z + 0.34);
        return player.level().noCollision(player, volume);
    }

    private static boolean clear(LocalPlayer player, Vec3 start, Vec3 end) {
        if (!player.level().hasChunkAt(BlockPos.containing(start))
                || !player.level().hasChunkAt(BlockPos.containing(end))) return false;
        HitResult obstruction = player.level().clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return obstruction.getType() == HitResult.Type.MISS
                || obstruction.getLocation().distanceToSqr(start) + 0.01 >= start.distanceToSqr(end);
    }
}
