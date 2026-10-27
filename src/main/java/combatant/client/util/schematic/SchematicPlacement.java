/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where a schematic sits in the world. The transform matches vanilla structure templates: mirror first, then
 * rotate around the origin, so schematic cell (0, 0, 0) always lands on {@link #origin()}.
 */
public record SchematicPlacement(BlockPos origin, Rotation rotation, Mirror mirror) {

    public SchematicPlacement {
        origin = origin == null ? BlockPos.ZERO : origin.immutable();
        rotation = rotation == null ? Rotation.NONE : rotation;
        mirror = mirror == null ? Mirror.NONE : mirror;
    }

    public static SchematicPlacement at(BlockPos origin) {
        return new SchematicPlacement(origin, Rotation.NONE, Mirror.NONE);
    }

    public SchematicPlacement withOrigin(BlockPos newOrigin) {
        return new SchematicPlacement(newOrigin, rotation, mirror);
    }

    public SchematicPlacement rotatedClockwise() {
        return new SchematicPlacement(origin, rotation.getRotated(Rotation.CLOCKWISE_90), mirror);
    }

    public SchematicPlacement withNextMirror() {
        Mirror next = switch (mirror) {
            case NONE -> Mirror.LEFT_RIGHT;
            case LEFT_RIGHT -> Mirror.FRONT_BACK;
            case FRONT_BACK -> Mirror.NONE;
        };
        return new SchematicPlacement(origin, rotation, next);
    }

    public int worldX(int x, int z) {
        return origin.getX() + rotatedX(mirroredX(x), mirroredZ(z));
    }

    public int worldY(int y) {
        return origin.getY() + y;
    }

    public int worldZ(int x, int z) {
        return origin.getZ() + rotatedZ(mirroredX(x), mirroredZ(z));
    }

    public BlockPos.MutableBlockPos toWorld(int x, int y, int z, BlockPos.MutableBlockPos out) {
        return out.set(worldX(x, z), worldY(y), worldZ(x, z));
    }

    public BlockPos toWorld(int x, int y, int z) {
        return new BlockPos(worldX(x, z), worldY(y), worldZ(x, z));
    }

    public int localX(int worldX, int worldZ) {
        int dx = worldX - origin.getX();
        int dz = worldZ - origin.getZ();
        return mirroredX(unrotatedX(dx, dz));
    }

    public int localY(int worldY) {
        return worldY - origin.getY();
    }

    public int localZ(int worldX, int worldZ) {
        int dx = worldX - origin.getX();
        int dz = worldZ - origin.getZ();
        return mirroredZ(unrotatedZ(dx, dz));
    }

    public BlockState transform(BlockState state) {
        return state == null ? null : state.mirror(mirror).rotate(rotation);
    }

    /** World-space corners of a {@code sizeX * sizeY * sizeZ} schematic: {minX, minY, minZ, maxX, maxY, maxZ}. */
    public int[] worldBounds(int sizeX, int sizeY, int sizeZ) {
        int ax = worldX(0, 0), az = worldZ(0, 0);
        int bx = worldX(sizeX - 1, sizeZ - 1), bz = worldZ(sizeX - 1, sizeZ - 1);
        return new int[]{
                Math.min(ax, bx), origin.getY(), Math.min(az, bz),
                Math.max(ax, bx), origin.getY() + sizeY - 1, Math.max(az, bz)};
    }

    private int mirroredX(int x) {
        return mirror == Mirror.FRONT_BACK ? -x : x;
    }

    private int mirroredZ(int z) {
        return mirror == Mirror.LEFT_RIGHT ? -z : z;
    }

    private int rotatedX(int x, int z) {
        return switch (rotation) {
            case NONE -> x;
            case CLOCKWISE_90 -> -z;
            case CLOCKWISE_180 -> -x;
            case COUNTERCLOCKWISE_90 -> z;
        };
    }

    private int rotatedZ(int x, int z) {
        return switch (rotation) {
            case NONE -> z;
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> -z;
            case COUNTERCLOCKWISE_90 -> -x;
        };
    }

    private int unrotatedX(int dx, int dz) {
        return switch (rotation) {
            case NONE -> dx;
            case CLOCKWISE_90 -> dz;
            case CLOCKWISE_180 -> -dx;
            case COUNTERCLOCKWISE_90 -> -dz;
        };
    }

    private int unrotatedZ(int dx, int dz) {
        return switch (rotation) {
            case NONE -> dz;
            case CLOCKWISE_90 -> -dx;
            case CLOCKWISE_180 -> -dz;
            case COUNTERCLOCKWISE_90 -> dx;
        };
    }
}
