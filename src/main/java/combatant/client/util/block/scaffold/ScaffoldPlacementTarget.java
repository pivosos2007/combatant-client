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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.aiming.data.Rotation;

public final class ScaffoldPlacementTarget {

    private final BlockPos interactedBlockPos;
    private final BlockPos placedBlockPos;
    private final Direction direction;
    private final Vec3 hitVec;
    private final double minPlacementY;
    private final Rotation rotation;

    public ScaffoldPlacementTarget(
            BlockPos interactedBlockPos,
            BlockPos placedBlockPos,
            Direction direction,
            Vec3 hitVec,
            double minPlacementY,
            Rotation rotation
    ) {
        this.interactedBlockPos = interactedBlockPos;
        this.placedBlockPos = placedBlockPos;
        this.direction = direction;
        this.hitVec = hitVec;
        this.minPlacementY = minPlacementY;
        this.rotation = rotation;
    }

    public BlockPos getInteractedBlockPos() {
        return interactedBlockPos;
    }

    public BlockPos getPlacedBlockPos() {
        return placedBlockPos;
    }

    public Direction getDirection() {
        return direction;
    }

    public Vec3 getHitVec() {
        return hitVec;
    }

    public Rotation getRotation() {
        return rotation;
    }

    public ScaffoldPlacementTarget withRotation(Rotation newRotation) {
        return new ScaffoldPlacementTarget(
                interactedBlockPos,
                placedBlockPos,
                direction,
                hitVec,
                minPlacementY,
                newRotation
        );
    }

    public BlockHitResult toHitResult() {
        return new BlockHitResult(hitVec, direction, interactedBlockPos, false);
    }

    public boolean matches(BlockHitResult hitResult) {
        return hitResult != null
                && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && interactedBlockPos.equals(hitResult.getBlockPos())
                && direction == hitResult.getDirection()
                && hitResult.getLocation().y >= minPlacementY;
    }
}
