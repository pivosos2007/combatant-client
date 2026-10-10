/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.player.navigation;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

/** Conservative traversal policy: loaded ground, adequate headroom, no void gaps or hazardous blocks. */
public final class MinecraftWalkTerrain implements LocalWalkPathfinder.Terrain {
    private static final double SUPPORT_DEPTH = 0.13;
    private final LocalPlayer player;
    private final Level level;
    private final Map<LocalWalkPathfinder.Node, Boolean> standCache = new HashMap<>();
    private final int startingExposure;
    private final Map<Edge, Boolean> traversalCache = new HashMap<>();

    private record Edge(LocalWalkPathfinder.Node from, LocalWalkPathfinder.Node to) {}

    public MinecraftWalkTerrain(LocalPlayer player) {
        this.player = player;
        this.level = player.level();
        this.startingExposure = HazardAvoidance.exposure(level, player.getBoundingBox());
    }

    @Override
    public boolean standable(LocalWalkPathfinder.Node node) {
        return standCache.computeIfAbsent(node, n -> safePose(n.centerX(), levelY(n.y()), n.centerZ(), true));
    }

    /** Also used before executing the next steering step, to invalidate stale paths. */
    public boolean safeStep(double x, double y, double z) {
        return safePose(x, y, z, true);
    }

    public boolean safeMotionPose(double x, double y, double z, boolean onGround) {
        return safePose(x, y, z, onGround, startingExposure > 0);
    }

    @Override
    public boolean traversable(LocalWalkPathfinder.Node from, LocalWalkPathfinder.Node to) {
        return traversalCache.computeIfAbsent(new Edge(from, to),
                edge -> checkTraversal(edge.from(), edge.to()));
    }

    private boolean checkTraversal(LocalWalkPathfinder.Node from, LocalWalkPathfinder.Node to) {
        int dy = to.y() - from.y();
        if (Math.abs(dy) > 1 || !standable(to)) return false;
        // Jumping over a full-height obstacle needs free space at the takeoff position.
        if (dy > 0 && !safePose(from.centerX(), levelY(to.y()), from.centerZ(), false)) return false;
        double dx = to.centerX() - from.centerX();
        double dz = to.centerZ() - from.centerZ();
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz) / 0.2));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double x = from.centerX() + dx * t;
            double z = from.centerZ() + dz * t;
            // For a jump, use the raised collision volume along the airborne portion;
            // for a descent, travel over the edge and land on the checked destination.
            double feetY = levelY(dy > 0 ? to.y() : from.y());
            if (!safePose(x, feetY, z, dy == 0)) return false;
        }
        return true;
    }

    private double levelY(int y) {
        // Do not round away fractional feet positions on slabs, snow and stairs.
        return y == (int) Math.floor(player.getY()) ? player.getY() : y;
    }

    private boolean safePose(double x, double feetY, double z, boolean requireSupport) {
        return safePose(x, feetY, z, requireSupport, false);
    }

    private boolean safePose(double x, double feetY, double z, boolean requireSupport, boolean allowEscape) {
        AABB box = player.getBoundingBox().move(
                x - player.getX(), feetY - player.getY(), z - player.getZ());
        if (!loaded(box) || !level.noCollision(player, box)) return false;
        int exposure = HazardAvoidance.exposure(level, box);
        // When already trapped in multiple cobwebs, crossing a shared block
        // boundary can momentarily touch one extra web. Permit a small increase
        // only for low-severity exposure; fire/lava scores are far higher.
        int escapeCeiling = startingExposure <= 6 ? startingExposure + 2 : startingExposure;
        if (exposure > 0 && (!allowEscape || exposure > escapeCeiling)) return false;
        if (!requireSupport) return true;

        // Require support underneath the centre of the body, not a wall at its side.
        AABB support = new AABB(
                x - 0.21, feetY - SUPPORT_DEPTH, z - 0.21,
                x + 0.21, feetY - 0.012, z + 0.21);
        return !level.noCollision(player, support);
    }

    private boolean loaded(AABB box) {
        int y = (int) Math.floor(box.minY);
        return level.hasChunkAt(BlockPos.containing(box.minX, y, box.minZ))
                && level.hasChunkAt(BlockPos.containing(box.maxX, y, box.maxZ))
                && level.hasChunkAt(BlockPos.containing(box.maxX, y, box.minZ))
                && level.hasChunkAt(BlockPos.containing(box.minX, y, box.maxZ));
    }

}
