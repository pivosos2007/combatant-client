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

public final class ScaffoldInteractionTarget {

    private final BlockPos interactedBlockPos;
    private final BlockPos targetBlockPos;
    private final Direction direction;
    private final Vec3 hitVec;
    private final double minTargetY;
    private final Rotation rotation;

    public ScaffoldInteractionTarget(
            BlockPos interactedBlockPos,
            BlockPos targetBlockPos,
            Direction direction,
            Vec3 hitVec,
            double minTargetY,
            Rotation rotation
    ) {
        this.interactedBlockPos = interactedBlockPos;
        this.targetBlockPos = targetBlockPos;
        this.direction = direction;
        this.hitVec = hitVec;
        this.minTargetY = minTargetY;
        this.rotation = rotation;
    }

    public BlockPos getInteractedBlockPos() {
        return interactedBlockPos;
    }

    public BlockPos getTargetBlockPos() {
        return targetBlockPos;
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

    public BlockHitResult toHitResult() {
        return new BlockHitResult(hitVec, direction, interactedBlockPos, false);
    }

    public boolean matches(BlockHitResult hitResult) {
        return hitResult != null
                && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && interactedBlockPos.equals(hitResult.getBlockPos())
                && direction == hitResult.getDirection()
                && hitResult.getLocation().y >= minTargetY;
    }
}
