/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.block.scaffold;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class ScaffoldMovementPrediction {

    private static final int MAX_PLACEMENT_OFFSETS = 4;
    private final ArrayDeque<Vec3> lastPlacementOffsets = new ArrayDeque<>();

    private static Vec3 rotateY(Vec3 vec, float angle) {
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        double x = vec.x * cos + vec.z * sin;
        double z = vec.z * cos - vec.x * sin;
        return new Vec3(x, vec.y, z);
    }

    private static boolean isCloseToEdge(LocalPlayer player, double distance) {
        if (player == null || player.input == null) {
            return false;
        }

        var move = player.input.getMoveVector();
        double sideways = move.x;
        double forward = move.y;
        if (Math.abs(forward) < 1.0E-6 && Math.abs(sideways) < 1.0E-6) {
            return false;
        }

        double rad = Math.toRadians(player.getYRot());
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double x = sideways * cos - forward * sin;
        double z = forward * cos + sideways * sin;
        Vec3 input = new Vec3(x, 0.0, z);
        if (input.lengthSqr() < 1.0E-6) {
            return false;
        }

        Vec3 offset = input.normalize().multiply(distance, 0.0, distance);
        EntityDimensions dimensions = player.getDimensions(player.getPose());
        AABB hitbox = dimensions.makeBoundingBox(player.position())
                .inflate(-0.05, 0.0, -0.05)
                .move(offset.x, -0.55, offset.z);
        return !player.level().getBlockCollisions(player, hitbox).iterator().hasNext();
    }

    private static Vec3 findEdgeCollision(LocalPlayer player, Vec3 from, Vec3 to, float allowedDropDown) {
        List<AABB> boundingBoxes = collectCollisionBoundingBoxes(player, from, to, allowedDropDown);
        Vec3 currentFrom = from;

        Vec3 lineVec = to.subtract(from);
        Vec3 extendedFrom = from.subtract(lineVec.scale(1000.0));
        Vec3 extendedTo = to.add(lineVec.scale(1000.0));

        List<AABB> cache = new ArrayList<>();
        while (true) {
            cache.clear();
            for (AABB box : boundingBoxes) {
                if (box.contains(currentFrom)) {
                    cache.add(box);
                }
            }

            if (cache.isEmpty()) {
                return currentFrom;
            }

            for (AABB box : cache) {
                if (box.contains(to)) {
                    return null;
                }
            }

            Optional<Vec3> next = cache.stream()
                    .map(box -> box.clip(extendedFrom, extendedTo).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .min(Comparator.comparingDouble(hit -> hit.distanceToSqr(to)));
            if (next.isEmpty()) {
                return null;
            }

            currentFrom = next.get();
            boundingBoxes.removeAll(cache);
        }
    }

    private static List<AABB> collectCollisionBoundingBoxes(
            LocalPlayer player,
            Vec3 from,
            Vec3 to,
            float allowedDropDown
    ) {
        EntityDimensions dimensions = player.getDimensions(player.getPose());
        AABB fromBox = dimensions.makeBoundingBox(from);
        AABB toBox = dimensions.makeBoundingBox(to);
        AABB unionBox = fromBox.minmax(toBox);

        BlockPos fromBlockPos = BlockPos.containing(
                unionBox.minX - 0.3 - 1.0E-7,
                unionBox.minY - allowedDropDown - 1.0E-7,
                unionBox.minZ - 0.3 - 1.0E-7
        );
        BlockPos toBlockPos = BlockPos.containing(
                unionBox.maxX + 0.3 + 1.0E-7,
                unionBox.minY + 1.0E-7,
                unionBox.maxZ + 0.3 + 1.0E-7
        );

        Vec3 lineVec = to.subtract(from);
        Vec3 extendedFrom = from.subtract(lineVec.scale(1000.0));
        Vec3 extendedTo = to.add(lineVec.scale(1000.0));

        List<AABB> foundBoxes = new ArrayList<>();
        for (int x = fromBlockPos.getX(); x <= toBlockPos.getX(); x++) {
            for (int y = fromBlockPos.getY(); y <= toBlockPos.getY(); y++) {
                for (int z = fromBlockPos.getZ(); z <= toBlockPos.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    var state = player.level().getBlockState(pos);
                    var collisionShape = state.getCollisionShape(player.level(), pos);
                    for (AABB boundingBox : collisionShape.toAabbs()) {
                        AABB adjustedBox = new AABB(
                                boundingBox.minX - 0.3,
                                boundingBox.minY - 1.0,
                                boundingBox.minZ - 0.3,
                                boundingBox.maxX + 0.3,
                                boundingBox.maxY + allowedDropDown + 0.05,
                                boundingBox.maxZ + 0.3
                        ).move(pos);

                        if (adjustedBox.clip(extendedFrom, extendedTo).isEmpty()) {
                            continue;
                        }

                        foundBoxes.add(adjustedBox);
                    }
                }
            }
        }

        return foundBoxes;
    }

    public void reset() {
        lastPlacementOffsets.clear();
    }

    public void onPlace(ScaffoldLine optimalLine, Vec3 lastFallOffPosition, Vec3 playerPosition) {
        if (optimalLine == null || lastFallOffPosition == null || playerPosition == null) return;

        float lineDirAngle = (float) Math.atan2(optimalLine.direction().z, optimalLine.direction().x);
        Vec3 unrotatedOffset = rotateY(playerPosition.subtract(lastFallOffPosition), lineDirAngle);

        lastPlacementOffsets.addLast(unrotatedOffset);
        while (lastPlacementOffsets.size() > MAX_PLACEMENT_OFFSETS) {
            lastPlacementOffsets.removeFirst();
        }
    }

    public Vec3 getPredictedPlacementPos(LocalPlayer player, ScaffoldLine optimalLine) {
        if (player == null || optimalLine == null) return null;
        if (isCloseToEdge(player, 0.0)) {
            return null;
        }

        Vec3 fallOffPoint = getFallOffPositionOnLine(player, optimalLine);
        if (fallOffPoint == null) return null;

        Vec3 avg = getAveragePlacementOffset();
        if (avg == null) {
            return fallOffPoint;
        }

        float lineDirAngle = (float) Math.atan2(optimalLine.direction().z, optimalLine.direction().x);
        return fallOffPoint.add(rotateY(avg, -lineDirAngle));
    }

    public Vec3 getFallOffPositionOnLine(LocalPlayer player, ScaffoldLine optimalLine) {
        if (player == null || optimalLine == null) return null;

        Vec3 nearest = optimalLine.getNearestPointTo(player.position());
        Vec3 fromLine = nearest.add(0.0, -0.1, 0.0);
        Vec3 toLine = fromLine.add(optimalLine.direction().normalize().scale(3.0));

        Vec3 edgeCollision = findEdgeCollision(player, fromLine, toLine, 0.5f);
        return edgeCollision != null ? new Vec3(edgeCollision.x, player.getY(), edgeCollision.z) : null;
    }

    private Vec3 getAveragePlacementOffset() {
        if (lastPlacementOffsets.isEmpty()) return null;

        Vec3 sum = Vec3.ZERO;
        for (Vec3 offset : lastPlacementOffsets) {
            sum = sum.add(offset);
        }
        return sum.scale(1.0 / lastPlacementOffsets.size());
    }
}
